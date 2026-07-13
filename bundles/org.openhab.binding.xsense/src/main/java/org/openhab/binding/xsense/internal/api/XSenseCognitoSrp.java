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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link XSenseCognitoSrp} implements the client side of the AWS Cognito Secure Remote Password
 * authentication flow (USER_SRP_AUTH) without requiring the AWS SDK.
 *
 * The implementation is a port of the well proven pycognito aws_srp.py which is also the base of the
 * X-Sense integration for Home Assistant.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseCognitoSrp {

    // 3072-bit group from RFC 5054 as used by AWS Cognito
    private static final String N_HEX = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
            + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
            + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
            + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
            + "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
            + "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64"
            + "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B"
            + "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31"
            + "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF";
    private static final BigInteger N = new BigInteger(N_HEX, 16);
    private static final BigInteger G = BigInteger.TWO;
    private static final byte[] DERIVED_KEY_INFO = "Caldera Derived Key".getBytes(StandardCharsets.UTF_8);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("EEE MMM d HH:mm:ss 'UTC' yyyy", Locale.US).withZone(ZoneOffset.UTC);

    public static final String PARAM_USERNAME = "USERNAME";
    public static final String PARAM_SRP_A = "SRP_A";
    public static final String PARAM_SECRET_HASH = "SECRET_HASH";
    public static final String PARAM_SRP_B = "SRP_B";
    public static final String PARAM_SALT = "SALT";
    public static final String PARAM_SECRET_BLOCK = "SECRET_BLOCK";
    public static final String PARAM_USER_ID_FOR_SRP = "USER_ID_FOR_SRP";
    public static final String PARAM_TIMESTAMP = "TIMESTAMP";
    public static final String PARAM_CLAIM_SECRET_BLOCK = "PASSWORD_CLAIM_SECRET_BLOCK";
    public static final String PARAM_CLAIM_SIGNATURE = "PASSWORD_CLAIM_SIGNATURE";

    private final String poolName;
    private final String clientId;
    private final byte[] clientSecret;
    private final BigInteger k;
    private final BigInteger smallA;
    private final BigInteger largeA;

    public XSenseCognitoSrp(String userPoolId, String clientId, byte[] clientSecret) throws XSenseApiException {
        int separator = userPoolId.indexOf('_');
        if (separator < 0 || separator == userPoolId.length() - 1) {
            throw new XSenseApiException("Invalid Cognito user pool id: " + userPoolId);
        }
        this.poolName = userPoolId.substring(separator + 1);
        this.clientId = clientId;
        this.clientSecret = clientSecret.clone();

        k = hexToLong(hexHash("00" + N_HEX + "0" + G.toString(16)));
        BigInteger a;
        BigInteger bigA;
        SecureRandom random = new SecureRandom();
        do {
            a = new BigInteger(1, randomBytes(random, 128)).mod(N);
            bigA = G.modPow(a, N);
        } while (bigA.mod(N).signum() == 0);
        smallA = a;
        largeA = bigA;
    }

    /**
     * Returns the AuthParameters for the InitiateAuth (USER_SRP_AUTH) request.
     */
    public Map<String, String> getAuthParameters(String username) throws XSenseApiException {
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_USERNAME, username);
        params.put(PARAM_SRP_A, largeA.toString(16));
        params.put(PARAM_SECRET_HASH, secretHash(clientSecret, username, clientId));
        return params;
    }

    /**
     * Computes the ChallengeResponses for the RespondToAuthChallenge (PASSWORD_VERIFIER) request.
     *
     * @param challengeParameters the ChallengeParameters returned by InitiateAuth
     * @param password the account password
     */
    public Map<String, String> processChallenge(Map<String, String> challengeParameters, String password)
            throws XSenseApiException {
        return processChallenge(challengeParameters, password, TIMESTAMP_FORMAT.format(Instant.now()));
    }

    /**
     * Same as {@link #processChallenge(Map, String)} with a fixed timestamp (for unit tests).
     */
    public Map<String, String> processChallenge(Map<String, String> challengeParameters, String password,
            String timestamp) throws XSenseApiException {
        String username = getParameter(challengeParameters, PARAM_USERNAME);
        String userIdForSrp = getParameter(challengeParameters, PARAM_USER_ID_FOR_SRP);
        String saltHex = getParameter(challengeParameters, PARAM_SALT);
        String srpBHex = getParameter(challengeParameters, PARAM_SRP_B);
        String secretBlock = getParameter(challengeParameters, PARAM_SECRET_BLOCK);

        byte[] hkdf = passwordAuthenticationKey(userIdForSrp, password, new BigInteger(srpBHex, 16), saltHex);
        byte[] secretBlockBytes = Base64.getDecoder().decode(secretBlock);

        byte[] message = concat(poolName.getBytes(StandardCharsets.UTF_8),
                userIdForSrp.getBytes(StandardCharsets.UTF_8), secretBlockBytes,
                timestamp.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(hmacSha256(hkdf, message));

        Map<String, String> response = new HashMap<>();
        response.put(PARAM_TIMESTAMP, timestamp);
        response.put(PARAM_USERNAME, username);
        response.put(PARAM_CLAIM_SECRET_BLOCK, secretBlock);
        response.put(PARAM_CLAIM_SIGNATURE, signature);
        response.put(PARAM_SECRET_HASH, secretHash(clientSecret, username, clientId));
        return response;
    }

    /**
     * Computes the Cognito SECRET_HASH: Base64(HMAC-SHA256(clientSecret, userName + clientId)).
     */
    public static String secretHash(byte[] clientSecret, String userName, String clientId) throws XSenseApiException {
        return Base64.getEncoder()
                .encodeToString(hmacSha256(clientSecret, (userName + clientId).getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] passwordAuthenticationKey(String userId, String password, BigInteger srpB, String saltHex)
            throws XSenseApiException {
        if (srpB.mod(N).signum() == 0) {
            throw new XSenseApiException("SRP error: B mod N is zero");
        }
        BigInteger u = hexToLong(hexHash(padHex(largeA) + padHex(srpB)));
        if (u.signum() == 0) {
            throw new XSenseApiException("SRP error: u is zero");
        }

        String usernamePassword = poolName + userId + ":" + password;
        String usernamePasswordHash = bytesToHex(sha256(usernamePassword.getBytes(StandardCharsets.UTF_8)));

        BigInteger x = hexToLong(hexHash(padHex(saltHex) + usernamePasswordHash));
        BigInteger gModPowXN = G.modPow(x, N);
        BigInteger intValue = srpB.subtract(k.multiply(gModPowXN));
        BigInteger s = intValue.modPow(smallA.add(u.multiply(x)), N);

        // HKDF: extract with salt=u, expand with "Caldera Derived Key" + 0x01, truncated to 16 bytes
        byte[] prk = hmacSha256(hexToBytes(padHex(u)), hexToBytes(padHex(s)));
        byte[] info = concat(DERIVED_KEY_INFO, new byte[] { 1 });
        byte[] hkdf = hmacSha256(prk, info);
        byte[] key = new byte[16];
        System.arraycopy(hkdf, 0, key, 0, 16);
        return key;
    }

    private static String getParameter(Map<String, String> parameters, String name) throws XSenseApiException {
        String value = parameters.get(name);
        if (value == null || value.isEmpty()) {
            throw new XSenseApiException("Cognito challenge parameter missing: " + name);
        }
        return value;
    }

    private static byte[] randomBytes(SecureRandom random, int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    private static BigInteger hexToLong(String hex) {
        return new BigInteger(hex, 16);
    }

    private static String hexHash(String hex) throws XSenseApiException {
        return bytesToHex(sha256(hexToBytes(hex)));
    }

    /**
     * Pads a hex representation the same way as pycognito pad_hex(): prepend "0" for odd length,
     * prepend "00" when the highest nibble is 8-f (to keep the value positive).
     */
    private static String padHex(BigInteger value) {
        return padHex(value.toString(16));
    }

    private static String padHex(String hex) {
        if (hex.length() % 2 == 1) {
            return "0" + hex;
        }
        char first = hex.charAt(0);
        if ((first >= '8' && first <= '9') || (first >= 'a' && first <= 'f') || (first >= 'A' && first <= 'F')) {
            return "00" + hex;
        }
        return hex;
    }

    private static byte[] hexToBytes(String hex) {
        String padded = hex.length() % 2 == 1 ? "0" + hex : hex;
        byte[] bytes = new byte[padded.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(padded.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] data) throws XSenseApiException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new XSenseApiException("SHA-256 not available", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) throws XSenseApiException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new XSenseApiException("HMAC-SHA256 failed", e);
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    /**
     * Package private test hook: creates an instance with a fixed private ephemeral key.
     */
    XSenseCognitoSrp(String userPoolId, String clientId, byte[] clientSecret, BigInteger fixedSmallA)
            throws XSenseApiException {
        int separator = userPoolId.indexOf('_');
        if (separator < 0 || separator == userPoolId.length() - 1) {
            throw new XSenseApiException("Invalid Cognito user pool id: " + userPoolId);
        }
        this.poolName = userPoolId.substring(separator + 1);
        this.clientId = clientId;
        this.clientSecret = clientSecret.clone();
        k = hexToLong(hexHash("00" + N_HEX + "0" + G.toString(16)));
        smallA = fixedSmallA;
        largeA = G.modPow(fixedSmallA, N);
    }

    /**
     * Returns the public ephemeral key A as hex string (for tests).
     */
    String getLargeAHex() {
        return largeA.toString(16);
    }
}
