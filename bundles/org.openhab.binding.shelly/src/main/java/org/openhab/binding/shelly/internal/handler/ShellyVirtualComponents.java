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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent.ShellyVCMeta;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent.ShellyVCUi;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.util.ShellyVersionComparator;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

/**
 * The {@link ShellyVirtualComponents} implements channel, status and command handling for Shelly Virtual Components
 * (Boolean/Number/Text/Enum/Group).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyVirtualComponents.class);

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

    /**
     * @return Enum options with their titles or the Number range, unit and step as configured on the device
     */
    public static @Nullable StateDescription getStateDescription(ShellyDeviceProfile profile, String channelName) {
        ShellyVCComponent vc = findComponent(profile, channelName);
        if (vc == null) {
            return null;
        }
        ShellyVCMeta meta = vc.meta;
        ShellyVCUi ui = meta != null ? meta.ui : null;
        String[] options = vc.options;
        StateDescriptionFragmentBuilder builder = StateDescriptionFragmentBuilder.create();
        if (CHANNEL_VCOMP_ENUM.equals(vc.type) && options != null) {
            JsonElement titles = ui != null ? ui.titles : null;
            for (int i = 0; i < options.length; i++) {
                JsonElement title = titles == null ? null
                        : titles.isJsonObject() ? titles.getAsJsonObject().get(options[i])
                                : titles.isJsonArray() && i < titles.getAsJsonArray().size()
                                        ? titles.getAsJsonArray().get(i)
                                        : null;
                String label = title != null && title.isJsonPrimitive() ? title.getAsString() : "";
                builder.withOption(new StateOption(options[i], label.isBlank() ? options[i] : label));
            }
        } else if (CHANNEL_VCOMP_NUMBER.equals(vc.type)) {
            Double min = vc.min, max = vc.max, step = ui != null ? ui.step : null;
            String unit = ui != null ? ui.unit : null;
            if (min != null && min != SHELLY2_VCOMP_NUMBER_MIN_SENTINEL) {
                builder.withMinimum(BigDecimal.valueOf(min));
            }
            if (max != null && max != SHELLY2_VCOMP_NUMBER_MAX_SENTINEL) {
                builder.withMaximum(BigDecimal.valueOf(max));
            }
            if (step != null) {
                builder.withStep(BigDecimal.valueOf(step));
            }
            if (unit != null && !unit.isBlank()) {
                builder.withPattern("%.2f " + unit.replace("%", "%%"));
            }
        } else {
            return null;
        }
        return builder.build().toStateDescription();
    }

    public static void handleVirtualComponentCommand(ShellyThingInterface thingHandler, String channel, Command command)
            throws ShellyApiException {
        String thingName = thingHandler.getThingName();
        ShellyVCComponent vc = findComponent(thingHandler.getProfile(), channel);
        if (vc == null) {
            LOGGER.debug("{}: Unknown Virtual Component channel {}, command ignored", thingName, channel);
            return;
        }
        // checked here to log the configured limit, the device would only return an RPC error
        Object value;
        switch (vc.type) {
            case CHANNEL_VCOMP_BOOLEAN:
                value = command == OnOffType.ON;
                break;
            case CHANNEL_VCOMP_NUMBER:
                double number = getNumber(command);
                Double min = vc.min, max = vc.max;
                if ((min != null && number < min) || (max != null && number > max)) {
                    LOGGER.warn("{}: Value {} is outside the range {}..{} of Virtual Number {}, ignoring", thingName,
                            number, min, max, vc.id);
                    return;
                }
                value = number;
                break;
            case CHANNEL_VCOMP_TEXT:
            case CHANNEL_VCOMP_ENUM:
                String text = getString(command);
                Integer maxLen = vc.maxLen;
                if (maxLen != null && text.length() > maxLen) {
                    LOGGER.warn("{}: Text exceeds the {} characters of Virtual Text {}, ignoring", thingName, maxLen,
                            vc.id);
                    return;
                }
                value = text;
                break;
            default:
                return;
        }
        thingHandler.getApi().setVirtualValue(vc.type, vc.id, value);
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
