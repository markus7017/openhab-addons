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
package org.openhab.binding.xsense.internal;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link XSenseBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseBindingConstants {

    public static final String BINDING_ID = "xsense";

    // Bridge Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_HOME = new ThingTypeUID(BINDING_ID, "home");
    public static final ThingTypeUID THING_TYPE_STATION = new ThingTypeUID(BINDING_ID, "station");

    // Sensor Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SMOKE = new ThingTypeUID(BINDING_ID, "smoke");
    public static final ThingTypeUID THING_TYPE_WATER = new ThingTypeUID(BINDING_ID, "water");

    public static final Set<ThingTypeUID> SENSOR_THING_TYPES = Set.of(THING_TYPE_SMOKE, THING_TYPE_WATER);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_HOME,
            THING_TYPE_STATION, THING_TYPE_SMOKE, THING_TYPE_WATER);

    /**
     * Maps the X-Sense device type (model code as reported by the cloud API) to the thing type.
     * Models not listed here are reported by the discovery service as unsupported, including
     * models supported by other X-Sense sensor lines (CO, heat, thermo-hygrometer, security
     * accessories) planned for a follow-up release.
     */
    private static final Map<String, ThingTypeUID> DEVICE_TYPE_TO_THING_TYPE = Map.ofEntries(
            Map.entry("XS01-M", THING_TYPE_SMOKE), //
            Map.entry("XS0B-MR", THING_TYPE_SMOKE), //
            Map.entry("XS0D-MR", THING_TYPE_SMOKE), //
            Map.entry("SWS51", THING_TYPE_WATER), //
            Map.entry("SWS54", THING_TYPE_WATER));

    // Device type of the SBS50 base station as reported in the station "category" field
    public static final String STATION_TYPE_SBS50 = "SBS50";

    // Thing configuration parameters
    public static final String CONFIG_DEVICE_SN = "deviceSn";
    public static final String CONFIG_HOUSE_ID = "houseId";
    public static final String CONFIG_STATION_SN = "stationSn";

    // Thing properties
    public static final String PROPERTY_HOUSE_ID = "houseId";
    public static final String PROPERTY_HOUSE_NAME = "houseName";
    public static final String PROPERTY_ROOM_NAME = "roomName";
    public static final String PROPERTY_UNIQUE_ID = "uniqueId";

    // Channels
    public static final String CHANNEL_PATH = "info#path";

    /**
     * Returns the thing type for an X-Sense model code or null for unsupported models.
     */
    public static @Nullable ThingTypeUID thingTypeForDeviceType(String deviceType) {
        return DEVICE_TYPE_TO_THING_TYPE.get(deviceType);
    }
}
