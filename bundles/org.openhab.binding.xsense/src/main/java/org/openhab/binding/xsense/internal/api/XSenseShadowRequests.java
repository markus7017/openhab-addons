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
package org.openhab.binding.xsense.internal.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * The {@link XSenseShadowRequests} builds the AWS IoT device-shadow payloads the X-Sense app
 * writes to control a base station. The payloads mirror the app verbatim (field names, values and
 * the redundant source/userParam fields included), since the cloud side validates them.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseShadowRequests {

    /** Named shadow the app writes arm/disarm (safeMode) requests to. */
    public static final String SHADOW_NAME_APP_MODE = "2nd_appmode";

    /**
     * Per-model mute request: {@code topic} is the named shadow written (the REST/MQTT "page"),
     * {@code shadow} is the JSON "shadow" field inside the desired state, {@code extra} carries the
     * remaining model-specific fields (e.g. muteType, userParam, silenceTime). Field names and
     * values mirror the X-Sense app verbatim, since the cloud side validates them per model.
     */
    private static final class MuteDefinition {
        final String topic;
        final String shadow;
        final Map<String, String> extra;

        MuteDefinition(String topic, String shadow, Map<String, String> extra) {
            this.topic = topic;
            this.shadow = shadow;
            this.extra = extra;
        }
    }

    // Device model code (Thing.PROPERTY_MODEL_ID) -> mute shadow request. Models without a mute
    // channel (door, motion, keypad, strobe, thermo-hygro) are intentionally not listed here.
    private static final Map<String, MuteDefinition> MUTE_DEFINITIONS = Map.ofEntries(
            Map.entry("XS01-M", new MuteDefinition("2nd_appmute", "appMute", Map.of())),
            Map.entry("XS0B-MR", new MuteDefinition("2nd_appmute", "appMute", Map.of())),
            Map.entry("XS0D-MR", new MuteDefinition("2nd_appmute", "appMute", Map.of())),
            Map.entry("XC01-M", new MuteDefinition("2nd_appmute", "appCoMute", Map.of("muteType", "1"))),
            Map.entry("SC07-MR", new MuteDefinition("2nd_appmute", "appSc07mrMute", Map.of("muteType", "1"))),
            Map.entry("XP0A-MR",
                    new MuteDefinition("2nd_appmute", "appXp0amrMute",
                            orderedMap("muteType", "1", "userParam", "source=1"))),
            Map.entry("XH02-M",
                    new MuteDefinition("2nd_appmute", "appXh02mMute",
                            orderedMap("muteType", "1", "userParam", "source=1"))),
            Map.entry("SWS51",
                    new MuteDefinition("2nd_appwater", "appWater", orderedMap("silenceTime", "", "setType", "0"))),
            Map.entry("SWS54",
                    new MuteDefinition("2nd_appwater", "appWater", orderedMap("silenceTime", "", "setType", "0"))),
            Map.entry("SAL51", new MuteDefinition("2nd_appmute", "appListener", Map.of("muteType", "1"))),
            Map.entry("SAL100", new MuteDefinition("2nd_appmute", "appListener", Map.of("muteType", "1"))),
            Map.entry("SDA51", new MuteDefinition("2nd_driveway", "appDriveway", Map.of("mute", "1"))),
            Map.entry("SMA51", new MuteDefinition("2nd_appmailmute", "appMailMute", Map.of("muteType", "1"))),
            Map.entry("SMA11", new MuteDefinition("2nd_appmailmute", "appMailMute", Map.of("muteType", "1"))));

    /**
     * Builds a map with deterministic (insertion-order) iteration, unlike {@code Map.of(...)} whose
     * iteration order is randomized per JVM run for 2+ entries. Needed here since the entries are
     * serialized directly into the JSON body.
     */
    private static Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // HTML escaping must stay off so "userParam":"source=1" is sent verbatim, not as =
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private XSenseShadowRequests() {
    }

    /**
     * Returns the shadow thing name of a station: device type prefix + serial number (e.g.
     * "SBS50" + station SN), as used in the shadow REST path and MQTT topics.
     */
    public static String stationThingName(String category, String stationSn) {
        return category + stationSn;
    }

    /**
     * Builds the appMode shadow body requesting the given safe mode, matching the app payload:
     * {@code {"state":{"desired":{"shadow":"appMode","safeMode":…,"stationSN":…,"source":"1",
     * "forceArm":"0","userId":…,"userParam":"source=1"}}}}.
     *
     * @param mode the requested safe mode
     * @param stationSn serial number of the station
     * @param userId the cloud user id (Cognito SRP user id) issuing the request
     * @return the JSON body for the shadow update request
     */
    public static String appModeDesiredState(XSenseSafeMode mode, String stationSn, String userId) {
        JsonObject desired = new JsonObject();
        desired.addProperty("shadow", "appMode");
        desired.addProperty("safeMode", mode.getValue());
        desired.addProperty("stationSN", stationSn);
        desired.addProperty("source", "1");
        desired.addProperty("forceArm", "0");
        desired.addProperty("userId", userId);
        desired.addProperty("userParam", "source=1");
        JsonObject state = new JsonObject();
        state.add("desired", desired);
        JsonObject body = new JsonObject();
        body.add("state", state);
        return GSON.toJson(body);
    }

    /**
     * Returns the named shadow a mute request for the given device model is written to, or
     * {@code null} if the model has no known mute mechanism (e.g. door/motion/keypad/strobe).
     */
    public static @Nullable String muteTopic(String deviceModel) {
        MuteDefinition definition = MUTE_DEFINITIONS.get(deviceModel);
        return definition != null ? definition.topic : null;
    }

    /**
     * Builds the mute shadow body for the given device model, matching the app payload:
     * {@code {"state":{"desired":{"deviceSN":…,"shadow":…,"stationSN":…,"userId":…,…model-specific}}}}.
     * Returns {@code null} if the model has no known mute mechanism.
     *
     * @param deviceModel device type/model code (e.g. "XS01-M")
     * @param stationSn serial number of the parent station
     * @param deviceSn serial number of the device to mute
     * @param userId the cloud user id (Cognito SRP user id) issuing the request
     * @return the JSON body for the shadow update request, or {@code null} if unsupported
     */
    public static @Nullable String muteDesiredState(String deviceModel, String stationSn, String deviceSn,
            String userId) {
        MuteDefinition definition = MUTE_DEFINITIONS.get(deviceModel);
        if (definition == null) {
            return null;
        }
        JsonObject desired = new JsonObject();
        desired.addProperty("deviceSN", deviceSn);
        desired.addProperty("shadow", definition.shadow);
        desired.addProperty("stationSN", stationSn);
        desired.addProperty("userId", userId);
        definition.extra.forEach(desired::addProperty);
        JsonObject state = new JsonObject();
        state.add("desired", desired);
        JsonObject body = new JsonObject();
        body.add("state", state);
        return GSON.toJson(body);
    }
}
