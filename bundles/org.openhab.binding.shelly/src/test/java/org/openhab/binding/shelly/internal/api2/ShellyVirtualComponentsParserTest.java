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
import static org.openhab.binding.shelly.internal.api2.ShellyVirtualComponentsParser.*;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponentEntry;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCGetComponentsResult;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponentsParserTest {

    private final Gson gson = new Gson();

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

    private ShellyVCComponent parseSingle(String key, String configJson) {
        return parseVirtualComponents(gson, result(entry(key, gson.fromJson(configJson, JsonObject.class), null)))
                .get(0);
    }

    @Test
    void booleanComponentParsesNameAndValue() {
        JsonObject config = new JsonObject();
        config.addProperty("name", "My Switch");
        JsonObject status = new JsonObject();
        status.addProperty("value", true);

        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("boolean:200", config, status)));

        assertEquals(1, list.size());
        ShellyVCComponent vc = list.get(0);
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

        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("number:201", config, status)));

        ShellyVCComponent vc = list.get(0);
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

        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("text:202", config, null)));

        ShellyVCComponent vc = list.get(0);
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

        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("enum:203", config, null)));

        ShellyVCComponent vc = list.get(0);
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

        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("group:204", null, status)));

        ShellyVCComponent vc = list.get(0);
        assertEquals("group", vc.type);
        assertEquals(List.of("boolean:200", "enum:203"), vc.groupMembers);
    }

    @Test
    void buttonComponentHasNoStatusValue() {
        List<ShellyVCComponent> list = parseVirtualComponents(gson,
                result(entry("button:205", null, new JsonObject())));

        ShellyVCComponent vc = list.get(0);
        assertEquals("button", vc.type);
        assertNull(vc.value);
    }

    @Test
    void nonVirtualComponentInSharedIdRangeIsIgnored() {
        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("presencezone:200", null, null),
                entry("lnm:201", null, null), entry("bthomesensor:202", null, null), entry("boolean:203", null, null)));

        assertEquals(1, list.size());
        assertEquals(203, list.get(0).id);
    }

    @Test
    void keyWithoutColonIsIgnored() {
        assertTrue(parseVirtualComponents(gson, result(entry("boolean", null, null))).isEmpty());
    }

    @Test
    void nonNumericIdIsIgnored() {
        assertTrue(parseVirtualComponents(gson, result(entry("boolean:abc", null, null))).isEmpty());
    }

    @Test
    void nullComponentsListYieldsEmptyList() {
        assertTrue(parseVirtualComponents(gson, new ShellyVCGetComponentsResult()).isEmpty());
    }

    @Test
    void nullResultYieldsEmptyList() {
        assertTrue(parseVirtualComponents(gson, null).isEmpty());
    }

    @Test
    void reportedJsonNullValueIsKeptAsJsonNullNotAsJavaNull() {
        JsonObject status = new JsonObject();
        status.add("value", JsonNull.INSTANCE);

        List<ShellyVCComponent> list = parseVirtualComponents(gson, result(entry("enum:203", null, status)));

        ShellyVCComponent vc = list.get(0);
        assertNotNull(vc.value);
        assertTrue(vc.value.isJsonNull());
    }

    @Test
    void numberComponentParsesStepAndUnitFromMetaUi() {
        ShellyVCComponent vc = parseSingle("number:201",
                "{\"min\":0.0,\"max\":100.0,\"meta\":{\"ui\":{\"step\":0.5,\"unit\":\"%\"}}}");

        assertEquals(0.5, vc.step);
        assertEquals("%", vc.unit);
    }

    @Test
    void numberComponentWithoutMetaUiLeavesStepAndUnitNull() {
        ShellyVCComponent vc = parseSingle("number:201", "{\"min\":0.0,\"max\":100.0}");

        assertNull(vc.step);
        assertNull(vc.unit);
    }

    @Test
    void enumComponentParsesOptionTitles() {
        ShellyVCComponent vc = parseSingle("enum:203",
                "{\"options\":[\"low\",\"high\"],\"meta\":{\"ui\":{\"titles\":{\"low\":\"Low power\",\"high\":\"High power\"}}}}");

        assertEquals(Map.of("low", "Low power", "high", "High power"), vc.optionTitles);
    }

    @Test
    void enumComponentMapsArrayShapedOptionTitlesToOptionsByPosition() {
        ShellyVCComponent vc = parseSingle("enum:203",
                "{\"options\":[\"low\",\"high\"],\"meta\":{\"ui\":{\"titles\":[\"Low power\",\"High power\"]}}}");

        assertEquals(Map.of("low", "Low power", "high", "High power"), vc.optionTitles);
    }

    @Test
    void arrayShapedOptionTitlesLongerThanOptionsAreTruncated() {
        ShellyVCComponent vc = parseSingle("enum:203",
                "{\"options\":[\"low\"],\"meta\":{\"ui\":{\"titles\":[\"Low power\",\"High power\"]}}}");

        assertEquals(Map.of("low", "Low power"), vc.optionTitles);
    }

    @Test
    void arrayShapedOptionTitlesWithoutOptionsAreDropped() {
        ShellyVCComponent vc = parseSingle("enum:203", "{\"meta\":{\"ui\":{\"titles\":[\"Low power\"]}}}");

        assertNull(vc.optionTitles);
    }

    @Test
    void notifyStatusExtractsOnlyVirtualValueComponents() {
        String json = "{\"src\":\"shellyplus1-aabbcc\",\"method\":\"NotifyStatus\",\"params\":{\"ts\":1.0,"
                + "\"boolean:200\":{\"value\":true},\"number:201\":{\"value\":21.5},\"enum:202\":{\"value\":\"a\"},"
                + "\"button:203\":{},\"switch:0\":{\"output\":true}}}";

        Map<String, JsonObject> result = parseVirtualComponentStatus(json);

        assertEquals(3, result.size());
        assertTrue(result.get("boolean:200").get("value").getAsBoolean());
        assertEquals(21.5, result.get("number:201").get("value").getAsDouble());
        assertEquals("a", result.get("enum:202").get("value").getAsString());
    }

    @Test
    void notifyFullStatusReadsComponentsFromResult() {
        Map<String, JsonObject> result = parseVirtualComponentStatus("{\"result\":{\"text:204\":{\"value\":\"hi\"}}}");

        assertEquals("hi", result.get("text:204").get("value").getAsString());
    }

    @Test
    void malformedNotifyStatusYieldsNoComponents() {
        assertTrue(parseVirtualComponentStatus("not json {").isEmpty());
        assertTrue(parseVirtualComponentStatus("[]").isEmpty());
    }
}
