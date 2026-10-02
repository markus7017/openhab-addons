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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
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
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        when(thing.getThingTypeUID()).thenReturn(THING_TYPE_SHELLYPLUS1);
        when(thing.getChannels()).thenReturn(List.of(existingChannels));
        when(thing.getChannel(anyString())).thenAnswer(invocation -> Stream.of(existingChannels)
                .filter(channel -> channel.getUID().getId().equals(invocation.getArgument(0))).findFirst()
                .orElse(null));
        return thing;
    }

    private static Channel channel(String channelId) {
        return ChannelBuilder.create(new ChannelUID(THING_UID, channelId)).build();
    }

    private static Channel channel(String channelId, String label) {
        return ChannelBuilder.create(new ChannelUID(THING_UID, channelId)).withLabel(label).build();
    }

    private static ShellyDeviceProfile vComponentsProfile(ShellyVCComponent... components) {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.vComponentsProbed = true;
        profile.vComponents = List.of(components);
        return profile;
    }

    private static ShellyVCComponent vcomp(String type, int id) {
        ShellyVCComponent vc = new ShellyVCComponent();
        vc.type = type;
        vc.id = id;
        return vc;
    }

    private static ShellyVCComponent vcomp(String type, int id, Object value) {
        ShellyVCComponent vc = vcomp(type, id);
        vc.value = value instanceof Boolean b ? new JsonPrimitive(b)
                : value instanceof String s ? new JsonPrimitive(s) : new JsonPrimitive((Double) value);
        return vc;
    }

    private static ShellyVCComponent vcompWithJsonNullValue(String type, int id) {
        ShellyVCComponent vc = vcomp(type, id);
        vc.value = JsonNull.INSTANCE;
        return vc;
    }

    private static ShellyVCComponent vcompNamed(String type, int id, String name) {
        ShellyVCComponent vc = vcomp(type, id);
        vc.name = name;
        return vc;
    }

    private static ShellyVCComponent vgroup(int id, String... memberKeys) {
        ShellyVCComponent vc = vcomp(SHELLY2_VCOMP_GROUP, id);
        vc.groupMembers = List.of(memberKeys);
        return vc;
    }

    private static Map<String, JsonObject> pushed(String key, String statusJson) {
        return Map.of(key, JsonParser.parseString(statusJson).getAsJsonObject());
    }

    private static ShellyVCComponent vgroupWithValue(int id, String membersJson) {
        ShellyVCComponent vc = vgroup(id);
        vc.value = JsonParser.parseString(membersJson);
        return vc;
    }

    private static ShellyThingInterface mockHandler(ShellyDeviceProfile profile, Thing thing) {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        when(handler.getProfile()).thenReturn(profile);
        when(handler.getThing()).thenReturn(thing);
        when(handler.areChannelsCreated()).thenReturn(true);
        return handler;
    }

    private static ShellyThingInterface commandHandler(ShellyDeviceProfile profile) {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        when(handler.getThingName()).thenReturn("test-vcomp");
        when(handler.getProfile()).thenReturn(profile);
        when(handler.getApi()).thenReturn(mock(ShellyApiInterface.class));
        return handler;
    }

    @Test
    void createVirtualComponentChannelsCreatesBooleanNumberTextEnumButtonButNotGroupChannels() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200), vcomp(CHANNEL_VCOMP_NUMBER, 201),
                        vcomp(CHANNEL_VCOMP_TEXT, 202), vcomp(CHANNEL_VCOMP_ENUM, 203), vcomp(SHELLY2_VCOMP_GROUP, 204),
                        vcomp(SHELLY2_VCOMP_BUTTON, 205)));

        assertThat(channels.keySet(),
                is(Set.of(CHANNEL_GROUP_VCOMPONENTS + "#boolean200", CHANNEL_GROUP_VCOMPONENTS + "#number201",
                        CHANNEL_GROUP_VCOMPONENTS + "#text202", CHANNEL_GROUP_VCOMPONENTS + "#enum203",
                        CHANNEL_GROUP_VCOMPONENTS + "#button205")));
    }

    @Test
    void createVirtualComponentChannelsCreatesButtonAsTriggerChannel() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(vcomp(SHELLY2_VCOMP_BUTTON, 205)));

        Channel button = Objects.requireNonNull(channels.get(CHANNEL_GROUP_VCOMPONENTS + "#button205"));
        assertThat(button.getKind(), is(ChannelKind.TRIGGER));
    }

    @Test
    void createVirtualComponentChannelsUsesConfiguredNameAsLabel() {
        ShellyVCComponent named = vcomp(CHANNEL_VCOMP_BOOLEAN, 200);
        named.name = "Garage Door Sensor";

        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(named));

        assertThat(Objects.requireNonNull(channels.get(CHANNEL_GROUP_VCOMPONENTS + "#boolean200")).getLabel(),
                is("Garage Door Sensor"));
    }

    @Test
    void createVirtualComponentChannelsEmptyWhenNoneDiscovered() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1));

        assertThat(channels.isEmpty(), is(true));
    }

    @Test
    void getObsoleteVirtualComponentChannelIdsRemovesOnlyChannelsNoLongerPresent() {
        String stillReportedByDevice = CHANNEL_GROUP_VCOMPONENTS + "#boolean200";
        String goneFromDevice = CHANNEL_GROUP_VCOMPONENTS + "#number201";
        String unrelatedToVirtualComponents = CHANNEL_GROUP_LORA + "#" + CHANNEL_LORA_TXDATA;
        Thing thing = thing(channel(stillReportedByDevice), channel(goneFromDevice),
                channel(unrelatedToVirtualComponents));

        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thing,
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200)));

        assertThat(obsolete, is(Set.of(goneFromDevice)));
    }

    @Test
    void updateVirtualComponentStatusPushesValuesAndSkipsUnreportedOnes() {
        Thing thing = thing();
        ShellyVCComponent booleanWithoutAValueYet = vcomp(CHANNEL_VCOMP_BOOLEAN, 299);
        ShellyDeviceProfile profile = vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200, true),
                vcomp(CHANNEL_VCOMP_NUMBER, 201, 42.5), vcomp(CHANNEL_VCOMP_TEXT, 202, "hello"),
                vcomp(CHANNEL_VCOMP_ENUM, 203, "high"), booleanWithoutAValueYet);
        ShellyThingInterface handler = mockHandler(profile, thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "boolean200", OnOffType.ON);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "number201", new DecimalType(42.5));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "text202", new StringType("hello"));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "enum203", new StringType("high"));
        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_VCOMPONENTS), eq("boolean299"), any());
    }

    @Test
    void updateVirtualComponentStatusPublishesUndefWhenDeviceReportsJsonNullValue() {
        ShellyDeviceProfile profile = vComponentsProfile(vcompWithJsonNullValue(CHANNEL_VCOMP_BOOLEAN, 200),
                vcompWithJsonNullValue(CHANNEL_VCOMP_NUMBER, 201), vcompWithJsonNullValue(CHANNEL_VCOMP_TEXT, 202),
                vcompWithJsonNullValue(CHANNEL_VCOMP_ENUM, 203));
        ShellyThingInterface handler = mockHandler(profile, thing());

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "boolean200", UnDefType.UNDEF);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "number201", UnDefType.UNDEF);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "text202", UnDefType.UNDEF);
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "enum203", UnDefType.UNDEF);
    }

    @Test
    void getRelabeledVirtualComponentChannelsReturnsOnlyTheOnesRenamedOnTheDevice() {
        String renamedOnTheDevice = CHANNEL_GROUP_VCOMPONENTS + "#boolean200";
        String stillCarryingItsOriginalName = CHANNEL_GROUP_VCOMPONENTS + "#text202";
        Thing thing = thing(channel(renamedOnTheDevice, "Gate"), channel(stillCarryingItsOriginalName, "Message"));
        ShellyDeviceProfile profile = vComponentsProfile(vcompNamed(CHANNEL_VCOMP_BOOLEAN, 200, "Garage Door"),
                vcompNamed(CHANNEL_VCOMP_TEXT, 202, "Message"),
                vcompNamed(CHANNEL_VCOMP_ENUM, 203, "Not Yet On Thing"));
        Map<String, Channel> desired = ShellyChannelDefinitions.createVirtualComponentChannels(thing, profile);

        Map<String, Channel> relabeled = ShellyChannelDefinitions.getRelabeledVirtualComponentChannels(thing, desired);

        assertThat(relabeled.keySet(), is(Set.of(renamedOnTheDevice)));
        assertThat(Objects.requireNonNull(relabeled.get(renamedOnTheDevice)).getLabel(), is("Garage Door"));
    }

    @Test
    void updateDeviceStatusFeedsRenamedVirtualComponentChannelsIntoTheChannelUpdatePath() {
        String renamedOnTheDevice = CHANNEL_GROUP_VCOMPONENTS + "#boolean200";
        Thing thing = thing(channel(renamedOnTheDevice, "Gate"));
        ShellyThingInterface handler = mockHandler(
                vComponentsProfile(vcompNamed(CHANNEL_VCOMP_BOOLEAN, 200, "Garage Door")), thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateThingChannels(argThat(updates -> updates.keySet().equals(Set.of(renamedOnTheDevice))),
                any());
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
    void checkVGroupThingTypeSwapsOnceWhenNamedGroupAppears() {
        Thing thing = thing();
        when(thing.getThingTypeUID()).thenReturn(THING_TYPE_SHELLYPLUS1);
        ShellyThingInterface handler = mockHandler(vComponentsProfile(vgroup(200, "boolean:201")), thing);

        ShellyVirtualComponents.checkVGroupThingType(handler, handler.getProfile());

        verify(handler).changeThingType(
                new ThingTypeUID(BINDING_ID, THING_TYPE_SHELLYPLUS1.getId() + VGROUP_TYPE_MARKER + THING_UID.getId()));
    }

    @Test
    void updateDeviceStatusNeverSwapsThingTypeMidCycle() {
        Thing thing = thing();
        when(thing.getThingTypeUID()).thenReturn(THING_TYPE_SHELLYPLUS1);
        ShellyThingInterface handler = mockHandler(vComponentsProfile(vgroup(200, "boolean:201")), thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler, never()).changeThingType(any());
    }

    @Test
    void checkVGroupThingTypeDoesNotSwapAgainOnceAlreadySwapped() {
        ThingTypeUID vgType = new ThingTypeUID(BINDING_ID,
                THING_TYPE_SHELLYPLUS1.getId() + VGROUP_TYPE_MARKER + THING_UID.getId());
        Thing thing = thing();
        when(thing.getThingTypeUID()).thenReturn(vgType);
        ShellyThingInterface handler = mockHandler(vComponentsProfile(vgroup(200, "boolean:201")), thing);

        ShellyVirtualComponents.checkVGroupThingType(handler, handler.getProfile());

        verify(handler, never()).changeThingType(any());
    }

    @Test
    void checkVGroupThingTypeDoesNotSwapWithoutANamedGroup() {
        Thing thing = thing();
        when(thing.getThingTypeUID()).thenReturn(THING_TYPE_SHELLYPLUS1);
        ShellyThingInterface handler = mockHandler(vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200)), thing);

        ShellyVirtualComponents.checkVGroupThingType(handler, handler.getProfile());

        verify(handler, never()).changeThingType(any());
    }

    @Test
    void handleVirtualComponentCommandDispatchesToMatchingRpcSetCall() throws ShellyApiException {
        ShellyThingInterface handler = commandHandler(vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200),
                vcomp(CHANNEL_VCOMP_NUMBER, 201), vcomp(CHANNEL_VCOMP_TEXT, 202), vcomp(CHANNEL_VCOMP_ENUM, 203)));

        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "boolean200", OnOffType.ON);
        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "number201", new DecimalType(12.5));
        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "text202", new StringType("hi"));
        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "enum203", new StringType("high"));

        verify(handler.getApi()).setVirtualBoolean(200, true);
        verify(handler.getApi()).setVirtualNumber(201, 12.5);
        verify(handler.getApi()).setVirtualText(202, "hi");
        verify(handler.getApi()).setVirtualEnum(203, "high");
    }

    @Test
    void handleVirtualComponentCommandIgnoresNumberOutsideTheConfiguredRange() throws ShellyApiException {
        ShellyVCComponent ranged = vcomp(CHANNEL_VCOMP_NUMBER, 201);
        ranged.min = 0.0;
        ranged.max = 100.0;
        ShellyThingInterface handler = commandHandler(vComponentsProfile(ranged));

        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "number201", new DecimalType(100.5));
        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "number201", new DecimalType(-0.5));

        verify(handler.getApi(), never()).setVirtualNumber(anyInt(), anyDouble());
    }

    @Test
    void handleVirtualComponentCommandForwardsNumberOnTheRangeBoundaries() throws ShellyApiException {
        ShellyVCComponent ranged = vcomp(CHANNEL_VCOMP_NUMBER, 201);
        ranged.min = 0.0;
        ranged.max = 100.0;
        ShellyThingInterface handler = commandHandler(vComponentsProfile(ranged));

        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "number201", new DecimalType(0));
        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "number201", new DecimalType(100));

        verify(handler.getApi()).setVirtualNumber(201, 0.0);
        verify(handler.getApi()).setVirtualNumber(201, 100.0);
    }

    @Test
    void handleVirtualComponentCommandIgnoresTextLongerThanTheConfiguredMaxLength() throws ShellyApiException {
        ShellyVCComponent limited = vcomp(CHANNEL_VCOMP_TEXT, 202);
        limited.maxLen = 5;
        ShellyThingInterface handler = commandHandler(vComponentsProfile(limited));

        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "text202", new StringType("123456"));
        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "text202", new StringType("12345"));

        verify(handler.getApi(), never()).setVirtualText(202, "123456");
        verify(handler.getApi()).setVirtualText(202, "12345");
    }

    @Test
    void handleVirtualComponentCommandIgnoresUnknownChannel() throws ShellyApiException {
        ShellyThingInterface handler = commandHandler(vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200)));

        ShellyVirtualComponents.handleVirtualComponentCommand(handler, "boolean299", OnOffType.ON);

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
        String locationBeforeTheDeviceMovedItIntoGroup204 = CHANNEL_GROUP_VCOMPONENTS + "#boolean200";
        Thing thing = thing(channel(locationBeforeTheDeviceMovedItIntoGroup204));

        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thing,
                vComponentsProfile(vcomp(CHANNEL_VCOMP_BOOLEAN, 200), vgroup(204, "boolean:200")));

        assertThat(obsolete, is(Set.of(locationBeforeTheDeviceMovedItIntoGroup204)));
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

    @Test
    void createVirtualComponentChannelsDuplicatesAComponentThatIsAMemberOfTwoGroups() {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thing(),
                vComponentsProfile(vcomp(CHANNEL_VCOMP_ENUM, 200), vgroup(200, "enum:200"), vgroup(201, "enum:200")));

        assertThat(channels.keySet(),
                is(Set.of(CHANNEL_GROUP_VGROUP_PREFIX + "200#enum200", CHANNEL_GROUP_VGROUP_PREFIX + "201#enum200")));
    }

    @Test
    void getObsoleteVirtualComponentChannelIdsRemovesBothCopiesWhenTheComponentIsDeleted() {
        Thing thing = thing(channel(CHANNEL_GROUP_VGROUP_PREFIX + "200#enum200"),
                channel(CHANNEL_GROUP_VGROUP_PREFIX + "201#enum200"));

        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thing,
                vComponentsProfile());

        assertThat(obsolete,
                is(Set.of(CHANNEL_GROUP_VGROUP_PREFIX + "200#enum200", CHANNEL_GROUP_VGROUP_PREFIX + "201#enum200")));
    }

    @Test
    void updateVirtualComponentStatusRoutesToBothGroupsWhenAComponentIsAMemberOfTwoGroups() {
        Thing thing = thing();
        ShellyDeviceProfile profile = vComponentsProfile(vcomp(CHANNEL_VCOMP_ENUM, 200, "high"),
                vgroup(200, "enum:200"), vgroup(201, "enum:200"));
        ShellyThingInterface handler = mockHandler(profile, thing);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler).updateChannel(CHANNEL_GROUP_VGROUP_PREFIX + "200", "enum200", new StringType("high"));
        verify(handler).updateChannel(CHANNEL_GROUP_VGROUP_PREFIX + "201", "enum200", new StringType("high"));
    }

    @Test
    void pushedValueOfKnownComponentUpdatesChannelWithoutMarkingProfileDirty() {
        ShellyDeviceProfile profile = vComponentsProfile(vcomp(CHANNEL_VCOMP_NUMBER, 200, 1.0));
        ShellyThingInterface handler = mockHandler(profile, thing());

        boolean updated = ShellyVirtualComponents.updateVirtualComponentValues(handler, profile,
                pushed("number:200", "{\"value\":21.5}"));

        assertThat(updated, is(true));
        assertThat(profile.vComponentsDirty, is(false));
        verify(handler).updateChannel(CHANNEL_GROUP_VCOMPONENTS, "number200", new DecimalType(21.5));
    }

    @Test
    void pushedUnknownComponentMarksProfileDirty() {
        ShellyDeviceProfile profile = vComponentsProfile(vcomp(CHANNEL_VCOMP_NUMBER, 200, 1.0));
        ShellyThingInterface handler = mockHandler(profile, thing());

        boolean updated = ShellyVirtualComponents.updateVirtualComponentValues(handler, profile,
                pushed("boolean:201", "{\"value\":true}"));

        assertThat(updated, is(false));
        assertThat(profile.vComponentsDirty, is(true));
        verify(handler, never()).updateChannel(anyString(), anyString(), any());
    }

    @Test
    void pushedGroupWithChangedMembersMarksProfileDirty() {
        ShellyDeviceProfile profile = vComponentsProfile(vgroupWithValue(200, "[\"number:201\"]"));
        ShellyThingInterface handler = mockHandler(profile, thing());

        ShellyVirtualComponents.updateVirtualComponentValues(handler, profile,
                pushed("group:200", "{\"value\":[\"number:201\",\"boolean:202\"]}"));

        assertThat(profile.vComponentsDirty, is(true));
    }

    @Test
    void pushedGroupWithUnchangedMembersKeepsProfileClean() {
        ShellyDeviceProfile profile = vComponentsProfile(vgroupWithValue(200, "[\"number:201\"]"));
        ShellyThingInterface handler = mockHandler(profile, thing());

        boolean updated = ShellyVirtualComponents.updateVirtualComponentValues(handler, profile,
                pushed("group:200", "{\"value\":[\"number:201\"]}"));

        assertThat(updated, is(false));
        assertThat(profile.vComponentsDirty, is(false));
    }
}
