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

import static org.junit.jupiter.api.Assertions.*;
import static org.openhab.binding.xsense.internal.XSenseBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
public class XSenseBindingConstantsTest {

    @Test
    public void knownDeviceTypesMapToThingTypes() {
        assertEquals(THING_TYPE_SMOKE, thingTypeForDeviceType("XS01-M"));
        assertEquals(THING_TYPE_SMOKE, thingTypeForDeviceType("XS0B-MR"));
        assertEquals(THING_TYPE_CO, thingTypeForDeviceType("XC01-M"));
        assertEquals(THING_TYPE_SMOKECO, thingTypeForDeviceType("SC07-MR"));
        assertEquals(THING_TYPE_SMOKECO, thingTypeForDeviceType("XP0A-MR"));
        assertEquals(THING_TYPE_HEAT, thingTypeForDeviceType("XH02-M"));
        assertEquals(THING_TYPE_WATER, thingTypeForDeviceType("SWS51"));
        assertEquals(THING_TYPE_THERMOHYGRO, thingTypeForDeviceType("STH51"));
    }

    @Test
    public void securityAccessoryDeviceTypesMapToThingTypes() {
        assertEquals(THING_TYPE_LISTENER, thingTypeForDeviceType("SAL51"));
        assertEquals(THING_TYPE_LISTENER, thingTypeForDeviceType("SAL100"));
        assertEquals(THING_TYPE_DRIVEWAY, thingTypeForDeviceType("SDA51"));
        assertEquals(THING_TYPE_MAILBOX, thingTypeForDeviceType("SMA51"));
        assertEquals(THING_TYPE_MAILBOX, thingTypeForDeviceType("SMA11"));
        assertEquals(THING_TYPE_DOOR, thingTypeForDeviceType("SDS0A"));
        assertEquals(THING_TYPE_DOOR, thingTypeForDeviceType("SES01"));
        assertEquals(THING_TYPE_MOTION, thingTypeForDeviceType("SMS0A"));
        assertEquals(THING_TYPE_STROBE, thingTypeForDeviceType("SSL51"));
        assertEquals(THING_TYPE_KEYPAD, thingTypeForDeviceType("SKP0A"));
    }

    @Test
    public void securityAccessoryThingTypesAreSensorThingTypes() {
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_LISTENER));
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_DRIVEWAY));
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_MAILBOX));
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_DOOR));
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_MOTION));
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_STROBE));
        assertTrue(SENSOR_THING_TYPES.contains(THING_TYPE_KEYPAD));
        assertEquals(13, SENSOR_THING_TYPES.size());
    }

    @Test
    public void unsupportedDeviceTypeReturnsNull() {
        assertNull(thingTypeForDeviceType(STATION_TYPE_SBS50));
        assertNull(thingTypeForDeviceType("XS01"));
        assertNull(thingTypeForDeviceType(""));
    }

    @Test
    public void supportedThingTypesContainAllSensorAndBridgeTypes() {
        assertTrue(SUPPORTED_THING_TYPES.containsAll(SENSOR_THING_TYPES));
        assertTrue(SUPPORTED_THING_TYPES.contains(THING_TYPE_ACCOUNT));
        assertTrue(SUPPORTED_THING_TYPES.contains(THING_TYPE_HOME));
        assertTrue(SUPPORTED_THING_TYPES.contains(THING_TYPE_STATION));
        assertEquals(SENSOR_THING_TYPES.size() + 3, SUPPORTED_THING_TYPES.size());
    }
}
