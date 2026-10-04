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
package org.openhab.binding.shelly.internal.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.*;
import static org.openhab.binding.shelly.internal.api2.Shelly2PillMapper.getComponentKey;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyPillJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.mkChannelId;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyChannelDefinitionsPillTest {

    @BeforeAll
    static void initChannelDefinitions() {
        ShellyTranslationProvider messages = mock(ShellyTranslationProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        new ShellyChannelDefinitions(messages);
    }

    private static Map<String, Channel> createChannels(List<String> components) {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID(THING_TYPE_SHELLYPLUSPILL, "test"));
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUSPILL);
        profile.pillComponents = components;
        return ShellyChannelDefinitions.createPillChannels(thing, profile, profile.status);
    }

    @Test
    void sensorChannelsFollowConfigurationWithoutStatus() {
        Map<String, Channel> created = createChannels(List.of(getComponentKey(SHELLY2_PILL_KEY_HUMIDITY, 0),
                getComponentKey(SHELLY2_PILL_KEY_TEMPERATURE, 0)));

        assertTrue(created.containsKey(mkChannelId(CHANNEL_GROUP_SENSOR, CHANNEL_ESENSOR_TEMP1)));
        assertTrue(created.containsKey(mkChannelId(CHANNEL_GROUP_SENSOR, CHANNEL_ESENSOR_HUMIDITY)));
        assertTrue(created.containsKey(mkChannelId(CHANNEL_GROUP_SENSOR, CHANNEL_LAST_UPDATE)));
        assertFalse(created.containsKey(mkChannelId(CHANNEL_GROUP_SENSOR, CHANNEL_ESENSOR_TEMP2)));
        assertFalse(created.containsKey(mkChannelId(CHANNEL_GROUP_SENSOR, CHANNEL_ESENSOR_VOLTAGE)));
    }

    @Test
    void noSensorComponentsCreateNoSensorChannels() {
        Map<String, Channel> created = createChannels(List.of(getComponentKey(SHELLY2_PILL_KEY_SWITCH, 0)));

        assertTrue(created.keySet().stream().noneMatch(id -> id.startsWith(CHANNEL_GROUP_SENSOR)));
    }
}
