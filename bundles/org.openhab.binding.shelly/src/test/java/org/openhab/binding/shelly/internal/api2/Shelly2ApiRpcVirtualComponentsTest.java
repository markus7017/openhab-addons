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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.Shelly2ComponentEntry;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.Shelly2GetComponentsResult;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVirtualComponent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link Shelly2ApiRpc#parseVirtualComponents}: verifies the {@code Shelly.GetComponents} response is
 * filtered to virtual-component types only and mapped into {@link ShellyVirtualComponent} correctly per type.
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
}
