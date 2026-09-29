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

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.SHELLY2_VCOMP_GROUP;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.ThingTypeProvider;
import org.openhab.core.thing.type.ChannelGroupDefinition;
import org.openhab.core.thing.type.ThingType;
import org.openhab.core.thing.type.ThingTypeBuilder;
import org.openhab.core.thing.type.ThingTypeRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link ShellyVGroupThingTypeProvider} provides a per-Thing synthetic {@link ThingType} for devices that have
 * at least one named Virtual Components Group. It clones the device's real, static {@link ThingType} and appends
 * one {@link ChannelGroupDefinition} per Group, labeled with the Group's device-configured name, so Main UI renders
 * each {@code vgroup<cid>} as its own section instead of a flat channel list.
 * <p>
 * The synthetic {@link ThingTypeUID} is {@code <baseTypeId>_vg_<thingId>} - unique per Thing, so this provider
 * fast-rejects every lookup that doesn't carry the {@code VGROUP_TYPE_MARKER} marker, keeping its cost for the
 * rest of the system to one cheap string check.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingTypeProvider.class)
public class ShellyVGroupThingTypeProvider implements ThingTypeProvider {
    private final ThingRegistry thingRegistry;
    private final ThingTypeRegistry thingTypeRegistry;

    @Activate
    public ShellyVGroupThingTypeProvider(final @Reference ThingRegistry thingRegistry,
            final @Reference ThingTypeRegistry thingTypeRegistry) {
        this.thingRegistry = thingRegistry;
        this.thingTypeRegistry = thingTypeRegistry;
    }

    @Override
    public @Nullable ThingType getThingType(ThingTypeUID uid, @Nullable Locale locale) {
        if (!BINDING_ID.equals(uid.getBindingId()) || !uid.getId().contains(VGROUP_TYPE_MARKER)) {
            return null;
        }
        String[] parts = uid.getId().split(VGROUP_TYPE_MARKER, 2);
        if (parts.length != 2) {
            return null;
        }
        String baseId = parts[0];
        String thingId = parts[1];

        // Thing identity (the last UID segment) is frozen since discovery and independent of the Thing's
        // current-vs-target type, so this is the only lookup that's safe to use during a type migration.
        Thing thing = thingRegistry.getAll().stream()
                .filter(t -> BINDING_ID.equals(t.getUID().getBindingId()) && thingId.equals(t.getUID().getId()))
                .findFirst().orElse(null);
        if (thing == null) {
            return null;
        }

        ThingType base = thingTypeRegistry.getThingType(new ThingTypeUID(BINDING_ID, baseId), locale);
        if (base == null) {
            return null;
        }

        // core resolves the type before creating the handler (startup, disabled Thing): serve the base groups,
        // the vgroup sections appear on the next lookup once the handler has probed the device
        List<ChannelGroupDefinition> vgroups = thing.getHandler() instanceof ShellyThingInterface handler
                ? buildVGroupDefinitions(handler)
                : List.of();
        return cloneWithGroups(uid, base, vgroups);
    }

    @Override
    public Collection<ThingType> getThingTypes(@Nullable Locale locale) {
        List<ThingType> result = new ArrayList<>();
        for (Thing thing : thingRegistry.getAll()) {
            ThingTypeUID currentType = thing.getThingTypeUID();
            if (BINDING_ID.equals(currentType.getBindingId()) && currentType.getId().contains(VGROUP_TYPE_MARKER)) {
                ThingType type = getThingType(currentType, locale);
                if (type != null) {
                    result.add(type);
                }
            }
        }
        return result;
    }

    private List<ChannelGroupDefinition> buildVGroupDefinitions(ShellyThingInterface handler) {
        List<ChannelGroupDefinition> groups = new ArrayList<>();
        for (ShellyVCComponent vc : handler.getProfile().vComponents) {
            List<String> members = vc.groupMembers;
            if (SHELLY2_VCOMP_GROUP.equals(vc.type) && members != null && !members.isEmpty()) {
                String name = vc.name;
                groups.add(new ChannelGroupDefinition(CHANNEL_GROUP_VGROUP_PREFIX + vc.id, VGROUP_TYPE_UID,
                        name != null && !name.isBlank() ? name : null, null));
            }
        }
        return groups;
    }

    private ThingType cloneWithGroups(ThingTypeUID uid, ThingType base, List<ChannelGroupDefinition> extraGroups) {
        List<ChannelGroupDefinition> groups = new ArrayList<>(base.getChannelGroupDefinitions());
        groups.addAll(extraGroups);

        ThingTypeBuilder builder = ThingTypeBuilder.instance(uid, base.getLabel()) //
                .isListed(base.isListed()) //
                .withChannelDefinitions(base.getChannelDefinitions()) //
                .withChannelGroupDefinitions(groups) //
                .withProperties(base.getProperties()) //
                .withExtensibleChannelTypeIds(base.getExtensibleChannelTypeIds()) //
                .withSupportedBridgeTypeUIDs(base.getSupportedBridgeTypeUIDs());

        String description = base.getDescription();
        if (description != null) {
            builder.withDescription(description);
        }
        String category = base.getCategory();
        if (category != null) {
            builder.withCategory(category);
        }
        String representationProperty = base.getRepresentationProperty();
        if (representationProperty != null) {
            builder.withRepresentationProperty(representationProperty);
        }
        URI configDescriptionURI = base.getConfigDescriptionURI();
        if (configDescriptionURI != null) {
            builder.withConfigDescriptionURI(configDescriptionURI);
        }
        String semanticEquipmentTag = base.getSemanticEquipmentTag();
        if (semanticEquipmentTag != null) {
            builder.withSemanticEquipmentTag(semanticEquipmentTag);
        }
        return builder.build();
    }
}
