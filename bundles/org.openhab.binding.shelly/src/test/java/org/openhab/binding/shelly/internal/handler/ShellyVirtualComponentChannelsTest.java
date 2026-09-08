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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLY2_VCOMP_BUTTON;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLY2_VCOMP_GROUP;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVirtualComponent;
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

import com.google.gson.JsonPrimitive;

/**
 * Tests for the Virtual Components (Gen3/Gen4/Gen2 Pro) channel lifecycle in {@link ShellyChannelDefinitions} and
 * {@link ShellyComponents}: channel creation, reconciliation and status updates for Boolean/Number/Text/Enum.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponentChannelsTest {

    private static final ThingUID THING_UID = new ThingUID("shelly", "shellyplus1", "test");

    @BeforeAll
    static void initChannelDefinitions() {
        ShellyTranslationProvider messages = mock(ShellyTranslationProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenReturn("mocked");
        new ShellyChannelDefinitions(messages);
    }

    private static Thing thing(Channel... existingChannels) {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(THING_UID);
        when(thing.getChannels()).thenReturn(List.of(existingChannels));
        return thing;
    }

    private static Channel channel(String channelId) {
        return ChannelBuilder.create(new ChannelUID(THING_UID, channelId)).build();
    }

    private static ShellyDeviceProfile vComponentsProfile(ShellyVirtualComponent... components) {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.vComponentsProbed = true;
        profile.vComponents = List.of(components);
        return profile;
    }

    private static ShellyVirtualComponent vcomp(String type, int id) {
        ShellyVirtualComponent vc = new ShellyVirtualComponent();
        vc.type = type;
        vc.id = id;
        return vc;
    }

    private static ShellyVirtualComponent vcomp(String type, int id, Object value) {
        ShellyVirtualComponent vc = vcomp(type, id);
        vc.value = value instanceof Boolean b ? new JsonPrimitive(b)
                : value instanceof String s ? new JsonPrimitive(s) : new JsonPrimitive((Double) value);
        return vc;
    }

    private static ShellyVirtualComponent vgroup(int id, String... memberKeys) {
        ShellyVirtualComponent vc = vcomp(SHELLY2_VCOMP_GROUP, id);
        vc.groupMembers = List.of(memberKeys);
        return vc;
    }

    /**
     * Builds a {@link ShellyThingInterface} mock wired to the given profile and thing, as used by every
     * {@code ShellyComponents.updateDeviceStatus} integration test below.
     */
    private static ShellyThingInterface mockHandler(ShellyDeviceProfile profile, Thing thing) {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        when(handler.getProfile()).thenReturn(profile);
        when(handler.getThing()).thenReturn(thing);
        when(handler.areChannelsCreated()).thenReturn(true);
        return handler;
    }

    /**
     * Builds a {@link ShellyThingInterface} mock wired to the given profile and a mocked API, as used by every
     * {@code ShellyComponents.handleVirtualComponentCommand} test below.
     */
    private static ShellyThingInterface commandHandler(ShellyDeviceProfile profile) {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        when(handler.getThingName()).thenReturn("test-vcomp");
        when(handler.getProfile()).thenReturn(profile);
        when(handler.getApi()).thenReturn(mock(ShellyApiInterface.class));
        return handler;
    }

    @Test
    void createVirtualComponentChannelsCreatesOnlyBooleanNumberTextEnumChannels() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200), vcomp(CHANNEL_VCOMP_NUMBER, 201),
                        vcomp(CHANNEL_VCOMP_TEXT, 202), vcomp(CHANNEL_VCOMP_ENUM, 203), vcomp(SHELLY2_VCOMP_GROUP, 204),
                        vcomp(SHELLY2_VCOMP_BUTTON, 205)));

        assertThat(channels.keySet(),
                is(Set.of(CHANNEL_GROUP_VCOMPONENTS + "#boolean200", CHANNEL_GROUP_VCOMPONENTS + "#number201",
                        CHANNEL_GROUP_VCOMPONENTS + "#text202", CHANNEL_GROUP_VCOMPONENTS + "#enum203")));
    }

    @Test
    void createVirtualComponentChannelsUsesConfiguredNameAsLabel() {
        ShellyVirtualComponent named = vcomp(CHANNEL_VCOMP_BOOLEAN, 200);
        named.name = "Garage Door Sensor";

        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(named));

        assertThat(channels.get(CHANNEL_GROUP_VCOMPONENTS + "#boolean200").getLabel(), is("Garage Door Sensor"));
    }

    @Test
    void createVirtualComponentChannelsEmptyWhenNoneDiscovered() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1));

        assertThat(channels.isEmpty(), is(true));
    }

    @Test
    void getObsoleteVirtualComponentChannelIdsRemovesOnlyChannelsNoLongerPresent() {
        // number201 is gone from the device; the unrelated lora channel must never be touched
        Thing thing = thing(channel(CHANNEL_GROUP_VCOMPONENTS + "#boolean200"),
                channel(CHANNEL_GROUP_VCOMPONENTS + "#number201"),
                channel(CHANNEL_GROUP_LORA + "#" + CHANNEL_LORA_TXDATA));

        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thing,
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200)));

        assertThat(obsolete, is(Set.of(CHANNEL_GROUP_VCOMPONENTS + "#number201")));
    }

    @Test
    void updateVirtualComponentStatusPushesValuesAndSkipsUnreportedOnes() {
        Thing thing = thing();
        ShellyDeviceProfile profile = vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200, true),
                vcomp(CHANNEL_VCOMP_NUMBER, 201, 42.5), vcomp(CHANNEL_VCOMP_TEXT, 202, "hello"),
                vcomp(CHANNEL_VCOMP_ENUM, 203, "high"), vcomp(CHANNEL_VCOMP_BOOLEAN, 299)); // 299: no value yet
        ShellyThingInterface handler = mockHandler(profile, thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "boolean200", OnOffType.ON);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "number201", new DecimalType(42.5));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "text202", new StringType("hello"));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "enum203", new StringType("high"));
        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_VCOMPONENTS), eq("boolean299"), any());
    }

    @Test
    void updateDeviceStatusCreatesVirtualComponentChannelsWhenProbed() {
        Thing thing = thing();
        ShellyThingInterface handler = mockHandler(vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200)), thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateThingChannels(eq(Map.of()),
                argThat(channels -> channels.containsKey(CHANNEL_GROUP_VCOMPONENTS + "#boolean200")));
    }

    @Test
    void updateDeviceStatusSkipsVirtualComponentsWhenNotYetProbed() {
        Thing thing = thing();
        ShellyThingInterface handler = mockHandler(new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1), thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_VCOMPONENTS), anyString(), any());
    }

    @Test
    void handleVirtualComponentCommandDispatchesToMatchingRpcSetCall() throws ShellyApiException {
        ShellyThingInterface handler = commandHandler(vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200),
                vcomp(CHANNEL_VCOMP_NUMBER, 201), vcomp(CHANNEL_VCOMP_TEXT, 202), vcomp(CHANNEL_VCOMP_ENUM, 203)));

        ShellyComponents.handleVirtualComponentCommand(handler, "boolean200", OnOffType.ON);
        ShellyComponents.handleVirtualComponentCommand(handler, "number201", new DecimalType(12.5));
        ShellyComponents.handleVirtualComponentCommand(handler, "text202", new StringType("hi"));
        ShellyComponents.handleVirtualComponentCommand(handler, "enum203", new StringType("high"));

        verify(handler.getApi()).setVirtualBoolean(200, true);
        verify(handler.getApi()).setVirtualNumber(201, 12.5);
        verify(handler.getApi()).setVirtualText(202, "hi");
        verify(handler.getApi()).setVirtualEnum(203, "high");
    }

    @Test
    void handleVirtualComponentCommandIgnoresUnknownChannel() throws ShellyApiException {
        ShellyThingInterface handler = commandHandler(vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200)));

        ShellyComponents.handleVirtualComponentCommand(handler, "boolean299", OnOffType.ON);

        verify(handler.getApi(), never()).setVirtualBoolean(anyInt(), anyBoolean());
    }

    @Test
    void createVirtualComponentChannelsPutsGroupMemberUnderVgroupPrefixAndSkipsTheGroupItself() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200), vcomp(CHANNEL_VCOMP_NUMBER, 201),
                        vgroup(204, "boolean:200")));

        assertThat(channels.keySet(),
                is(Set.of(CHANNEL_GROUP_VGROUP_PREFIX + "204#boolean200", CHANNEL_GROUP_VCOMPONENTS + "#number201")));
    }

    @Test
    void getObsoleteVirtualComponentChannelIdsRemovesChannelMovedToADifferentGroup() {
        // boolean200 used to live directly under vcomponents; the device now reports it as a member of group 204
        Thing thing = thing(channel(CHANNEL_GROUP_VCOMPONENTS + "#boolean200"));

        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thing,
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200), vgroup(204, "boolean:200")));

        assertThat(obsolete, is(Set.of(CHANNEL_GROUP_VCOMPONENTS + "#boolean200")));
    }

    @Test
    void getObsoleteVirtualComponentChannelIdsKeepsChannelStillInItsGroup() {
        Thing thing = thing(channel(CHANNEL_GROUP_VGROUP_PREFIX + "204#boolean200"));

        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thing,
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200), vgroup(204, "boolean:200")));

        assertThat(obsolete.isEmpty(), is(true));
    }

    @Test
    void updateVirtualComponentStatusRoutesGroupMemberToVgroupChannel() {
        Thing thing = thing();
        ShellyDeviceProfile profile = vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200, true),
                vgroup(204, "boolean:200"));
        ShellyThingInterface handler = mockHandler(profile, thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateChannel(CHANNEL_GROUP_VGROUP_PREFIX + "204", "boolean200", OnOffType.ON);
    }
}
