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
public class XSenseShadowRequestsTest {

    @Test
    public void appModeDesiredStateMatchesAppPayloadVerbatim() {
        String body = XSenseShadowRequests.appModeDesiredState(XSenseSafeMode.AWAY, "12345678", "user-abc");
        assertEquals("{\"state\":{\"desired\":{\"shadow\":\"appMode\",\"safeMode\":\"Away\","
                + "\"stationSN\":\"12345678\",\"source\":\"1\",\"forceArm\":\"0\",\"userId\":\"user-abc\","
                + "\"userParam\":\"source=1\"}}}", body);
    }

    @Test
    public void userParamEqualsSignIsNotHtmlEscaped() {
        String body = XSenseShadowRequests.appModeDesiredState(XSenseSafeMode.DISARMED, "1", "u");
        assertTrue(body.contains("\"userParam\":\"source=1\""));
        assertFalse(body.contains("\\u003d"));
    }

    @Test
    public void stationThingNameConcatenatesCategoryAndSerial() {
        assertEquals("SBS5012345678", XSenseShadowRequests.stationThingName("SBS50", "12345678"));
    }

    @Test
    public void appModeShadowNameIsStable() {
        assertEquals("2nd_appmode", XSenseShadowRequests.SHADOW_NAME_APP_MODE);
    }

    @Test
    public void muteTopicAndDesiredStateForSmokeDetectorHaveNoExtraFields() {
        assertEquals("2nd_appmute", XSenseShadowRequests.muteTopic("XS01-M"));
        String body = XSenseShadowRequests.muteDesiredState("XS01-M", "12345678", "87654321", "user-abc");
        assertEquals("{\"state\":{\"desired\":{\"deviceSN\":\"87654321\",\"shadow\":\"appMute\","
                + "\"stationSN\":\"12345678\",\"userId\":\"user-abc\"}}}", body);
    }

    @Test
    public void muteDesiredStateForCoDetectorIncludesMuteType() {
        String body = XSenseShadowRequests.muteDesiredState("XC01-M", "12345678", "87654321", "user-abc");
        assertEquals("{\"state\":{\"desired\":{\"deviceSN\":\"87654321\",\"shadow\":\"appCoMute\","
                + "\"stationSN\":\"12345678\",\"userId\":\"user-abc\",\"muteType\":\"1\"}}}", body);
    }

    @Test
    public void muteDesiredStateForXp0amrIncludesUserParam() {
        String body = XSenseShadowRequests.muteDesiredState("XP0A-MR", "12345678", "87654321", "user-abc");
        assertEquals("{\"state\":{\"desired\":{\"deviceSN\":\"87654321\",\"shadow\":\"appXp0amrMute\","
                + "\"stationSN\":\"12345678\",\"userId\":\"user-abc\",\"muteType\":\"1\",\"userParam\":\"source=1\"}}}",
                body);
    }

    @Test
    public void muteDesiredStateForWaterSensorIncludesSilenceTimeAndSetType() {
        String body = XSenseShadowRequests.muteDesiredState("SWS51", "12345678", "87654321", "user-abc");
        assertEquals("{\"state\":{\"desired\":{\"deviceSN\":\"87654321\",\"shadow\":\"appWater\","
                + "\"stationSN\":\"12345678\",\"userId\":\"user-abc\",\"silenceTime\":\"\",\"setType\":\"0\"}}}", body);
    }

    @Test
    public void muteDesiredStateForDrivewayAlarmUsesRawMuteField() {
        String body = XSenseShadowRequests.muteDesiredState("SDA51", "12345678", "87654321", "user-abc");
        assertEquals("{\"state\":{\"desired\":{\"deviceSN\":\"87654321\",\"shadow\":\"appDriveway\","
                + "\"stationSN\":\"12345678\",\"userId\":\"user-abc\",\"mute\":\"1\"}}}", body);
    }

    @Test
    public void muteTopicAndDesiredStateAreNullForUnsupportedModel() {
        assertNull(XSenseShadowRequests.muteTopic("SDS0A"));
        assertNull(XSenseShadowRequests.muteDesiredState("SDS0A", "12345678", "87654321", "user-abc"));
    }
}
