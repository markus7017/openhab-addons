/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.xsense.internal.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.ApiResponse;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.ClientInfo;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.House;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.StationList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link XSenseApiClient} implements the X-Sense cloud API: Cognito SRP login, token refresh
 * and the bizCode based application REST API.
 *
 * The protocol has been decoded by the python-xsense and Home Assistant X-Sense projects, see
 * https://github.com/theosnel/python-xsense and https://github.com/Jarnsen/ha-xsense-component_test.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseApiClient {
    private static final String API_URL = "https://api.x-sense-iot.com/app";
    private static final String COGNITO_URL_FORMAT = "https://cognito-idp.%s.amazonaws.com/";
    private static final String COGNITO_TARGET_PREFIX = "AWSCognitoIdentityProviderService.";
    private static final String COGNITO_CONTENT_TYPE = "application/x-amz-json-1.1";

    private static final String CLIENT_TYPE_ANDROID = "2";
    private static final String APP_VERSION = "v1.36.0_20260130";
    private static final String APP_CODE = "1360";
    // Fixed integrity hash accepted for unauthenticated requests
    private static final String UNAUTH_MAC = "abcdefg";

    private static final String BIZCODE_CLIENT_INFO = "101001";
    private static final String BIZCODE_QUERY_HOUSES = "102007";
    private static final String BIZCODE_QUERY_STATIONS = "103007";

    // errCode values indicating an expired/invalid session
    private static final Set<String> SESSION_EXPIRED_ERRORS = Set.of("10000008", "10000020");

    private static final int REQUEST_TIMEOUT_SEC = 15;
    private static final long TOKEN_EXPIRY_MARGIN_SEC = 60;

    private final Logger logger = LoggerFactory.getLogger(XSenseApiClient.class);
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final Object sessionLock = new Object();

    // Cognito app client information (bizCode 101001)
    private @Nullable String cognitoClientId;
    private @Nullable String cognitoRegion;
    private @Nullable String userPoolId;
    private byte[] clientSecret = new byte[0];

    // Session state, guarded by sessionLock; accessToken is additionally volatile so that
    // isLoggedIn() never blocks on a login/refresh in progress
    private String email = "";
    private String password = "";
    // Internal Cognito user name (USER_ID_FOR_SRP), required for the refresh token SECRET_HASH
    private @Nullable String cognitoUsername;
    private volatile @Nullable String accessToken;
    private @Nullable String refreshToken;
    private Instant accessTokenExpiry = Instant.MIN;

    public XSenseApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Performs a full login: fetch the Cognito client information and run the SRP authentication flow.
     */
    public void login(String email, String password) throws XSenseApiException {
        synchronized (sessionLock) {
            this.email = email;
            this.password = password;
            fetchClientInfo();
            srpLogin();
        }
    }

    public boolean isLoggedIn() {
        return accessToken != null;
    }

    /**
     * Returns all houses of the account.
     */
    public List<House> getHouses() throws XSenseApiException {
        JsonElement data = apiCall(BIZCODE_QUERY_HOUSES, orderedParams("utctimestamp", "0"));
        List<House> houses = fromJson(data, TypeToken.getParameterized(List.class, House.class).getType());
        return houses != null ? houses : new ArrayList<>();
    }

    /**
     * Returns the base stations of a house including their attached devices.
     */
    public StationList getStations(String houseId) throws XSenseApiException {
        JsonElement data = apiCall(BIZCODE_QUERY_STATIONS, orderedParams("houseId", houseId, "utctimestamp", "0"));
        StationList stations = fromJson(data, StationList.class);
        return stations != null ? stations : new StationList();
    }

    // === bizCode application API ===

    private void fetchClientInfo() throws XSenseApiException {
        JsonElement data = unauthenticatedApiCall(BIZCODE_CLIENT_INFO, new LinkedHashMap<>());
        ClientInfo info = fromJson(data, ClientInfo.class);
        String clientId = info != null ? info.clientId : null;
        String encodedSecret = info != null ? info.clientSecret : null;
        String region = info != null ? info.cgtRegion : null;
        String poolId = info != null ? info.userPoolId : null;
        if (info == null || clientId == null || encodedSecret == null || region == null || poolId == null) {
            throw new XSenseApiException("Incomplete client info response (bizCode 101001)");
        }
        cognitoClientId = clientId;
        clientSecret = decodeClientSecret(encodedSecret);
        cognitoRegion = region;
        userPoolId = poolId;
    }

    private JsonElement apiCall(String bizCode, LinkedHashMap<String, String> params) throws XSenseApiException {
        String token;
        synchronized (sessionLock) {
            ensureFreshToken();
            token = accessToken;
        }
        if (token == null) {
            throw new XSenseApiException("Not logged in", true);
        }
        return executeApiCall(bizCode, params, calculateMac(params, clientSecret), token);
    }

    private JsonElement unauthenticatedApiCall(String bizCode, LinkedHashMap<String, String> params)
            throws XSenseApiException {
        return executeApiCall(bizCode, params, UNAUTH_MAC, null);
    }

    private JsonElement executeApiCall(String bizCode, LinkedHashMap<String, String> params, String mac,
            @Nullable String authToken) throws XSenseApiException {
        JsonObject body = new JsonObject();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.addProperty(entry.getKey(), entry.getValue());
        }
        body.addProperty("clientType", CLIENT_TYPE_ANDROID);
        body.addProperty("mac", mac);
        body.addProperty("appVersion", APP_VERSION);
        body.addProperty("bizCode", bizCode);
        body.addProperty("appCode", APP_CODE);

        String responseBody = httpPost(API_URL, body.toString(), "application/json", authToken, Map.of());

        ApiResponse response = fromJson(responseBody, ApiResponse.class);
        Integer reCode = response != null ? response.reCode : null;
        if (response == null || reCode == null) {
            throw new XSenseApiException("Unparsable API response for bizCode " + bizCode);
        }
        if (reCode != 200) {
            String errCode = response.errCode;
            boolean expired = errCode != null && SESSION_EXPIRED_ERRORS.contains(errCode);
            throw new XSenseApiException(
                    "API error for bizCode " + bizCode + ": " + reCode + "/" + errCode + " " + response.reMsg, expired);
        }
        JsonElement reData = response.reData;
        return reData != null ? reData : new JsonObject();
    }

    /**
     * Calculates the request integrity hash: MD5 over the concatenated parameter values
     * (in request order) followed by the client secret.
     */
    static String calculateMac(LinkedHashMap<String, String> params, byte[] clientSecret) throws XSenseApiException {
        StringBuilder sb = new StringBuilder();
        for (String value : params.values()) {
            sb.append(value);
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(sb.toString().getBytes(StandardCharsets.UTF_8));
            md5.update(clientSecret);
            StringBuilder hex = new StringBuilder();
            for (byte b : md5.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new XSenseApiException("MD5 not available", e);
        }
    }

    /**
     * The client secret is delivered base64 encoded with 4 leading and 1 trailing garbage byte.
     */
    static byte[] decodeClientSecret(String encoded) throws XSenseApiException {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new XSenseApiException("Unable to decode client secret", e);
        }
        if (decoded.length <= 5) {
            throw new XSenseApiException("Unexpected client secret length: " + decoded.length);
        }
        byte[] secret = new byte[decoded.length - 5];
        System.arraycopy(decoded, 4, secret, 0, secret.length);
        return secret;
    }

    // === Cognito authentication ===

    private void srpLogin() throws XSenseApiException {
        String clientId = cognitoClientId;
        String poolId = userPoolId;
        if (clientId == null || poolId == null) {
            throw new XSenseApiException("Cognito client info not initialized");
        }

        XSenseCognitoSrp srp = new XSenseCognitoSrp(poolId, clientId, clientSecret);

        JsonObject initRequest = new JsonObject();
        initRequest.addProperty("AuthFlow", "USER_SRP_AUTH");
        initRequest.addProperty("ClientId", clientId);
        initRequest.add("AuthParameters", toJsonObject(srp.getAuthParameters(email)));
        JsonObject initResponse = cognitoCall("InitiateAuth", initRequest);

        JsonObject challengeParameters = getAsJsonObject(initResponse, "ChallengeParameters");
        Map<String, String> challenge = new HashMap<>();
        for (String key : challengeParameters.keySet()) {
            JsonElement value = challengeParameters.get(key);
            if (value != null && value.isJsonPrimitive()) {
                challenge.put(key, value.getAsString());
            }
        }
        String userIdForSrp = challenge.get(XSenseCognitoSrp.PARAM_USER_ID_FOR_SRP);
        if (userIdForSrp != null) {
            cognitoUsername = userIdForSrp;
        }

        JsonObject challengeRequest = new JsonObject();
        challengeRequest.addProperty("ChallengeName", "PASSWORD_VERIFIER");
        challengeRequest.addProperty("ClientId", clientId);
        challengeRequest.add("ChallengeResponses", toJsonObject(srp.processChallenge(challenge, password)));
        JsonObject challengeResponse = cognitoCall("RespondToAuthChallenge", challengeRequest);

        parseAuthenticationResult(getAsJsonObject(challengeResponse, "AuthenticationResult"));
        logger.debug("X-Sense login successful for {}", XSenseDtoUtil.maskEmail(email));
    }

    /**
     * Refreshes the access token when it is about to expire; falls back to a full re-login when
     * the refresh token has been invalidated. Must be called while holding sessionLock.
     */
    private void ensureFreshToken() throws XSenseApiException {
        if (accessToken == null) {
            throw new XSenseApiException("Not logged in", true);
        }
        if (Instant.now().isBefore(accessTokenExpiry.minusSeconds(TOKEN_EXPIRY_MARGIN_SEC))) {
            return;
        }
        String clientId = cognitoClientId;
        String refresh = refreshToken;
        if (clientId == null || refresh == null) {
            srpLogin();
            return;
        }
        try {
            String userName = cognitoUsername;
            JsonObject authParameters = new JsonObject();
            authParameters.addProperty("REFRESH_TOKEN", refresh);
            authParameters.addProperty("SECRET_HASH",
                    XSenseCognitoSrp.secretHash(clientSecret, userName != null ? userName : email, clientId));
            JsonObject request = new JsonObject();
            request.addProperty("AuthFlow", "REFRESH_TOKEN_AUTH");
            request.addProperty("ClientId", clientId);
            request.add("AuthParameters", authParameters);
            request.add("UserContextData", new JsonObject());
            JsonObject response = cognitoCall("InitiateAuth", request);
            parseAuthenticationResult(getAsJsonObject(response, "AuthenticationResult"));
            logger.debug("X-Sense access token refreshed");
        } catch (XSenseApiException e) {
            logger.debug("Token refresh failed ({}), performing full login", e.getMessage());
            srpLogin();
        }
    }

    private void parseAuthenticationResult(JsonObject result) throws XSenseApiException {
        JsonElement token = result.get("AccessToken");
        if (token == null || !token.isJsonPrimitive()) {
            throw new XSenseApiException("Cognito response misses AccessToken", true);
        }
        accessToken = token.getAsString();
        JsonElement refresh = result.get("RefreshToken");
        if (refresh != null && refresh.isJsonPrimitive()) {
            refreshToken = refresh.getAsString();
        }
        JsonElement expiresIn = result.get("ExpiresIn");
        long expirySeconds = expiresIn != null && expiresIn.isJsonPrimitive() ? expiresIn.getAsLong() : 3600;
        accessTokenExpiry = Instant.now().plusSeconds(expirySeconds);
    }

    private JsonObject cognitoCall(String target, JsonObject request) throws XSenseApiException {
        String region = cognitoRegion;
        if (region == null) {
            throw new XSenseApiException("Cognito client info not initialized");
        }
        String url = String.format(COGNITO_URL_FORMAT, region);
        String responseBody = httpPost(url, request.toString(), COGNITO_CONTENT_TYPE, null,
                Map.of("X-Amz-Target", COGNITO_TARGET_PREFIX + target));
        JsonObject response = fromJson(responseBody, JsonObject.class);
        if (response == null) {
            throw new XSenseApiException("Unparsable Cognito response for " + target);
        }
        JsonElement type = response.get("__type");
        if (type != null) {
            String message = response.has("message") ? response.get("message").getAsString() : "";
            boolean authFailure = type.getAsString().contains("NotAuthorized")
                    || type.getAsString().contains("UserNotFound");
            throw new XSenseApiException("Cognito error " + type.getAsString() + ": " + message, authFailure);
        }
        return response;
    }

    // === HTTP + JSON helpers ===

    private String httpPost(String url, String body, String contentType, @Nullable String authToken,
            Map<String, String> additionalHeaders) throws XSenseApiException {
        try {
            var request = httpClient.newRequest(url).method(HttpMethod.POST)
                    .content(new StringContentProvider(contentType, body, StandardCharsets.UTF_8), contentType)
                    .timeout(REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (authToken != null) {
                request.header(HttpHeader.AUTHORIZATION, authToken);
            }
            for (Map.Entry<String, String> header : additionalHeaders.entrySet()) {
                request.header(header.getKey(), header.getValue());
            }
            ContentResponse response = request.send();
            String content = response.getContentAsString();
            if (response.getStatus() >= 400) {
                // Cognito errors are reported with HTTP 400 and a JSON body containing "__type"
                if (content.contains("\"__type\"")) {
                    return content;
                }
                throw new XSenseApiException("HTTP " + response.getStatus() + " from " + url);
            }
            return content;
        } catch (ExecutionException | TimeoutException e) {
            throw new XSenseApiException("Request to " + url + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XSenseApiException("Request to " + url + " interrupted", e);
        }
    }

    private <T> @Nullable T fromJson(String json, Class<T> clazz) throws XSenseApiException {
        try {
            return gson.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            throw new XSenseApiException("Invalid JSON response", e);
        }
    }

    private <T> @Nullable T fromJson(JsonElement json, java.lang.reflect.Type type) throws XSenseApiException {
        try {
            return gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new XSenseApiException("Invalid JSON response", e);
        }
    }

    private static JsonObject toJsonObject(Map<String, String> map) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private static JsonObject getAsJsonObject(JsonObject parent, String name) throws XSenseApiException {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new XSenseApiException("Cognito response misses object: " + name);
        }
        return element.getAsJsonObject();
    }

    private static LinkedHashMap<String, String> orderedParams(String... keyValues) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put(keyValues[i], keyValues[i + 1]);
        }
        return params;
    }
}
