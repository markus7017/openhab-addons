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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
public class XSenseApiClientTest {

    private static final byte[] SECRET = "secret".getBytes(StandardCharsets.UTF_8);

    @Test
    public void calculateMacHashesParameterValuesInOrderWithSecret() throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", "2");
        // MD5("12" + "secret")
        assertEquals("f71fdbb0a8d84142eb57a95ed0474f9f", XSenseApiClient.calculateMac(params, SECRET));
    }

    @Test
    public void calculateMacOfEmptyParamsHashesSecretOnly() throws Exception {
        String mac = XSenseApiClient.calculateMac(new LinkedHashMap<>(), SECRET);
        assertEquals(32, mac.length());
        assertNotEquals(XSenseApiClient.calculateMac(new LinkedHashMap<>(), "other".getBytes(StandardCharsets.UTF_8)),
                mac);
    }

    @Test
    public void decodeClientSecretStripsGarbageBytes() throws Exception {
        // 4 leading garbage bytes + "mysecret" + 1 trailing garbage byte
        byte[] secret = XSenseApiClient.decodeClientSecret("AAECA215c2VjcmV0/w==");
        assertEquals("mysecret", new String(secret, StandardCharsets.UTF_8));
    }

    @Test
    public void decodeClientSecretTooShortThrows() {
        String encoded = Base64.getEncoder().encodeToString(new byte[5]);
        assertThrows(XSenseApiException.class, () -> XSenseApiClient.decodeClientSecret(encoded));
    }

    @Test
    public void decodeClientSecretInvalidBase64Throws() {
        assertThrows(XSenseApiException.class, () -> XSenseApiClient.decodeClientSecret("not-base64!!"));
    }
}
