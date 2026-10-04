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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * {@link ShellyPillJsonDTO} includes constants and structures used to read the peripherals of The Pill by Shelly,
 * which are dynamic components omitted by Shelly.GetStatus and Shelly.GetConfig.
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyPillJsonDTO {
    public static final String SHELLYRPC_METHOD_GETCOMPONENTS = "Shelly.GetComponents";
    public static final String SHELLY2_GETCOMPONENTS_STATUS = "status";
    public static final String SHELLY2_GETCOMPONENTS_CONFIG = "config";
    public static final int SHELLY2_PILL_COMPONENT_BASE_ID = 200;

    public static class Shelly2GetComponentsParams {
        public @Nullable Integer offset;
        public List<String> include = List.of(SHELLY2_GETCOMPONENTS_STATUS, SHELLY2_GETCOMPONENTS_CONFIG);
        @SerializedName("dynamic_only")
        public Boolean dynamicOnly = true;
    }

    public static class Shelly2GetComponentsResult {
        public @Nullable ArrayList<Shelly2Component> components;
        public @Nullable Integer offset;
        public @Nullable Integer total;
    }

    public static class Shelly2Component {
        public @Nullable String key;
        public @Nullable JsonObject status;
        public @Nullable JsonObject config;
    }
}
