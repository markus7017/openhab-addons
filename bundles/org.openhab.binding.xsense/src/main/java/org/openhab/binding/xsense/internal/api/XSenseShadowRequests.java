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

import org.eclipse.jdt.annotation.NonNullByDefault;

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
}
