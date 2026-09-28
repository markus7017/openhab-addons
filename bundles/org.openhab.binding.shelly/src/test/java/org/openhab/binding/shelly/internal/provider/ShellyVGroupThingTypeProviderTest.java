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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLY2_VCOMP_GROUP;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.type.ChannelGroupDefinition;
import org.openhab.core.thing.type.ChannelGroupTypeUID;
import org.openhab.core.thing.type.ThingType;
import org.openhab.core.thing.type.ThingTypeBuilder;
import org.openhab.core.thing.type.ThingTypeRegistry;

/**
 * Tests for {@link ShellyVGroupThingTypeProvider}: the per-Thing synthetic {@link ThingType} that appends a
 * {@link ChannelGroupDefinition} per named Virtual Components Group, so Main UI renders each "vgroup<cid>" as its
 * own labeled section.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVGroupThingTypeProviderTest {

    private static final ThingUID THING_UID = new ThingUID(THING_TYPE_SHELLYPLUS1, "test");
    private static final ThingTypeUID VGROUP_UID = new ThingTypeUID(BINDING_ID,
            THING_TYPE_SHELLYPLUS1.getId() + VGROUP_TYPE_MARKER + "test");

    private final ThingRegistry thingRegistry = mock(ThingRegistry.class);
    private final ThingTypeRegistry thingTypeRegistry = mock(ThingTypeRegistry.class);
    private final ShellyVGroupThingTypeProvider provider = new ShellyVGroupThingTypeProvider(thingRegistry,
            thingTypeRegistry);

    private static ThingType baseType() {
        return ThingTypeBuilder.instance(THING_TYPE_SHELLYPLUS1, "Shelly Plus 1")
                .withChannelGroupDefinitions(
                        List.of(new ChannelGroupDefinition("status", new ChannelGroupTypeUID(BINDING_ID, "status"))))
                .build();
    }

    private static ShellyDeviceProfile vComponentsProfile(ShellyVCComponent... components) {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        profile.vComponentsProbed = true;
        profile.vComponents = List.of(components);
        return profile;
    }

    private static ShellyVCComponent vgroup(int id, String name, String... memberKeys) {
        ShellyVCComponent vc = new ShellyVCComponent();
        vc.type = SHELLY2_VCOMP_GROUP;
        vc.id = id;
        vc.name = name;
        vc.groupMembers = List.of(memberKeys);
        return vc;
    }

    private static ThingHandler shellyHandler(ShellyDeviceProfile profile) {
        ThingHandler handler = mock(ThingHandler.class, withSettings().extraInterfaces(ShellyThingInterface.class));
        when(((ShellyThingInterface) handler).getProfile()).thenReturn(profile);
        return handler;
    }

    private static Thing thingWithHandler(ThingUID uid, ThingTypeUID currentType, @Nullable ThingHandler handler) {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(uid);
        when(thing.getThingTypeUID()).thenReturn(currentType);
        when(thing.getHandler()).thenReturn(handler);
        return thing;
    }

    @Test
    void getThingTypeReturnsNullForUidsNotCarryingTheMarker() {
        assertThat(provider.getThingType(new ThingTypeUID("other", "thing"), null), is(nullValue()));
        assertThat(provider.getThingType(THING_TYPE_SHELLYPLUS1, null), is(nullValue()));
    }

    @Test
    void getThingTypeReturnsNullWhenNoThingMatchesTheEncodedThingId() {
        when(thingRegistry.getAll()).thenReturn(List.of());

        assertThat(provider.getThingType(VGROUP_UID, null), is(nullValue()));
    }

    @Test
    void getThingTypeReturnsNullWhenHandlerIsNotAShellyThingInterface() {
        Thing thing = thingWithHandler(THING_UID, THING_TYPE_SHELLYPLUS1, mock(ThingHandler.class));
        when(thingRegistry.getAll()).thenReturn(List.of(thing));

        assertThat(provider.getThingType(VGROUP_UID, null), is(nullValue()));
    }

    @Test
    void getThingTypeReturnsNullWhenBaseThingTypeIsUnknown() {
        Thing thing = thingWithHandler(THING_UID, THING_TYPE_SHELLYPLUS1, shellyHandler(vComponentsProfile()));
        when(thingRegistry.getAll()).thenReturn(List.of(thing));
        when(thingTypeRegistry.getThingType(THING_TYPE_SHELLYPLUS1, null)).thenReturn(null);

        assertThat(provider.getThingType(VGROUP_UID, null), is(nullValue()));
    }

    @Test
    void getThingTypeAppendsOneChannelGroupDefinitionPerNamedGroup() {
        ShellyDeviceProfile profile = vComponentsProfile(vgroup(200, "Virtual1", "boolean:300"),
                vgroup(201, "VirtualGroup2", "number:301"));
        Thing thing = thingWithHandler(THING_UID, THING_TYPE_SHELLYPLUS1, shellyHandler(profile));
        when(thingRegistry.getAll()).thenReturn(List.of(thing));
        ThingType base = baseType();
        when(thingTypeRegistry.getThingType(THING_TYPE_SHELLYPLUS1, null)).thenReturn(base);

        ThingType resultType = Objects.requireNonNull(provider.getThingType(VGROUP_UID, null));

        assertThat(resultType.getUID(), is(VGROUP_UID));
        assertThat(resultType.getLabel(), is(base.getLabel()));
        assertThat(resultType.getChannelGroupDefinitions(), hasSize(3));
        assertThat(resultType.getChannelGroupDefinitions(), hasItem(base.getChannelGroupDefinitions().get(0)));

        ChannelGroupDefinition group200 = resultType.getChannelGroupDefinitions().stream()
                .filter(g -> (CHANNEL_GROUP_VGROUP_PREFIX + "200").equals(g.getId())).findFirst().orElseThrow();
        assertThat(group200.getLabel(), is("Virtual1"));
        assertThat(group200.getTypeUID(), is(VGROUP_TYPE_UID));

        ChannelGroupDefinition group201 = resultType.getChannelGroupDefinitions().stream()
                .filter(g -> (CHANNEL_GROUP_VGROUP_PREFIX + "201").equals(g.getId())).findFirst().orElseThrow();
        assertThat(group201.getLabel(), is("VirtualGroup2"));
    }

    @Test
    void getThingTypeKeepsBaseGroupsUnchangedWhenThereIsNoQualifyingGroup() {
        Thing thing = thingWithHandler(THING_UID, THING_TYPE_SHELLYPLUS1, shellyHandler(vComponentsProfile()));
        when(thingRegistry.getAll()).thenReturn(List.of(thing));
        ThingType base = baseType();
        when(thingTypeRegistry.getThingType(THING_TYPE_SHELLYPLUS1, null)).thenReturn(base);

        ThingType resultType = Objects.requireNonNull(provider.getThingType(VGROUP_UID, null));

        assertThat(resultType.getChannelGroupDefinitions(), hasSize(1));
    }

    @Test
    void getThingTypesReturnsOnlyThingsAlreadySwappedToTheSyntheticType() {
        Thing swapped = thingWithHandler(THING_UID, VGROUP_UID,
                shellyHandler(vComponentsProfile(vgroup(200, "Virtual1", "boolean:300"))));
        ThingUID otherThingUID = new ThingUID(THING_TYPE_SHELLYPLUS1, "other");
        Thing notSwapped = thingWithHandler(otherThingUID, THING_TYPE_SHELLYPLUS1, shellyHandler(vComponentsProfile()));
        when(thingRegistry.getAll()).thenReturn(List.of(swapped, notSwapped));
        when(thingTypeRegistry.getThingType(THING_TYPE_SHELLYPLUS1, null)).thenReturn(baseType());

        Collection<ThingType> types = provider.getThingTypes(null);

        assertThat(types, hasSize(1));
        assertThat(types.iterator().next().getUID(), is(VGROUP_UID));
    }
}
