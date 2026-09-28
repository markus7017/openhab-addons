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
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * {@link ShellyVirtualComponentsJsonDTO} includes constants and structures used for the Shelly Virtual Components
 * (Boolean/Number/Text/Enum/Group/Button) JSON mapping and processing. Available on Gen3, Gen4 and Gen2 "Pro"
 * devices; discovered/refreshed via {@code Shelly.GetComponents} rather than a dedicated list method.
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyVirtualComponentsJsonDTO {
    public static final String SHELLYRPC_METHOD_GETCOMPONENTS = "Shelly.GetComponents";
    public static final String SHELLYRPC_METHOD_BOOLEAN_SET = "Boolean.Set";
    public static final String SHELLYRPC_METHOD_NUMBER_SET = "Number.Set";
    public static final String SHELLYRPC_METHOD_TEXT_SET = "Text.Set";
    public static final String SHELLYRPC_METHOD_ENUM_SET = "Enum.Set";

    public static final String SHELLY2_VCOMP_BOOLEAN = "boolean";
    public static final String SHELLY2_VCOMP_NUMBER = "number";
    public static final String SHELLY2_VCOMP_TEXT = "text";
    public static final String SHELLY2_VCOMP_ENUM = "enum";
    public static final String SHELLY2_VCOMP_GROUP = "group";
    public static final String SHELLY2_VCOMP_BUTTON = "button";

    public static final String SHELLY2_VCOMP_BOOLEAN_PREFIX = SHELLY2_VCOMP_BOOLEAN + ":";
    public static final String SHELLY2_VCOMP_NUMBER_PREFIX = SHELLY2_VCOMP_NUMBER + ":";
    public static final String SHELLY2_VCOMP_TEXT_PREFIX = SHELLY2_VCOMP_TEXT + ":";
    public static final String SHELLY2_VCOMP_ENUM_PREFIX = SHELLY2_VCOMP_ENUM + ":";
    public static final String SHELLY2_VCOMP_GROUP_PREFIX = SHELLY2_VCOMP_GROUP + ":";
    public static final String SHELLY2_VCOMP_BUTTON_PREFIX = SHELLY2_VCOMP_BUTTON + ":";

    public static class ShellyVCGetComponentsParams {
        @SerializedName("dynamic_only")
        public Boolean dynamicOnly = true;
        public String[] include = { "status", "config" };
        public @Nullable Integer offset;
    }

    public static class ShellyVCGetComponentsResult {
        public @Nullable List<ShellyVCComponentEntry> components;
        @SerializedName("cfg_rev")
        public @Nullable Integer cfgRev;
        public @Nullable Integer offset;
        public @Nullable Integer total;
    }

    public static class ShellyVCComponentEntry {
        public @Nullable String key; // e.g. "boolean:200"
        public @Nullable JsonObject status;
        public @Nullable JsonObject config;
    }

    /**
     * Config fields across all virtual component types (only the fields matching the component's own type are
     * populated by the device, the rest stay null).
     */
    public static class ShellyVCConfig {
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
            public @Nullable Map<String, String> titles; // enum option -> display text
            public @Nullable Double step; // number
            public @Nullable String unit; // number
        }
    }

    /**
     * The device always returns {@code min}/{@code max} on {@code Number.GetConfig}, defaulting to these sentinel
     * values when the user never customized them. Callers must filter them out before using min/max for a
     * {@code StateDescription} to avoid presenting a meaningless full-range slider.
     */
    public static final double SHELLY2_VCOMP_NUMBER_MIN_SENTINEL = -999999999999999d;
    public static final double SHELLY2_VCOMP_NUMBER_MAX_SENTINEL = 999999999999999d;

    /**
     * Status value shape differs by type (boolean/number/string/string-or-null/array-of-keys) and is kept as the
     * raw {@link JsonElement}; the caller interprets it based on the component's {@code type}.
     */
    public static class ShellyVCStatus {
        public @Nullable JsonElement value;
    }

    public static class ShellyVCBooleanSetParams {
        public Integer id;
        public @Nullable Boolean value;
    }

    public static class ShellyVCNumberSetParams {
        public Integer id;
        public @Nullable Double value;
    }

    public static class ShellyVCTextSetParams {
        public Integer id;
        public @Nullable String value;
    }

    public static class ShellyVCEnumSetParams {
        public Integer id;
        public @Nullable String value;
    }

    /**
     * Binding-internal representation of one discovered virtual component, built from a
     * {@link ShellyVCComponentEntry} plus its parsed {@link ShellyVCConfig}/current value.
     */
    public static class ShellyVCComponent {
        public String type = ""; // one of SHELLY2_VCOMP_xxx
        public int id;
        public @Nullable String name;
        public @Nullable Double min;
        public @Nullable Double max;
        public @Nullable Integer maxLen;
        public @Nullable String[] options;
        public @Nullable Map<String, String> optionTitles; // enum option -> display text as set in the Shelly app
        public @Nullable Double step; // number
        public @Nullable String unit; // number
        public @Nullable JsonElement value; // current status value, type-dependent; null for button
        public @Nullable List<String> groupMembers; // group type only, from status.value (e.g. "boolean:200")
    }
}
