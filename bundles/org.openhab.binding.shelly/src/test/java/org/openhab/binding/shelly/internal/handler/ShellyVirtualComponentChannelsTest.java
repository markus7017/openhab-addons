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
package org.openhab.binding.shelly.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.provider.ShellyTranslationProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.UnDefType;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponentChannelsTest {

    private static final ThingUID THING_UID = new ThingUID("shelly", "shellyplus1", "test");

    @BeforeAll
    static void initChannelDefinitions() {
        ShellyTranslationProvider messages = mock(ShellyTranslationProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenReturn("Virtual");
        new ShellyChannelDefinitions(messages);
    }

    private static Thing thing(Channel... existingChannels) {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(THING_UID);
        when(thing.getThingTypeUID()).thenReturn(THING_TYPE_SHELLYPLUS1);
        when(thing.getChannels()).thenReturn(List.of(existingChannels));
        when(thing.getChannelsOfGroup(CHANNEL_GROUP_VCOMPONENTS)).thenReturn(Stream.of(existingChannels)
                .filter(c -> CHANNEL_GROUP_VCOMPONENTS.equals(c.getUID().getGroupId())).toList());
        return thing;
    }

    private static Channel channel(String channelId, String label) {
        return ChannelBuilder.create(new ChannelUID(THING_UID, CHANNEL_GROUP_VCOMPONENTS + "#" + channelId))
                .withLabel(label).build();
    }

    private static ShellyDeviceProfile profile(ShellyVCComponent... components) {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.vComponentsProbed = true;
        profile.vComponents = List.of(components);
        return profile;
    }

    private static ShellyVCComponent vcomp(String type, int id, String config, String value) {
        ShellyVCComponent vc = Objects.requireNonNull(new Gson().fromJson(config, ShellyVCComponent.class));
        vc.type = type;
        vc.id = id;
        vc.value = value.isEmpty() ? null : JsonParser.parseString(value);
        return vc;
    }

    private static ShellyVCComponent vcomp(String type, int id) {
        return vcomp(type, id, "{}", "");
    }

    private static ShellyThingInterface handler(ShellyDeviceProfile profile, Thing thing) {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        when(handler.getThingName()).thenReturn("test-vcomp");
        when(handler.getProfile()).thenReturn(profile);
        when(handler.getThing()).thenReturn(thing);
        when(handler.areChannelsCreated()).thenReturn(true);
        return handler;
    }

    @Test
    void createVirtualComponentChannelsLabelsChannelsWithGroupAndComponentNames() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                profile(vcomp(CHANNEL_VCOMP_NUMBER, 200, "{\"name\":\"Set point\"}", ""),
                        vcomp(CHANNEL_VCOMP_BOOLEAN, 201),
                        vcomp(SHELLY2_VCOMP_GROUP, 202, "{\"name\":\"Living Room\"}", "[\"number:200\"]"),
                        vcomp(SHELLY2_VCOMP_GROUP, 203, "{\"name\":\"Heating\"}", "[\"number:200\"]"),
                        vcomp(SHELLY2_VCOMP_GROUP, 204, "{}", "[\"number:200\"]")));

        assertEquals(Set.of("vcomponents#number200", "vcomponents#boolean201"), channels.keySet());
        assertEquals("Living Room, Heating: Set point",
                Objects.requireNonNull(channels.get("vcomponents#number200")).getLabel());
        assertEquals("Virtual 201", Objects.requireNonNull(channels.get("vcomponents#boolean201")).getLabel());
    }

    @Test
    void updateDeviceStatusRelabelsRemovesObsoleteChannelsAndPublishesValues() {
        Thing thing = thing(channel("boolean200", "Gate"), channel("text202", "Message"), channel("number299", "x"));
        ShellyThingInterface handler = handler(profile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200, "{\"name\":\"Door\"}", "true"),
                vcomp(CHANNEL_VCOMP_NUMBER, 201, "{}", "42.5"),
                vcomp(CHANNEL_VCOMP_TEXT, 202, "{\"name\":\"Message\"}", "null"),
                vcomp(CHANNEL_VCOMP_ENUM, 203, "{}", "\"high\""), vcomp(CHANNEL_VCOMP_BOOLEAN, 204)), thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateThingChannels(
                argThat(relabeled -> relabeled.keySet().equals(Set.of("vcomponents#boolean200"))),
                argThat(channels -> channels.containsKey("vcomponents#enum203")));
        verify(handler).removeChannels(Set.of("vcomponents#number299"));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "boolean200", OnOffType.ON);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "number201", new DecimalType(42.5));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "text202", UnDefType.UNDEF);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "enum203", new StringType("high"));
        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_VCOMPONENTS), eq("boolean204"), any());
    }

    @ParameterizedTest
    @CsvSource({ "false, true, 1.6.1, false", "true, false, 1.6.1, false", "true, true, 1.6.0, false",
            "true, true, 1.6.1, true", "true, true, 2.0.0, true" })
    void virtualComponentsNeedAnAlwaysOnRpcDeviceWithFirmware161(boolean gen2, boolean alwaysOn, String fwVersion,
            boolean expected) {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.isGen2 = gen2;
        profile.alwaysOn = alwaysOn;
        profile.fwVersion = fwVersion;

        assertEquals(expected, ShellyVirtualComponents.isSupported(profile));
    }
}
