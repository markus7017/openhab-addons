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
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLYRPC_METHOD_GETCOMPONENTS;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.Shelly2ComponentEntry;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.Shelly2GetComponentsParams;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.Shelly2GetComponentsResult;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVirtualComponent;
import org.openhab.binding.shelly.internal.config.ShellyApiConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.handler.ShellyThingTable;
import org.openhab.core.net.NetworkAddressChangeListener;
import org.openhab.core.net.NetworkAddressService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link Shelly2ApiRpc#parseVirtualComponents}: verifies the {@code Shelly.GetComponents} response is
 * filtered to virtual-component types only and mapped into {@link ShellyVirtualComponent} correctly per type, and for
 * {@link Shelly2ApiRpc#refreshVirtualComponents}: the paged response is read until the device's reported total.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class Shelly2ApiRpcVirtualComponentsTest {

    private final Gson gson = new Gson();

    private Shelly2ComponentEntry entry(String key, @Nullable JsonObject config, @Nullable JsonObject status) {
        Shelly2ComponentEntry entry = new Shelly2ComponentEntry();
        entry.key = key;
        entry.config = config;
        entry.status = status;
        return entry;
    }

    private Shelly2GetComponentsResult result(Shelly2ComponentEntry... entries) {
        Shelly2GetComponentsResult result = new Shelly2GetComponentsResult();
        result.components = List.of(entries);
        return result;
    }

    private Shelly2GetComponentsResult page(int total, Shelly2ComponentEntry... entries) {
        Shelly2GetComponentsResult result = result(entries);
        result.total = total;
        return result;
    }

    private Shelly2ApiRpc apiServing(Map<Integer, Shelly2GetComponentsResult> pagesByOffset,
            ShellyDeviceProfile profile) throws ShellyApiException {
        ShellyThingInterface thing = mock(ShellyThingInterface.class);
        when(thing.getProfile()).thenReturn(profile);
        ShellyBindingConfiguration raw = ShellyBindingConfiguration
                .fromProperties(Map.of(ShellyBindingConfiguration.CONFIG_LOCAL_IP, "192.168.1.50"));
        ShellyApiConfiguration config = new ShellyApiConfiguration(new ShellyBindingRuntimeConfig(raw, 8080, nullNas()),
                "test-realm", "192.168.1.100");
        Shelly2ApiRpc api = spy(new Shelly2ApiRpc("test-vcomp", mock(ShellyThingTable.class), thing, config,
                mock(WebSocketClient.class), mock(ScheduledExecutorService.class)));
        doAnswer(invocation -> pagesByOffset
                .getOrDefault(((Shelly2GetComponentsParams) invocation.getArgument(1)).offset, result())).when(api)
                .apiRequest(eq(SHELLYRPC_METHOD_GETCOMPONENTS), any(), eq(Shelly2GetComponentsResult.class));
        return api;
    }

    private void verifyPagesRequested(Shelly2ApiRpc api, int count) throws ShellyApiException {
        verify(api, times(count)).apiRequest(eq(SHELLYRPC_METHOD_GETCOMPONENTS), any(),
                eq(Shelly2GetComponentsResult.class));
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

    @Test
    void booleanComponentParsesNameAndValue() {
        JsonObject config = new JsonObject();
        config.addProperty("name", "My Switch");
        JsonObject status = new JsonObject();
        status.addProperty("value", true);

        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("boolean:200", config, status)));

        assertEquals(1, list.size());
        ShellyVirtualComponent vc = list.get(0);
        assertEquals("boolean", vc.type);
        assertEquals(200, vc.id);
        assertEquals("My Switch", vc.name);
        assertNotNull(vc.value);
        assertTrue(vc.value.getAsBoolean());
    }

    @Test
    void numberComponentParsesMinMax() {
        JsonObject config = new JsonObject();
        config.addProperty("min", 0.0);
        config.addProperty("max", 100.0);
        JsonObject status = new JsonObject();
        status.addProperty("value", 42.5);

        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("number:201", config, status)));

        ShellyVirtualComponent vc = list.get(0);
        assertEquals("number", vc.type);
        assertEquals(201, vc.id);
        assertEquals(0.0, vc.min);
        assertEquals(100.0, vc.max);
        assertNotNull(vc.value);
        assertEquals(42.5, vc.value.getAsDouble());
    }

    @Test
    void textComponentParsesMaxLen() {
        JsonObject config = new JsonObject();
        config.addProperty("max_len", 64);

        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("text:202", config, null)));

        ShellyVirtualComponent vc = list.get(0);
        assertEquals("text", vc.type);
        assertEquals(Integer.valueOf(64), vc.maxLen);
        assertNull(vc.value);
    }

    @Test
    void enumComponentParsesOptions() {
        JsonObject config = new JsonObject();
        JsonArray options = new JsonArray();
        options.add("low");
        options.add("high");
        config.add("options", options);

        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("enum:203", config, null)));

        ShellyVirtualComponent vc = list.get(0);
        assertEquals("enum", vc.type);
        assertNotNull(vc.options);
        assertArrayEquals(new String[] { "low", "high" }, vc.options);
    }

    @Test
    void groupComponentExtractsMembers() {
        JsonArray members = new JsonArray();
        members.add("boolean:200");
        members.add("enum:203");
        JsonObject status = new JsonObject();
        status.add("value", members);

        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("group:204", null, status)));

        ShellyVirtualComponent vc = list.get(0);
        assertEquals("group", vc.type);
        assertEquals(List.of("boolean:200", "enum:203"), vc.groupMembers);
    }

    @Test
    void buttonComponentHasNoStatusValue() {
        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("button:205", null, new JsonObject())));

        ShellyVirtualComponent vc = list.get(0);
        assertEquals("button", vc.type);
        assertNull(vc.value);
    }

    @Test
    void nonVirtualComponentInSharedIdRangeIsIgnored() {
        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("presencezone:200", null, null), entry("lnm:201", null, null),
                        entry("bthomesensor:202", null, null), entry("boolean:203", null, null)));

        assertEquals(1, list.size());
        assertEquals(203, list.get(0).id);
    }

    @Test
    void keyWithoutColonIsIgnored() {
        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("boolean", null, null)));

        assertTrue(list.isEmpty());
    }

    @Test
    void nonNumericIdIsIgnored() {
        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("boolean:abc", null, null)));

        assertTrue(list.isEmpty());
    }

    @Test
    void nullComponentsListYieldsEmptyList() {
        assertTrue(Shelly2ApiRpc.parseVirtualComponents(gson, new Shelly2GetComponentsResult()).isEmpty());
    }

    @Test
    void nullResultYieldsEmptyList() {
        assertTrue(Shelly2ApiRpc.parseVirtualComponents(gson, null).isEmpty());
    }

    @Test
    void reportedJsonNullValueIsKeptAsJsonNullNotAsJavaNull() {
        JsonObject status = new JsonObject();
        status.add("value", JsonNull.INSTANCE);

        List<ShellyVirtualComponent> list = Shelly2ApiRpc.parseVirtualComponents(gson,
                result(entry("enum:203", null, status)));

        ShellyVirtualComponent vc = list.get(0);
        assertNotNull(vc.value);
        assertTrue(vc.value.isJsonNull());
    }

    @Test
    void componentsOnLaterPagesAreFetchedUntilTheReportedTotalIsReached() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        Shelly2ApiRpc api = apiServing(
                Map.of(0, page(3, entry("boolean:200", null, null), entry("number:201", null, null)), 2,
                        page(3, entry("text:202", null, null))),
                profile);

        api.refreshVirtualComponents(profile, true);

        assertEquals(List.of(200, 201, 202), profile.vComponents.stream().map(vc -> vc.id).toList());
        verifyPagesRequested(api, 2);
    }

    @Test
    void pagingStopsWhenTheDeviceDeliversLessThanTheTotalItReports() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        int totalTheDeviceNeverDelivers = 5;
        Shelly2ApiRpc api = apiServing(Map.of(0, page(totalTheDeviceNeverDelivers, entry("boolean:200", null, null))),
                profile);

        api.refreshVirtualComponents(profile, true);

        assertEquals(1, profile.vComponents.size());
        assertTrue(profile.vComponentsProbed);
        verifyPagesRequested(api, 2);
    }

    @Test
    void singlePageResponseWithoutTotalIsFetchedOnce() throws ShellyApiException {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        Shelly2ApiRpc api = apiServing(Map.of(0, result(entry("boolean:200", null, null))), profile);

        api.refreshVirtualComponents(profile, true);

        assertEquals(1, profile.vComponents.size());
        verifyPagesRequested(api, 1);
    }

    @Test
    void notifyStatusExtractsOnlyVirtualValueComponents() {
        String json = "{\"src\":\"shellyplus1-aabbcc\",\"method\":\"NotifyStatus\",\"params\":{\"ts\":1.0,"
                + "\"boolean:200\":{\"value\":true},\"number:201\":{\"value\":21.5},\"enum:202\":{\"value\":\"a\"},"
                + "\"button:203\":{},\"switch:0\":{\"output\":true}}}";

        Map<String, JsonObject> result = Shelly2ApiRpc.parseVirtualComponentStatus(json);

        assertEquals(3, result.size());
        assertTrue(result.get("boolean:200").get("value").getAsBoolean());
        assertEquals(21.5, result.get("number:201").get("value").getAsDouble());
        assertEquals("a", result.get("enum:202").get("value").getAsString());
    }

    @Test
    void notifyFullStatusReadsComponentsFromResult() {
        String json = "{\"result\":{\"text:204\":{\"value\":\"hi\"}}}";

        Map<String, JsonObject> result = Shelly2ApiRpc.parseVirtualComponentStatus(json);

        assertEquals("hi", result.get("text:204").get("value").getAsString());
    }

    @Test
    void malformedNotifyStatusYieldsNoComponents() {
        assertTrue(Shelly2ApiRpc.parseVirtualComponentStatus("not json {").isEmpty());
        assertTrue(Shelly2ApiRpc.parseVirtualComponentStatus("[]").isEmpty());
    }

    @Test
    void enumComponentParsesOptionTitles() {
        JsonObject config = gson.fromJson(
                "{\"options\":[\"low\",\"high\"],\"meta\":{\"ui\":{\"titles\":{\"low\":\"Low power\",\"high\":\"High power\"}}}}",
                JsonObject.class);

        ShellyVirtualComponent vc = Shelly2ApiRpc.parseVirtualComponents(gson, result(entry("enum:203", config, null)))
                .get(0);

        assertEquals(Map.of("low", "Low power", "high", "High power"), vc.optionTitles);
    }
}
