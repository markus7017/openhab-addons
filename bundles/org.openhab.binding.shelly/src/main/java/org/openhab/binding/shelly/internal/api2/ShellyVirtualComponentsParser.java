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

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLY2_VCOMP_GROUP;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.getString;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponentEntry;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCGetComponentsResult;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * The {@link ShellyVirtualComponentsParser} maps the device's {@code Shelly.GetComponents} results into
 * {@link ShellyVCComponent}s.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public final class ShellyVirtualComponentsParser {
    // presencezone/bthomesensor/lnm share the id range, but aren't Virtual Components
    private static final Set<String> TYPES = Set.of(CHANNEL_VCOMP_BOOLEAN, CHANNEL_VCOMP_NUMBER, CHANNEL_VCOMP_TEXT,
            CHANNEL_VCOMP_ENUM, SHELLY2_VCOMP_GROUP, CHANNEL_VCOMP_BUTTON);

    private ShellyVirtualComponentsParser() {
    }

    static List<ShellyVCComponent> parseVirtualComponents(Gson gson, @Nullable ShellyVCGetComponentsResult result) {
        List<ShellyVCComponent> list = new ArrayList<>();
        List<ShellyVCComponentEntry> entries = result != null ? result.components : null;
        for (ShellyVCComponentEntry entry : entries != null ? entries : List.<ShellyVCComponentEntry> of()) {
            String[] key = getString(entry.key).split(":");
            if (key.length != 2 || !TYPES.contains(key[0]) || !key[1].matches("\\d+")) {
                continue;
            }
            ShellyVCComponent vc = entry.config != null ? gson.fromJson(entry.config, ShellyVCComponent.class) : null;
            vc = vc != null ? vc : new ShellyVCComponent();
            vc.type = key[0];
            vc.id = Integer.parseInt(key[1]);
            JsonObject status = entry.status;
            vc.value = status != null ? status.get("value") : null;
            list.add(vc);
        }
        return list;
    }
}
