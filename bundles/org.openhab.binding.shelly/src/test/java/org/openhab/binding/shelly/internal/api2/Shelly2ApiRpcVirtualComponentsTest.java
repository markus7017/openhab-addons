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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.CHANNEL_GROUP_VCOMPONENTS;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLYRPC_METHOD_GETCOMPONENTS;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponentEntry;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCGetComponentsParams;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCGetComponentsResult;
import org.openhab.binding.shelly.internal.config.ShellyApiConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.handler.ShellyThingTable;
import org.openhab.binding.shelly.internal.handler.ShellyVirtualComponents;
import org.openhab.core.net.NetworkAddressChangeListener;
import org.openhab.core.net.NetworkAddressService;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class Shelly2ApiRpcVirtualComponentsTest {

    private ShellyVCComponentEntry entry(String key, @Nullable JsonObject config, @Nullable JsonObject status) {
        ShellyVCComponentEntry entry = new ShellyVCComponentEntry();
        entry.key = key;
        entry.config = config;
        entry.status = status;
        return entry;
    }

    private ShellyVCGetComponentsResult result(ShellyVCComponentEntry... entries) {
        ShellyVCGetComponentsResult result = new ShellyVCGetComponentsResult();
        result.components = List.of(entries);
        return result;
    }

    private ShellyVCGetComponentsResult page(int total, ShellyVCComponentEntry... entries) {
        ShellyVCGetComponentsResult result = result(entries);
        result.total = total;
        return result;
    }

    private Shelly2ApiRpc rpc(ShellyThingInterface thing) {
        ShellyBindingConfiguration raw = ShellyBindingConfiguration
                .fromProperties(Map.of(ShellyBindingConfiguration.CONFIG_LOCAL_IP, "192.168.1.50"));
        ShellyApiConfiguration config = new ShellyApiConfiguration(new ShellyBindingRuntimeConfig(raw, 8080, nullNas()),
                "test-realm", "192.168.1.100");
        return new Shelly2ApiRpc("test-vcomp", mock(ShellyThingTable.class), thing, config, mock(WebSocketClient.class),
                mock(ScheduledExecutorService.class));
    }

    private Shelly2ApiRpc apiServing(Map<Integer, ShellyVCGetComponentsResult> pagesByOffset,
            ShellyDeviceProfile profile) throws ShellyApiException {
        ShellyThingInterface thing = mock(ShellyThingInterface.class);
        when(thing.getProfile()).thenReturn(profile);
        Shelly2ApiRpc api = spy(rpc(thing));
        doAnswer(invocation -> pagesByOffset
                .getOrDefault(((ShellyVCGetComponentsParams) invocation.getArgument(1)).offset, result())).when(api)
                .apiRequest(eq(SHELLYRPC_METHOD_GETCOMPONENTS), any(), eq(ShellyVCGetComponentsResult.class));
        return api;
    }

    private void verifyPagesRequested(Shelly2ApiRpc api, int count) throws ShellyApiException {
        verify(api, times(count)).apiRequest(eq(SHELLYRPC_METHOD_GETCOMPONENTS), any(),
                eq(ShellyVCGetComponentsResult.class));
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

    @ParameterizedTest
    @CsvSource({ "3, true, 3", "5, false, 2" })
    void pagingFollowsTheReportedTotalUntilAPageIsEmpty(int total, boolean secondPage, int expectedComponents)
            throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        ShellyVCGetComponentsResult first = page(total, entry("boolean:200", null, null),
                entry("number:201", null, null));
        Shelly2ApiRpc api = apiServing(
                secondPage ? Map.of(0, first, 2, page(total, entry("text:202", null, null))) : Map.of(0, first),
                profile);

        api.refreshVirtualComponents(profile, true);

        assertEquals(expectedComponents, profile.vComponents.size());
        assertTrue(profile.vComponentsProbed);
        verifyPagesRequested(api, 2);
    }

    @Test
    void unparsableComponentConfigLeavesTheDeviceUnprobedInsteadOfThrowing() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        JsonObject config = new JsonObject();
        config.addProperty("min", "not a number");
        Shelly2ApiRpc api = apiServing(Map.of(0, result(entry("number:201", config, null))), profile);

        assertDoesNotThrow(() -> api.refreshVirtualComponents(profile, true));

        assertFalse(profile.vComponentsProbed);
        assertTrue(profile.vComponents.isEmpty());
    }

    @Test
    void unprobedDeviceIsReadAgainOnTheRegularRefresh() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        Shelly2ApiRpc api = apiServing(Map.of(0, result(entry("boolean:200", null, null))), profile);

        api.refreshVirtualComponents(profile, false);

        assertTrue(profile.vComponentsProbed);
        assertEquals(1, profile.vComponents.size());
    }

    @Test
    void deviceWithoutComponentsIsNotReadAgainOnTheRegularRefresh() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.vComponentsProbed = true;
        Shelly2ApiRpc api = apiServing(Map.of(), profile);

        api.refreshVirtualComponents(profile, false);

        verifyPagesRequested(api, 0);
    }

    @Test
    void valuePushedWhileReadingTheComponentsIsKept() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        ShellyThingInterface thing = mock(ShellyThingInterface.class);
        when(thing.getProfile()).thenReturn(profile);
        Shelly2ApiRpc api = spy(rpc(thing));
        JsonObject stale = JsonParser.parseString("{\"value\":true}").getAsJsonObject();
        doAnswer(invocation -> result(entry("boolean:200", null, stale))).doAnswer(invocation -> {
            ShellyVirtualComponents.updateVirtualComponentValues(thing, profile,
                    Map.of("boolean:200", JsonParser.parseString("{\"value\":false}").getAsJsonObject()));
            return result(entry("boolean:200", null, stale));
        }).when(api).apiRequest(eq(SHELLYRPC_METHOD_GETCOMPONENTS), any(), eq(ShellyVCGetComponentsResult.class));

        api.refreshVirtualComponents(profile, true);
        api.refreshVirtualComponents(profile, true);

        assertEquals(new JsonPrimitive(false), profile.vComponents.get(0).value);
    }

    @ParameterizedTest
    @CsvSource({ "true, 1", "false, 0" })
    void virtualButtonEventFiresItsTriggerChannelOnlyWhenSupported(boolean supported, int triggers)
            throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.vComponentsSupported = supported;
        ShellyThingInterface thing = mock(ShellyThingInterface.class);
        when(thing.getProfile()).thenReturn(profile);

        rpc(thing).onNotifyEvent("{\"src\":\"shellyplus1-test\",\"params\":{\"events\":"
                + "[{\"id\":205,\"event\":\"single_push\",\"component\":\"button:205\"}]}}");

        verify(thing, times(triggers)).triggerChannel(CHANNEL_GROUP_VCOMPONENTS, "button205", "SHORT_PRESSED");
    }
}
