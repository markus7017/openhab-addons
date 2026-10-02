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
package org.openhab.binding.shelly.internal.api2.dto;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * {@link ShellyVirtualComponentsJsonDTO} includes constants and structures used for the Shelly Virtual Components
 * (Boolean/Number/Text/Enum/Group) JSON mapping.
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyVirtualComponentsJsonDTO {
    public static final String SHELLYRPC_METHOD_GETCOMPONENTS = "Shelly.GetComponents";
    public static final String SHELLY2_VCOMP_GROUP = "group";

    // reported by Number.GetConfig when min/max were never customized
    public static final double SHELLY2_VCOMP_NUMBER_MIN_SENTINEL = -999999999999999d;
    public static final double SHELLY2_VCOMP_NUMBER_MAX_SENTINEL = 999999999999999d;

    public static class ShellyVCGetComponentsParams {
        @SerializedName("dynamic_only")
        public Boolean dynamicOnly = true;
        public String[] include = { "status", "config" };
        public @Nullable Integer offset;
    }

    public static class ShellyVCGetComponentsResult {
        public @Nullable List<ShellyVCComponentEntry> components;
        public @Nullable Integer total;
    }

    public static class ShellyVCComponentEntry {
        public @Nullable String key; // e.g. "boolean:200"
        public @Nullable JsonObject status;
        public @Nullable JsonObject config;
    }

    public static class ShellyVCSetParams {
        public Integer id;
        public @Nullable Object value;
    }

    /**
     * Parsed from the component's config, type and value are filled from key and status.
     */
    public static class ShellyVCComponent {
        public transient String type = "";
        public int id;
        public transient volatile @Nullable JsonElement value; // type-dependent, group: array of member keys
        public @Nullable String name;
        public @Nullable Double min; // number
        public @Nullable Double max; // number
        @SerializedName("max_len")
        public @Nullable Integer maxLen; // text
        public @Nullable String[] options; // enum
        public @Nullable ShellyVCMeta meta;

        public static class ShellyVCMeta {
            public @Nullable ShellyVCUi ui;
        }

        public static class ShellyVCUi {
            public @Nullable JsonElement titles; // enum: object option -> title, some firmware sends an array
            public @Nullable Double step; // number
            public @Nullable String unit; // number
        }
    }
}
