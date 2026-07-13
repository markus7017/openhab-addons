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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
public class XSenseSafeModeTest {

    @Test
    public void valuesUseCapitalizedWireFormat() {
        assertEquals("Disarmed", XSenseSafeMode.DISARMED.getValue());
        assertEquals("Home", XSenseSafeMode.HOME.getValue());
        assertEquals("Away", XSenseSafeMode.AWAY.getValue());
    }

    @Test
    public void fromCloudToleratesCasingAndSynonyms() {
        assertEquals(XSenseSafeMode.DISARMED, XSenseSafeMode.fromCloud("Disarmed"));
        assertEquals(XSenseSafeMode.DISARMED, XSenseSafeMode.fromCloud("disarm"));
        assertEquals(XSenseSafeMode.DISARMED, XSenseSafeMode.fromCloud("off"));
        assertEquals(XSenseSafeMode.DISARMED, XSenseSafeMode.fromCloud("0"));
        assertEquals(XSenseSafeMode.HOME, XSenseSafeMode.fromCloud("HOME"));
        assertEquals(XSenseSafeMode.HOME, XSenseSafeMode.fromCloud(" home "));
        assertEquals(XSenseSafeMode.AWAY, XSenseSafeMode.fromCloud("away"));
    }

    @Test
    public void fromCloudReturnsNullForUnknownOrMissingValues() {
        assertNull(XSenseSafeMode.fromCloud(null));
        assertNull(XSenseSafeMode.fromCloud(""));
        assertNull(XSenseSafeMode.fromCloud("vacation"));
    }

    @Test
    public void fromCommandMatchesChannelValuesCaseInsensitively() {
        for (XSenseSafeMode mode : XSenseSafeMode.values()) {
            assertEquals(mode, XSenseSafeMode.fromCommand(mode.getValue()));
            assertEquals(mode, XSenseSafeMode.fromCommand(mode.getValue().toUpperCase()));
        }
        assertEquals(XSenseSafeMode.HOME, XSenseSafeMode.fromCommand(" home "));
    }

    @Test
    public void fromCommandReturnsNullForUnsupportedCommands() {
        assertNull(XSenseSafeMode.fromCommand("ON"));
        assertNull(XSenseSafeMode.fromCommand("armed"));
        assertNull(XSenseSafeMode.fromCommand(""));
    }

    @Test
    public void cloudAndChannelValuesRoundTrip() {
        for (XSenseSafeMode mode : XSenseSafeMode.values()) {
            assertEquals(mode, XSenseSafeMode.fromCloud(mode.getValue()));
        }
    }
}
