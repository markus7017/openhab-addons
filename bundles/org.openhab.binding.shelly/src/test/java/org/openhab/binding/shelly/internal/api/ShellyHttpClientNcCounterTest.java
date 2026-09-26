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
package org.openhab.binding.shelly.internal.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2AuthChallenge;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2AuthRsp;
import org.openhab.binding.shelly.internal.config.ShellyApiConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.core.net.NetworkAddressService;

/**
 * Unit tests for {@link ShellyHttpClient}'s RFC 2617 digest auth {@code nc} counter: it must increase on every
 * request that reuses the same server nonce, and reset when the device issues a new nonce.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
class ShellyHttpClientNcCounterTest {

    @Test
    void ncIncrementsOnRepeatedNonce() throws Exception {
        ShellyHttpClient client = buildClient();
        Shelly2AuthChallenge challenge = digestChallenge("nonce-1");

        Shelly2AuthRsp first = client.buildChannelAuthResponse(challenge, "admin", "pw");
        Shelly2AuthRsp second = client.buildChannelAuthResponse(challenge, "admin", "pw");
        Shelly2AuthRsp third = client.buildChannelAuthResponse(challenge, "admin", "pw");

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertEquals("00000001", first.nc);
        assertEquals("00000002", second.nc);
        assertEquals("00000003", third.nc);
    }

    @Test
    void ncResetsWhenNonceChanges() throws Exception {
        ShellyHttpClient client = buildClient();
        Shelly2AuthChallenge first = digestChallenge("nonce-1");
        Shelly2AuthChallenge second = digestChallenge("nonce-2");

        client.buildChannelAuthResponse(first, "admin", "pw");
        client.buildChannelAuthResponse(first, "admin", "pw");
        Shelly2AuthRsp afterNewNonce = client.buildChannelAuthResponse(second, "admin", "pw");

        assertNotNull(afterNewNonce);
        assertEquals("00000001", afterNewNonce.nc);
    }

    private static ShellyHttpClient buildClient() throws Exception {
        ShellyBindingConfiguration raw = ShellyBindingConfiguration
                .fromProperties(Map.of(ShellyBindingConfiguration.CONFIG_LOCAL_IP, "192.168.1.1"));
        ShellyBindingRuntimeConfig bindingConfig = new ShellyBindingRuntimeConfig(raw, 8080,
                mock(NetworkAddressService.class));
        ShellyApiConfiguration config = new ShellyApiConfiguration(bindingConfig, "test", "");
        return new ShellyHttpClient("test", config, mock(HttpClient.class));
    }

    private static Shelly2AuthChallenge digestChallenge(String nonce) {
        Shelly2AuthChallenge challenge = new Shelly2AuthChallenge();
        challenge.authType = SHELLY2_AUTHTTYPE_DIGEST;
        challenge.realm = "shelly";
        challenge.nonce = nonce;
        challenge.algorithm = SHELLY2_AUTHALG_SHA256;
        return challenge;
    }
}
