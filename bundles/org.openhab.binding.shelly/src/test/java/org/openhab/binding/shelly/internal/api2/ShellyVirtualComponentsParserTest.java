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
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCGetComponentsResult;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponentsParserTest {

    @Test
    void parseVirtualComponentsReadsConfigAndValueAndSkipsOtherComponents() {
        Gson gson = new Gson();
        String json = "{\"components\":["
                + "{\"key\":\"boolean:200\",\"config\":{\"id\":200,\"name\":\"My Switch\"},\"status\":{\"value\":true}},"
                + "{\"key\":\"number:201\",\"config\":{\"min\":0,\"max\":100,\"meta\":{\"ui\":{\"step\":0.5,\"unit\":\"%\"}}}},"
                + "{\"key\":\"text:202\",\"config\":{\"max_len\":64}},"
                + "{\"key\":\"enum:203\",\"config\":{\"options\":[\"low\",\"high\"]},\"status\":{\"value\":null}},"
                + "{\"key\":\"group:204\",\"status\":{\"value\":[\"boolean:200\"]}},"
                + "{\"key\":\"presencezone:205\"},{\"key\":\"lnm:206\"},{\"key\":\"bthomesensor:207\"}]}";

        List<ShellyVCComponent> list = parseVirtualComponents(gson,
                gson.fromJson(json, ShellyVCGetComponentsResult.class));

        assertEquals(List.of("boolean200", "number201", "text202", "enum203", "group204"),
                list.stream().map(vc -> vc.type + vc.id).toList());
        assertEquals("My Switch", list.get(0).name);
        assertEquals(JsonParser.parseString("true"), list.get(0).value);
        assertEquals(100.0, list.get(1).max);
        assertEquals(64, list.get(2).maxLen);
        assertArrayEquals(new String[] { "low", "high" }, list.get(3).options);
        assertEquals(JsonNull.INSTANCE, list.get(3).value);
        assertEquals(JsonParser.parseString("[\"boolean:200\"]"), list.get(4).value);
    }

    @Test
    void parseVirtualComponentStatusExtractsValueAndGroupComponents() {
        String json = "{\"src\":\"shellyplus1-aabbcc\",\"method\":\"NotifyStatus\",\"params\":{\"ts\":1.0,"
                + "\"boolean:200\":{\"value\":true},\"number:201\":{\"value\":21.5},\"enum:202\":{\"value\":\"a\"},"
                + "\"button:203\":{},\"group:204\":{\"value\":[\"boolean:200\"]},\"switch:0\":{\"output\":true}}}";

        Map<String, JsonObject> result = parseVirtualComponentStatus(json);

        assertEquals(
                Map.of("boolean:200", "true", "number:201", "21.5", "enum:202", "\"a\"", "group:204",
                        "[\"boolean:200\"]"),
                result.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue().get("value")))));
    }

    @Test
    void parseVirtualComponentStatusIgnoresMessagesWithoutVirtualComponentValues() {
        assertEquals(Map.of(), parseVirtualComponentStatus(
                "{\"method\":\"NotifyStatus\",\"params\":" + "{\"switch:0\":{\"output\":true},\"button:203\":{}}}"));
    }
}
