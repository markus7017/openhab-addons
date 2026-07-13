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

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
public class XSenseCognitoSrpTest {

    private static final String USER_POOL_ID = "us-east-1_testpool";
    private static final String CLIENT_ID = "testclientid";
    private static final byte[] CLIENT_SECRET = "key".getBytes(StandardCharsets.UTF_8);
    private static final BigInteger FIXED_SMALL_A = new BigInteger("1234567890abcdef1234567890abcdef1234567890abcdef",
            16);

    @Test
    public void secretHashMatchesHmacSha256TestVector() throws Exception {
        // HMAC-SHA256 test vector: key "key", message "The quick brown fox jumps over the lazy dog"
        String hash = XSenseCognitoSrp.secretHash(CLIENT_SECRET, "The quick brown fox ", "jumps over the lazy dog");
        assertEquals("97yD9DBThCSxMpjmqm+xQ+9NWaFJRhdZl0edvC0aPNg=", hash);
    }

    @Test
    public void invalidUserPoolIdThrows() {
        assertThrows(XSenseApiException.class, () -> new XSenseCognitoSrp("invalidpool", CLIENT_ID, CLIENT_SECRET));
        assertThrows(XSenseApiException.class, () -> new XSenseCognitoSrp("us-east-1_", CLIENT_ID, CLIENT_SECRET));
    }

    @Test
    public void authParametersContainRequiredFields() throws Exception {
        XSenseCognitoSrp srp = new XSenseCognitoSrp(USER_POOL_ID, CLIENT_ID, CLIENT_SECRET, FIXED_SMALL_A);
        Map<String, String> params = srp.getAuthParameters("user@example.com");
        assertEquals("user@example.com", params.get(XSenseCognitoSrp.PARAM_USERNAME));
        assertEquals(srp.getLargeAHex(), params.get(XSenseCognitoSrp.PARAM_SRP_A));
        assertEquals(XSenseCognitoSrp.secretHash(CLIENT_SECRET, "user@example.com", CLIENT_ID),
                params.get(XSenseCognitoSrp.PARAM_SECRET_HASH));
    }

    @Test
    public void processChallengeProducesDeterministicSignature() throws Exception {
        XSenseCognitoSrp srp = new XSenseCognitoSrp(USER_POOL_ID, CLIENT_ID, CLIENT_SECRET, FIXED_SMALL_A);
        Map<String, String> challenge = challengeParameters();
        String timestamp = "Sat Jul 4 12:00:00 UTC 2026";

        Map<String, String> first = srp.processChallenge(challenge, "password123", timestamp);
        Map<String, String> second = srp.processChallenge(challenge, "password123", timestamp);

        assertEquals(timestamp, first.get(XSenseCognitoSrp.PARAM_TIMESTAMP));
        assertEquals("user-id-1234", first.get(XSenseCognitoSrp.PARAM_USERNAME));
        assertEquals(challenge.get(XSenseCognitoSrp.PARAM_SECRET_BLOCK),
                first.get(XSenseCognitoSrp.PARAM_CLAIM_SECRET_BLOCK));

        String signature = first.get(XSenseCognitoSrp.PARAM_CLAIM_SIGNATURE);
        assertNotNull(signature);
        assertEquals(32, Base64.getDecoder().decode(signature).length);
        assertEquals(signature, second.get(XSenseCognitoSrp.PARAM_CLAIM_SIGNATURE));
    }

    @Test
    public void missingChallengeParameterThrows() throws Exception {
        XSenseCognitoSrp srp = new XSenseCognitoSrp(USER_POOL_ID, CLIENT_ID, CLIENT_SECRET, FIXED_SMALL_A);
        Map<String, String> challenge = challengeParameters();
        challenge.remove(XSenseCognitoSrp.PARAM_SRP_B);
        assertThrows(XSenseApiException.class, () -> srp.processChallenge(challenge, "password123"));
    }

    @Test
    public void srpBZeroModNThrows() throws Exception {
        XSenseCognitoSrp srp = new XSenseCognitoSrp(USER_POOL_ID, CLIENT_ID, CLIENT_SECRET, FIXED_SMALL_A);
        Map<String, String> challenge = challengeParameters();
        challenge.put(XSenseCognitoSrp.PARAM_SRP_B, "0");
        assertThrows(XSenseApiException.class, () -> srp.processChallenge(challenge, "password123"));
    }

    private static Map<String, String> challengeParameters() {
        Map<String, String> challenge = new HashMap<>();
        challenge.put(XSenseCognitoSrp.PARAM_USERNAME, "user-id-1234");
        challenge.put(XSenseCognitoSrp.PARAM_USER_ID_FOR_SRP, "user-id-1234");
        challenge.put(XSenseCognitoSrp.PARAM_SALT, "a1b2c3d4e5f6");
        challenge.put(XSenseCognitoSrp.PARAM_SRP_B, "5a7b9c1d3e5f7a9b");
        challenge.put(XSenseCognitoSrp.PARAM_SECRET_BLOCK,
                Base64.getEncoder().encodeToString("secret-block".getBytes(StandardCharsets.UTF_8)));
        return challenge;
    }
}
