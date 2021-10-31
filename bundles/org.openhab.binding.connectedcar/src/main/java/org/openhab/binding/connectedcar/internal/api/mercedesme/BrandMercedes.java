/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.connectedcar.internal.api.mercedesme;

import static org.openhab.binding.connectedcar.internal.BindingConstants.*;
import static org.openhab.binding.connectedcar.internal.api.ApiDataTypesDTO.API_BRAND_MERCEDES;
import static org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.*;
import static org.openhab.binding.connectedcar.internal.util.Helpers.*;

import java.util.UUID;

import javax.ws.rs.core.HttpHeaders;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.connectedcar.internal.api.ApiBrandProperties;
import org.openhab.binding.connectedcar.internal.api.ApiConfigurationException;
import org.openhab.binding.connectedcar.internal.api.ApiEventListener;
import org.openhab.binding.connectedcar.internal.api.ApiException;
import org.openhab.binding.connectedcar.internal.api.ApiHttpClient;
import org.openhab.binding.connectedcar.internal.api.ApiHttpMap;
import org.openhab.binding.connectedcar.internal.api.ApiIdentity;
import org.openhab.binding.connectedcar.internal.api.ApiIdentity.OAuthToken;
import org.openhab.binding.connectedcar.internal.api.BrandAuthenticator;
import org.openhab.binding.connectedcar.internal.api.IdentityManager;
import org.openhab.binding.connectedcar.internal.api.IdentityOAuthFlow;
import org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.MmeRequestPinResponse;
import org.openhab.binding.connectedcar.internal.handler.ThingHandlerInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BrandApiFord} provides the brand specific functions of the API
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class BrandMercedes extends MercedesMeApi implements BrandAuthenticator {
    private final Logger logger = LoggerFactory.getLogger(BrandMercedes.class);
    private ApiBrandProperties properties = new ApiBrandProperties();

    public BrandMercedes(ThingHandlerInterface handler, ApiHttpClient httpClient, IdentityManager tokenManager,
            @Nullable ApiEventListener eventListener) {
        super(handler, httpClient, tokenManager, eventListener);
    }

    @Override
    public ApiBrandProperties getProperties() throws ApiException {
        properties.brand = API_BRAND_MERCEDES;
        properties.userAgent = "MyCar/1.11.0 (com.daimler.ris.mercedesme.ece.ios; build:1051; iOS 12.5.1) Alamofire/5.4.0";
        properties.clientId = "01398c1c-dc45-4b42-882b-9f5ba9f175f1";

        properties.tokenUrl = "https://id.mercedes-benz.com/as/token.oauth2";
        properties.tokenRefreshUrl = properties.tokenUrl;
        // properties.authScope = "openid profile email phone offline_access ciam-uid";
        properties.authScope = "offline_access";
        properties.redirect_uri = "mmeconnect://authenticated";

        switch (config.account.region) {
            case MME_REGION_EUROPE:
                properties.xappName = "mycar-store-ece";
                properties.xappVersion = "1.6.3";
                properties.xcountry = "de-DE";
                properties.loginUrl = "https://bff-prod.risingstars.daimler.com/v1/login";
                properties.apiDefaultUrl = "https://bff-prod.risingstars.daimler.com";
                break;
            case MME_REGION_NORTHAM:
                properties.xappName = "mycar-store-us";
                properties.xappVersion = "3.0.1";
                properties.xcountry = "en-US";
                properties.loginUrl = "https://bff-prod.risingstars.daimler.com/v1/login";
                properties.apiDefaultUrl = "https://bff-prod.risingstars-amap.daimler.com";
                break;
            case MME_REGION_APAC:
                properties.xappName = "mycar-store-ap";
                properties.xappVersion = "1.6.2";
                properties.xcountry = "de-DE";
                properties.loginUrl = "https://bff-prod.risingstars.daimler.com/v1/login";
                properties.apiDefaultUrl = "https://bff-prod.risingstars-amap.daimler.com";
                break;
            default:
                throw new ApiException("Unsupported Region: " + config.account.region);
        }
        properties.xappVersion = "1.11.0 (1051)";

        properties.stdHeaders.put(HttpHeaders.USER_AGENT.toString(), properties.userAgent);
        properties.stdHeaders.put(HttpHeaders.ACCEPT.toString(), "*/*");
        properties.stdHeaders.put(HttpHeaders.ACCEPT_LANGUAGE.toString(), properties.xcountry + ";q=1.0");
        properties.stdHeaders.put("X-Locale", properties.xcountry);
        properties.stdHeaders.put("device-uuid", UUID.randomUUID().toString());
        properties.stdHeaders.put("RIS-OS-Name", "ios");
        properties.stdHeaders.put("RIS-OS-Version", "14.6");
        properties.stdHeaders.put("RIS-SDK-Version", "2.43.0");
        properties.stdHeaders.put("RIS-application-version", properties.xappVersion);
        properties.stdHeaders.put("X-ApplicationName", config.api.xappName);
        properties.stdHeaders.put("X-SessionId", UUID.randomUUID().toString());
        properties.stdHeaders.put("X-TrackingId", UUID.randomUUID().toString());

        properties.loginHeaders.put("Stage", properties.apiDefaultUrl.contains("tryout") ? "staging" : "prod");
        properties.loginHeaders.put("X-Authmode", "CIAMNG"); // "KEYCLOAK");
        return properties;
    }

    @Override
    public String getLoginUrl(IdentityOAuthFlow oauth) {
        return properties.loginUrl;
    }

    @Override
    public ApiIdentity login(String loginUrl, IdentityOAuthFlow oauth) throws ApiException {
        String json = "";
        String message = "";
        String nonce = getProperty(PROPERTY_NONCE);
        if (config.account.code.isEmpty() || nonce.isEmpty()) {
            // Step 1: create login pin
            logger.info("{}: Requesting Login Code for {}", config.getLogId(), config.account.user);

            nonce = UUID.randomUUID().toString();// generateNonce();
            logger.debug("{}: Login NONCE={}", config.getLogId(), nonce);
            json = oauth.init(createDefaultParameters()) //
                    // .headers(config.api.loginHeaders)//
                    .data("nonce", nonce).data("locale", config.api.xcountry)
                    .data("emailOrPhoneNumber", config.account.user)
                    .data("countryCode", substringAfter(config.api.xcountry, "-"))//
                    .post(loginUrl, true).response;
            MmeRequestPinResponse response = fromJson(gson, json, MmeRequestPinResponse.class);
            if (getBool(response.isEmail)) {
                message = "Login code has been requested successful, check your E-Mails and enter the TAN-Code into the Bridge Thing configuration within the next 15 minutes";
                logger.info("{}: {}", config.getLogId(), message);
                fillProperty(PROPERTY_NONCE, nonce);
                fillProperty("accessToken", "");
                fillProperty("refreshToken", "");
                throw new ApiException(message, new ApiConfigurationException(message));
            }
            message = "Unable to request Login Code";
            logger.warn("{}: {}", config.getLogId(), message);
            throw new ApiException(message, new ApiConfigurationException(message));
        }

        OAuthToken token = new OAuthToken();
        oauth.code = token.idToken = nonce; // we need the nonce to generate the access token
        return new ApiIdentity(token);
    }

    @Override
    public ApiIdentity grantAccess(IdentityOAuthFlow oauth) throws ApiException {
        try {
            // Step 2: get aaccess token
            String password = oauth.code + ":" + config.account.code;
            String json = oauth.clearHeader().clearData().headers(createDefaultParameters().getHeaders())//
                    .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_FORM_URLENC).data("client_id", config.api.clientId)
                    .headers(config.api.loginHeaders)//
                    .data("grant_type", "password").data("username", urlEncode(config.account.user))
                    .data("password", password).data("scope", urlEncode(config.api.authScope))//
                    .post(config.api.tokenUrl, false).response;
            OAuthToken token = fromJson(gson, json, OAuthToken.class).normalize();
            fillProperty("accessToken", token.accessToken);
            fillProperty("refreshToken", token.refreshToken);
            return new ApiIdentity(fromJson(gson, json, OAuthToken.class).normalize());
        } catch (ApiException e) {
            OAuthToken ctoken = new OAuthToken();
            ctoken.accessToken = getProperty("accessToken");
            ctoken.refreshToken = getProperty("refreshToken");
            return new ApiIdentity(refreshToken(new ApiIdentity(ctoken)));
        }
    }

    @Override
    public OAuthToken refreshToken(ApiIdentity apiToken) throws ApiException {
        ApiHttpMap params = new ApiHttpMap().headers(createApiParameters(apiToken.getAccessToken())) //
                .headers(config.api.loginHeaders)//
                .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_FORM_URLENC) //
                .data("grant_type", "refresh_token") //
                .data("refresh_token", apiToken.getRefreshToken());
        String json = http.post(config.api.tokenRefreshUrl, params.getHeaders(), //
                params.getRequestData(false)).response;
        return fromJson(gson, json, OAuthToken.class).normalize();
    }
}
