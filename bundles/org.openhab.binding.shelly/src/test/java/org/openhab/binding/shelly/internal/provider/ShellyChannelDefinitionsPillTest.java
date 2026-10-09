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
import static org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.api2.Shelly2PillMapper.getComponentKey;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyPillJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.mkChannelId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellyInputState;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsInput;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsRelay;
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

    private static ShellyDeviceProfile createProfile(List<String> components) {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUSPILL);
        profile.pillComponents = components;
        return profile;
    }

    private static Map<String, Channel> createChannels(ShellyDeviceProfile profile) {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID(THING_TYPE_SHELLYPLUSPILL, "test"));
        return ShellyChannelDefinitions.createPillChannels(thing, profile, profile.status);
    }

    private static Map<String, Channel> createChannels(List<String> components) {
        return createChannels(createProfile(components));
    }

    private static ShellyDeviceProfile createMixedIoProfile(String inputType) {
        ShellyDeviceProfile profile = createProfile(List.of(getComponentKey(SHELLY2_PILL_KEY_INPUT, 1),
                getComponentKey(SHELLY2_PILL_KEY_SWITCH, 0), getComponentKey(SHELLY2_PILL_KEY_SWITCH, 2)));
        ArrayList<ShellySettingsRelay> relays = new ArrayList<>();
        for (int pin : new int[] { 0, 2 }) {
            ShellySettingsRelay relay = new ShellySettingsRelay();
            relay.id = SHELLY2_PILL_ID + pin;
            relays.add(relay);
        }
        ShellySettingsInput input = new ShellySettingsInput();
        input.btnType = inputType;
        profile.settings.relays = relays;
        profile.settings.inputs = new ArrayList<>(List.of(input));
        profile.numRelays = 2;
        profile.numInputs = 1;
        profile.hasRelays = true;
        profile.status.relays = relays;
        profile.status.inputs = new ArrayList<>(List.of(new ShellyInputState(0)));
        return profile;
    }

    @Test
    void mixedIoChannelsUseGroupOfPin() {
        Map<String, Channel> created = createChannels(createMixedIoProfile(SHELLY_BTNT_EDGE));

        assertTrue(created.containsKey(mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 1, CHANNEL_OUTPUT)));
        assertTrue(created.containsKey(mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 2, CHANNEL_INPUT)));
        assertTrue(created.containsKey(mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 3, CHANNEL_OUTPUT)));
        assertFalse(created.containsKey(mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 1, CHANNEL_INPUT)));
        assertFalse(created.containsKey(mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 2, CHANNEL_OUTPUT)));
        assertTrue(created.keySet().stream().noneMatch(id -> id.startsWith(CHANNEL_GROUP_RELAY_CONTROL + "#")));
        assertTrue(created.keySet().stream().noneMatch(id -> id.startsWith(CHANNEL_GROUP_SENSOR)));
    }

    @Test
    void buttonChannelsOnlyForButtonInputs() {
        String trigger = mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 2, CHANNEL_BUTTON_TRIGGER);
        String eventType = mkChannelId(CHANNEL_GROUP_RELAY_CONTROL + 2, CHANNEL_STATUS_EVENTTYPE);
        Map<String, Channel> switchInput = createChannels(createMixedIoProfile(SHELLY_BTNT_EDGE));
        Map<String, Channel> buttonInput = createChannels(createMixedIoProfile(SHELLY_BTNT_MOMENTARY));

        assertFalse(switchInput.containsKey(trigger));
        assertFalse(switchInput.containsKey(eventType));
        assertTrue(buttonInput.containsKey(trigger));
        assertTrue(buttonInput.containsKey(eventType));
    }

    @Test
    void relayIndexOfPinFollowsConfiguredSwitches() {
        ShellyDeviceProfile profile = createMixedIoProfile(SHELLY_BTNT_EDGE);

        assertEquals(0, profile.getPillRelayIdx(0));
        assertEquals(-1, profile.getPillRelayIdx(1));
        assertEquals(1, profile.getPillRelayIdx(2));
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
}
