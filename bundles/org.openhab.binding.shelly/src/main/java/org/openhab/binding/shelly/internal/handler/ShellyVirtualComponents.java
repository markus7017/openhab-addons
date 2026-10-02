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

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.util.ShellyVersionComparator;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonElement;

/**
 * The {@link ShellyVirtualComponents} implements channel and status handling for Shelly Virtual Components
 * (Boolean/Number/Text/Enum/Group).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponents {

    public static boolean isSupported(ShellyDeviceProfile profile) {
        return profile.isGen2 && profile.alwaysOn
                && new ShellyVersionComparator().compare(profile.fwVersion, SHELLY2_API_FW_VCOMPONENTS) >= 0;
    }

    /**
     * Adds, relabels and removes the channels to match the components on the device, then publishes their values.
     */
    static void updateVirtualComponents(ShellyThingInterface thingHandler, ShellyDeviceProfile profile) {
        Map<String, Channel> channels = ShellyChannelDefinitions.createVirtualComponentChannels(thingHandler.getThing(),
                profile);
        Map<String, Channel> relabeled = new HashMap<>();
        Set<String> obsolete = new HashSet<>();
        for (Channel channel : thingHandler.getThing().getChannelsOfGroup(CHANNEL_GROUP_VCOMPONENTS)) {
            String channelId = channel.getUID().getId();
            Channel desired = channels.get(channelId);
            if (desired == null) {
                obsolete.add(channelId);
            } else if (!getString(desired.getLabel()).equals(getString(channel.getLabel()))) {
                relabeled.put(channelId, desired);
            }
        }
        thingHandler.updateThingChannels(relabeled, channels);
        thingHandler.removeChannels(obsolete);
        profile.vComponents.forEach(vc -> updateVirtualComponentChannel(thingHandler, vc));
    }

    private static @Nullable ShellyVCComponent findComponent(ShellyDeviceProfile profile, String channelName) {
        return profile.vComponents.stream().filter(vc -> (vc.type + vc.id).equals(channelName)).findFirst()
                .orElse(null);
    }

    private static void updateVirtualComponentChannel(ShellyThingInterface thingHandler, ShellyVCComponent vc) {
        JsonElement value = vc.value;
        if (value == null) {
            return;
        }
        // an Enum without default value reports JSON null after a reboot
        boolean reported = value.isJsonPrimitive();
        State state = switch (vc.type) {
            case CHANNEL_VCOMP_BOOLEAN -> reported ? OnOffType.from(value.getAsBoolean()) : UnDefType.UNDEF;
            case CHANNEL_VCOMP_NUMBER -> reported ? new DecimalType(value.getAsDouble()) : UnDefType.UNDEF;
            case CHANNEL_VCOMP_TEXT, CHANNEL_VCOMP_ENUM ->
                reported ? getStringType(value.getAsString()) : UnDefType.UNDEF;
            default -> null;
        };
        if (state != null) {
            thingHandler.updateChannel(CHANNEL_GROUP_VCOMPONENTS, vc.type + vc.id, state);
        }
    }
}
