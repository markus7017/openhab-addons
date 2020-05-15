/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.carnet.internal;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link CarNetBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetBindingConstants {

    public static final String BINDING_ID = "carnet";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_VEHICLE = new ThingTypeUID(BINDING_ID, "vehicle");
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(THING_TYPE_ACCOUNT, THING_TYPE_VEHICLE).collect(Collectors.toSet()));

    // List of all Channel ids
    public static final String CHANNEL_GROUP_GENERAL = "general";
    public static final String CHANNEL_GENERAL_VIN = "vin";

    // List of all ChannelGroups
    public static final String CHANNEL_GROUP_STATUS = "status";
    public static final String CHANNEL_GROUP_WINDOWS = "windows";
    public static final String CHANNEL_GROUP_DOORS = "doors";
    public static final String CHANNEL_GROUP_TYRES = "tyres";

    public static final String PROPERTY_VIN = "vin";
    public static final String PROPERTY_BRAND = "brand";
    public static final String PROPERTY_MODEL = "model";
    public static final String PROPERTY_COLOR = "color";
    public static final String PROPERTY_MMI = "mmi";
    public static final String PROPERTY_ENGINE = "engine";
    public static final String PROPERTY_TRANS = "transmission";
}
