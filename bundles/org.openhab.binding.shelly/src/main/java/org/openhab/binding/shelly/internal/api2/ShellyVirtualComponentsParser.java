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

import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.getString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponentEntry;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCConfig;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCGetComponentsResult;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCStatus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The {@link ShellyVirtualComponentsParser} maps the device's Virtual Components JSON ({@code Shelly.GetComponents}
 * results and NotifyStatus/NotifyFullStatus messages) into the binding's {@link ShellyVCComponent} model.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public final class ShellyVirtualComponentsParser {

    private ShellyVirtualComponentsParser() {
    }

    static List<ShellyVCComponent> parseVirtualComponents(Gson gson, @Nullable ShellyVCGetComponentsResult result) {
        List<ShellyVCComponent> list = new ArrayList<>();
        List<ShellyVCComponentEntry> entries = result != null ? result.components : null;
        if (entries == null) {
            return list;
        }
        for (ShellyVCComponentEntry entry : entries) {
            String key = getString(entry.key);
            int sep = key.indexOf(':');
            if (sep < 0) {
                continue;
            }
            String type = key.substring(0, sep);
            switch (type) {
                case SHELLY2_VCOMP_BOOLEAN:
                case SHELLY2_VCOMP_NUMBER:
                case SHELLY2_VCOMP_TEXT:
                case SHELLY2_VCOMP_ENUM:
                case SHELLY2_VCOMP_GROUP:
                case SHELLY2_VCOMP_BUTTON:
                    break;
                default:
                    continue; // presencezone/bthomesensor/lnm share the same id range, not ours
            }
            int id;
            try {
                id = Integer.parseInt(key.substring(sep + 1));
            } catch (NumberFormatException e) {
                continue;
            }

            ShellyVCComponent vc = new ShellyVCComponent();
            vc.type = type;
            vc.id = id;
            if (entry.config != null) {
                ShellyVCConfig vconfig = gson.fromJson(entry.config, ShellyVCConfig.class);
                if (vconfig != null) {
                    vc.name = vconfig.name;
                    vc.min = vconfig.min;
                    vc.max = vconfig.max;
                    vc.maxLen = vconfig.maxLen;
                    vc.options = vconfig.options;
                    ShellyVCConfig.ShellyVCMeta meta = vconfig.meta;
                    ShellyVCConfig.ShellyVCUi ui = meta != null ? meta.ui : null;
                    vc.optionTitles = ui != null ? parseOptionTitles(ui.titles, vconfig.options) : null;
                    vc.step = ui != null ? ui.step : null;
                    vc.unit = ui != null ? ui.unit : null;
                }
            }
            if (entry.status != null) {
                ShellyVCStatus vstatus = gson.fromJson(entry.status, ShellyVCStatus.class);
                vc.value = vstatus != null ? vstatus.value : null;
            }
            JsonElement value = vc.value;
            if (SHELLY2_VCOMP_GROUP.equals(type) && value != null && value.isJsonArray()) {
                List<String> members = new ArrayList<>();
                value.getAsJsonArray().forEach(el -> members.add(el.getAsString()));
                vc.groupMembers = members;
            }
            list.add(vc);
        }
        return list;
    }

    /**
     * {@code meta.ui.titles} is documented as an option-value -> display-text object, but some firmware sends a
     * plain array of display texts ordered against {@code options} instead; a bare {@code Map<String, String>}
     * field would let Gson throw a JsonSyntaxException on that shape and abort the whole probe. Accept both.
     */
    private static @Nullable Map<String, String> parseOptionTitles(@Nullable JsonElement titles,
            @Nullable String @Nullable [] options) {
        if (titles == null || titles.isJsonNull()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        if (titles.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : titles.getAsJsonObject().entrySet()) {
                JsonElement title = entry.getValue();
                if (!title.isJsonNull()) {
                    result.put(entry.getKey(), title.getAsString());
                }
            }
        } else if (titles.isJsonArray() && options != null) {
            JsonArray array = titles.getAsJsonArray();
            for (int i = 0; i < array.size() && i < options.length; i++) {
                JsonElement title = array.get(i);
                String option = options[i];
                if (!title.isJsonNull() && option != null) {
                    result.put(option, title.getAsString());
                }
            }
        } else {
            return null;
        }
        return result;
    }

    /**
     * Extracts the status objects of virtual components from a NotifyStatus/NotifyFullStatus message.
     * The device sends them under dynamic keys ("boolean:200") that the typed status DTO can't hold.
     */
    static Map<String, JsonObject> parseVirtualComponentStatus(String json) {
        Map<String, JsonObject> result = new HashMap<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return result;
            }
            JsonObject message = root.getAsJsonObject();
            JsonElement params = message.has("params") ? message.get("params") : message.get("result");
            if (params == null || !params.isJsonObject()) {
                return result;
            }
            for (Map.Entry<String, JsonElement> entry : params.getAsJsonObject().entrySet()) {
                String type = entry.getKey().substring(0, Math.max(0, entry.getKey().indexOf(':')));
                if (isVirtualValueType(type) && entry.getValue().isJsonObject()) {
                    result.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        } catch (JsonParseException e) {
            // not a status message we can inspect, the typed parsing reports real problems
        }
        return result;
    }

    private static boolean isVirtualValueType(String type) {
        return SHELLY2_VCOMP_BOOLEAN.equals(type) || SHELLY2_VCOMP_NUMBER.equals(type)
                || SHELLY2_VCOMP_TEXT.equals(type) || SHELLY2_VCOMP_ENUM.equals(type);
    }
}
