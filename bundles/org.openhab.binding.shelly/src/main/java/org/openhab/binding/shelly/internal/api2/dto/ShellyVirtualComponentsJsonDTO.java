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

    public static class Shelly2GetComponentsParams {
        @SerializedName("dynamic_only")
        public Boolean dynamicOnly = true;
        public String[] include = { "status", "config" };
        public @Nullable Integer offset;
    }

    public static class Shelly2GetComponentsResult {
        public @Nullable List<Shelly2ComponentEntry> components;
        @SerializedName("cfg_rev")
        public @Nullable Integer cfgRev;
        public @Nullable Integer offset;
        public @Nullable Integer total;
    }

    public static class Shelly2ComponentEntry {
        public @Nullable String key; // e.g. "boolean:200"
        public @Nullable JsonObject status;
        public @Nullable JsonObject config;
    }

    /**
     * Config fields across all virtual component types (only the fields matching the component's own type are
     * populated by the device, the rest stay null).
     */
    public static class Shelly2VCompConfig {
        public @Nullable String name;
        public @Nullable Double min; // number
        public @Nullable Double max; // number
        @SerializedName("max_len")
        public @Nullable Integer maxLen; // text
        public @Nullable String[] options; // enum
    }

    /**
     * Status value shape differs by type (boolean/number/string/string-or-null/array-of-keys) and is kept as the
     * raw {@link JsonElement}; the caller interprets it based on the component's {@code type}.
     */
    public static class Shelly2VCompStatus {
        public @Nullable JsonElement value;
    }

    public static class Shelly2BooleanSetParams {
        public Integer id;
        public @Nullable Boolean value;
    }

    public static class Shelly2NumberSetParams {
        public Integer id;
        public @Nullable Double value;
    }

    public static class Shelly2TextSetParams {
        public Integer id;
        public @Nullable String value;
    }

    public static class Shelly2EnumSetParams {
        public Integer id;
        public @Nullable String value;
    }

    /**
     * Binding-internal representation of one discovered virtual component, built from a
     * {@link Shelly2ComponentEntry} plus its parsed {@link Shelly2VCompConfig}/current value.
     */
    public static class ShellyVirtualComponent {
        public String type = ""; // one of SHELLY2_VCOMP_xxx
        public int id;
        public @Nullable String name;
        public @Nullable Double min;
        public @Nullable Double max;
        public @Nullable Integer maxLen;
        public @Nullable String[] options;
        public @Nullable JsonElement value; // current status value, type-dependent; null for button
        public @Nullable List<String> groupMembers; // group type only, from status.value (e.g. "boolean:200")
    }
}
