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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link XSenseAwsSigV4} signs HTTP requests with the AWS Signature Version 4 scheme. It is a
 * minimal pure-Java implementation (no AWS SDK dependency, matching the pure-Java Cognito SRP in
 * {@link XSenseCognitoSrp}) covering exactly what the binding needs: header-based signing of
 * requests to the AWS IoT data plane with temporary credentials (session token signed as the
 * X-Amz-Security-Token header, supplied by the caller).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseAwsSigV4 {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final String region;
    private final String service;

    public XSenseAwsSigV4(String region, String service) {
        this.region = region;
        this.service = service;
    }

    /**
     * Signs the request and returns the headers to add: Host, X-Amz-Date and Authorization. The
     * given headers (e.g. Content-Type, X-Amz-Security-Token) are included in the signature and
     * must be sent verbatim with the request.
     *
     * @param method HTTP method, e.g. "POST"
     * @param uri full request URI including scheme, host, path and query
     * @param headers additional headers to include in the signature (name -> value)
     * @param body request body, empty string for body-less requests
     * @param accessKeyId AWS access key id
     * @param secretAccessKey AWS secret access key
     * @param signingTime the signing timestamp (injectable for tests)
     * @return the headers to add to the request
     * @throws XSenseApiException if the JVM lacks the required crypto algorithms
     */
    public Map<String, String> sign(String method, URI uri, Map<String, String> headers, String body,
            String accessKeyId, String secretAccessKey, Instant signingTime) throws XSenseApiException {
        String amzDate = AMZ_DATE_FORMAT.format(signingTime);
        String dateStamp = amzDate.substring(0, 8);
        String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

        Map<String, String> signedHeaders = new TreeMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            signedHeaders.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().trim());
        }
        signedHeaders.put("host", host);
        signedHeaders.put("x-amz-date", amzDate);

        String canonicalRequest = canonicalRequest(method, uri, signedHeaders, body);
        String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = stringToSign(amzDate, scope, canonicalRequest);
        byte[] signingKey = signingKey(secretAccessKey, dateStamp);
        String signature = toHex(hmacSha256(signingKey, stringToSign));

        Map<String, String> result = new LinkedHashMap<>();
        result.put("Host", host);
        result.put("X-Amz-Date", amzDate);
        result.put("Authorization", ALGORITHM + " Credential=" + accessKeyId + "/" + scope + ", SignedHeaders="
                + String.join(";", signedHeaders.keySet()) + ", Signature=" + signature);
        return result;
    }

    /**
     * Builds the canonical request: method, path, sorted query string, sorted lowercase headers,
     * signed header names and the hex-encoded SHA-256 of the body.
     */
    String canonicalRequest(String method, URI uri, Map<String, String> signedHeaders, String body)
            throws XSenseApiException {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append('\n');
        String path = uri.getRawPath();
        sb.append(path.isEmpty() ? "/" : path).append('\n');
        sb.append(canonicalQueryString(uri.getRawQuery())).append('\n');
        for (Map.Entry<String, String> entry : signedHeaders.entrySet()) {
            sb.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        sb.append('\n');
        sb.append(String.join(";", signedHeaders.keySet())).append('\n');
        sb.append(hexSha256(body));
        return sb.toString();
    }

    /**
     * Builds the canonical query string: parameters sorted by name (then value), names and values
     * URI-encoded with the unreserved character set required by SigV4.
     */
    static String canonicalQueryString(@Nullable String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String> encoded = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            encoded.add(uriEncode(URLDecoder.decode(key, StandardCharsets.UTF_8)) + "="
                    + uriEncode(URLDecoder.decode(value, StandardCharsets.UTF_8)));
        }
        encoded.sort(Comparator.naturalOrder());
        return String.join("&", encoded);
    }

    /**
     * Builds the string to sign from the timestamp, credential scope and canonical request hash.
     */
    static String stringToSign(String amzDate, String scope, String canonicalRequest) throws XSenseApiException {
        return ALGORITHM + "\n" + amzDate + "\n" + scope + "\n" + hexSha256(canonicalRequest);
    }

    /**
     * Derives the signing key via the SigV4 HMAC chain: "AWS4" + secret -> date -> region ->
     * service -> "aws4_request".
     */
    byte[] signingKey(String secretAccessKey, String dateStamp) throws XSenseApiException {
        byte[] kDate = hmacSha256(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    static String hexSha256(String data) throws XSenseApiException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new XSenseApiException("SHA-256 not available", e);
        }
    }

    static byte[] hmacSha256(byte[] key, String data) throws XSenseApiException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new XSenseApiException("HmacSHA256 not available", e);
        }
    }

    /**
     * Percent-encodes per the SigV4 rules: only unreserved characters A-Z a-z 0-9 - _ . ~ are left
     * as-is, everything else (UTF-8 encoded) becomes %XX with uppercase hex digits.
     */
    static String uriEncode(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_'
                    || c == '.' || c == '~') {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
