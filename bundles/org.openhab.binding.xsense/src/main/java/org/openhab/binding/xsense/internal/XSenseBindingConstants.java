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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
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
    public static final ThingTypeUID THING_TYPE_CO = new ThingTypeUID(BINDING_ID, "co");
    public static final ThingTypeUID THING_TYPE_SMOKECO = new ThingTypeUID(BINDING_ID, "smokeco");
    public static final ThingTypeUID THING_TYPE_HEAT = new ThingTypeUID(BINDING_ID, "heat");
    public static final ThingTypeUID THING_TYPE_WATER = new ThingTypeUID(BINDING_ID, "water");
    public static final ThingTypeUID THING_TYPE_THERMOHYGRO = new ThingTypeUID(BINDING_ID, "thermohygro");

    public static final Set<ThingTypeUID> SENSOR_THING_TYPES = Set.of(THING_TYPE_SMOKE, THING_TYPE_CO,
            THING_TYPE_SMOKECO, THING_TYPE_HEAT, THING_TYPE_WATER, THING_TYPE_THERMOHYGRO);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_HOME,
            THING_TYPE_STATION, THING_TYPE_SMOKE, THING_TYPE_CO, THING_TYPE_SMOKECO, THING_TYPE_HEAT, THING_TYPE_WATER,
            THING_TYPE_THERMOHYGRO);

    // Thing properties
    public static final String PROPERTY_HOUSE_ID = "houseId";
    public static final String PROPERTY_HOUSE_NAME = "houseName";
    public static final String PROPERTY_ROOM_NAME = "roomName";
    public static final String PROPERTY_UNIQUE_ID = "uniqueId";

    // Channels
    public static final String CHANNEL_PATH = "info#path";
}
