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
package org.openhab.binding.shelly.internal.api2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1PM;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiResult;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2AuthChallenge;
import org.openhab.binding.shelly.internal.config.ShellyApiConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.handler.ShellyThingTable;
import org.openhab.core.net.NetworkAddressChangeListener;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;

/**
 * Unit tests for {@link Shelly2ApiRpc#apiRequest}: verifies that an HTTP 429 (the device's nonce cache is
 * exhausted) is retried once immediately with a fresh authentication handshake instead of waiting for the
 * next poll cycle, and that a second consecutive 429 on the retry still propagates as before.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
public class Shelly2ApiRpcThrottleRetryTest {

    @Test
    void throttledRequestIsRetriedOnceImmediately() throws Exception {
        ThrottlingApi api = buildApi();
        api.outcomes.add(throttled());
        api.outcomes.add(success());

        String result = api.apiRequest("Test.Method", null, String.class);

        assertEquals("\"ok\"", result);
        assertEquals(2, api.callCount);
    }

    @Test
    void secondConsecutiveThrottleStillPropagates() throws Exception {
        ThrottlingApi api = buildApi();
        api.outcomes.add(throttled());
        api.outcomes.add(throttled());

        assertThrows(ShellyApiException.class, () -> api.apiRequest("Test.Method", null, String.class));
        assertEquals(2, api.callCount);
    }

    private static ShellyApiException throttled() {
        return new ShellyApiException(ShellyApiResult.builder().httpCode(HttpStatus.TOO_MANY_REQUESTS_429).build());
    }

    private static String success() {
        return "{\"id\":1,\"src\":\"test\",\"result\":\"ok\"}";
    }

    private ThrottlingApi buildApi() throws Exception {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1PM);
        profile.alwaysOn = false;

        Thing ohThing = mock(Thing.class);
        when(ohThing.getUID()).thenReturn(new ThingUID(THING_TYPE_SHELLYPLUS1PM, "test"));

        ShellyThingInterface thing = mock(ShellyThingInterface.class);
        HttpClient httpClient = mock(HttpClient.class);
        when(thing.getThing()).thenReturn(ohThing);
        when(thing.getHttpClient()).thenReturn(httpClient);
        when(thing.getProfile()).thenReturn(profile);

        ShellyThingTable thingTable = mock(ShellyThingTable.class);
        ShellyBindingConfiguration raw = ShellyBindingConfiguration
                .fromProperties(Map.of(ShellyBindingConfiguration.CONFIG_LOCAL_IP, "192.168.1.1"));
        ShellyBindingRuntimeConfig bindingConfig = new ShellyBindingRuntimeConfig(raw, 8080, nullNas());
        ShellyApiConfiguration config = new ShellyApiConfiguration(bindingConfig, "test-rpc", "");

        return new ThrottlingApi("test-rpc", thingTable, thing, config, mock(WebSocketClient.class),
                mock(ScheduledExecutorService.class));
    }

    private static NetworkAddressService nullNas() {
        return new NetworkAddressService() {
            @Override
            public @Nullable String getPrimaryIpv4HostAddress() {
                return null;
            }

            @Override
            public @Nullable String getConfiguredBroadcastAddress() {
                return null;
            }

            @Override
            public boolean isUseOnlyOneAddress() {
                return false;
            }

            @Override
            public boolean isUseIPv6() {
                return false;
            }

            @Override
            public void addNetworkAddressChangeListener(NetworkAddressChangeListener listener) {
            }

            @Override
            public void removeNetworkAddressChangeListener(NetworkAddressChangeListener listener) {
            }
        };
    }

    @NonNullByDefault
    private static class ThrottlingApi extends Shelly2ApiRpc {
        final Deque<Object> outcomes = new ArrayDeque<>();
        int callCount;

        ThrottlingApi(String thingName, ShellyThingTable thingTable, ShellyThingInterface thing,
                ShellyApiConfiguration config, WebSocketClient webSocketClient, ScheduledExecutorService scheduler) {
            super(thingName, thingTable, thing, config, webSocketClient, scheduler);
        }

        @Override
        public String httpPost(@Nullable Shelly2AuthChallenge auth, String data) throws ShellyApiException {
            callCount++;
            Object outcome = outcomes.poll();
            if (outcome instanceof ShellyApiException e) {
                throw e;
            }
            return (String) outcome;
        }
    }
}
