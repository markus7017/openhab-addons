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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.dto.ShellyVirtualComponentsJsonDTO.ShellyVCComponent;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The {@link ShellyVirtualComponents} implements status updates and command handling for Shelly Virtual Components
 * (Boolean/Number/Text/Enum/Group/Button), including the synthetic vgroup {@link ThingTypeUID} swap that lets
 * Main UI render each named Virtual Components Group as its own labeled section.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyVirtualComponents.class);

    /**
     * Every device-model classification (handler dispatch, {@code ShellyDeviceProfile.initFromThingType},
     * generation/BLU detection, ...) is keyed off exact matches against the device's real, static
     * {@link ThingTypeUID}. Once a Thing has been swapped to its per-Thing synthetic vgroup type (see
     * {@link #checkVGroupThingType}), {@code thing.getThingTypeUID()} no longer matches any of those lookups, so
     * every call site that uses the Thing's type for classification (not just for display) must resolve back to
     * the real type first via this method.
     *
     * @param thingTypeUID the Thing's current type, real or synthetic
     * @return the real device ThingTypeUID (unchanged if it wasn't a synthetic vgroup type)
     */
    public static ThingTypeUID resolveVGroupBaseType(ThingTypeUID thingTypeUID) {
        String id = thingTypeUID.getId();
        int idx = id.indexOf(VGROUP_TYPE_MARKER);
        return idx < 0 ? thingTypeUID : new ThingTypeUID(thingTypeUID.getBindingId(), id.substring(0, idx));
    }

    static void reconcileVirtualComponentChannels(ShellyThingInterface thingHandler, ShellyDeviceProfile profile) {
        Set<String> obsolete = ShellyChannelDefinitions.getObsoleteVirtualComponentChannelIds(thingHandler.getThing(),
                profile);
        if (!obsolete.isEmpty()) {
            thingHandler.removeChannels(obsolete);
        }
    }

    /**
     * Once the device has at least one Virtual Components Group with members, swap the Thing to a synthetic
     * per-Thing {@link ThingTypeUID} (handled by {@code ShellyVGroupThingTypeProvider}) so Main UI can render each
     * "vgroup<cid>" as its own labeled section. The synthetic UID encodes only the Thing's identity, not the group
     * content, so this fires at most once per Thing; renames/membership changes afterward are picked up live by
     * the provider on every lookup.
     */
    static void checkVGroupThingType(ShellyThingInterface thingHandler, ShellyDeviceProfile profile) {
        ThingTypeUID currentType = thingHandler.getThing().getThingTypeUID();
        if (currentType.getId().contains(VGROUP_TYPE_MARKER)) {
            return; // already swapped
        }
        boolean hasNamedGroups = profile.vComponents.stream().anyMatch(c -> {
            List<String> members = c.groupMembers;
            return SHELLY2_VCOMP_GROUP.equals(c.type) && members != null && !members.isEmpty();
        });
        if (hasNamedGroups) {
            ThingTypeUID vgType = new ThingTypeUID(BINDING_ID,
                    currentType.getId() + VGROUP_TYPE_MARKER + thingHandler.getThing().getUID().getId());
            thingHandler.changeThingType(vgType);
        }
    }

    /**
     * Builds the Enum option list / Number min-max-step-unit for every discovered Virtual Enum/Number component.
     */
    public static void addVirtualComponentStateOptions(ShellyThingInterface thingHandler, ShellyDeviceProfile profile) {
        for (ShellyVCComponent vc : profile.vComponents) {
            String[] vcOptions = vc.options;
            if (CHANNEL_VCOMP_ENUM.equals(vc.type) && vcOptions != null) {
                for (String group : ShellyChannelDefinitions.getVirtualComponentChannelGroups(profile, vc)) {
                    String channelId = mkChannelId(group, vc.type + vc.id);
                    LOGGER.debug("{}: Adding {} option(s) to Virtual Enum channel {}", thingHandler.getThingName(),
                            vcOptions.length, channelId);
                    thingHandler.clearStateOptions(channelId);
                    Map<String, String> titles = vc.optionTitles;
                    for (String option : vcOptions) {
                        String title = titles != null ? titles.get(option) : null;
                        thingHandler.addStateOption(channelId, option,
                                title != null && !title.isBlank() ? title : option);
                    }
                }
            }
            if (CHANNEL_VCOMP_NUMBER.equals(vc.type)) {
                Double min = vc.min != null && vc.min != SHELLY2_VCOMP_NUMBER_MIN_SENTINEL ? vc.min : null;
                Double max = vc.max != null && vc.max != SHELLY2_VCOMP_NUMBER_MAX_SENTINEL ? vc.max : null;
                for (String group : ShellyChannelDefinitions.getVirtualComponentChannelGroups(profile, vc)) {
                    String channelId = mkChannelId(group, vc.type + vc.id);
                    LOGGER.debug("{}: Setting Virtual Number range for channel {}: min={}, max={}, step={}, unit={}",
                            thingHandler.getThingName(), channelId, min, max, vc.step, vc.unit);
                    thingHandler.setNumberRange(channelId, min, max, vc.step, vc.unit);
                }
            }
        }
    }

    /**
     * Pushes the current value of every discovered Boolean/Number/Text/Enum virtual component into its channel.
     * Group and Button don't reach here: Group has no state of its own, Button is stateless and only ever fires
     * as a trigger event.
     */
    static void updateVirtualComponentStatus(ShellyThingInterface thingHandler, ShellyDeviceProfile profile) {
        for (ShellyVCComponent vc : profile.vComponents) {
            JsonElement jvalue = vc.value;
            if (jvalue == null) {
                continue; // not yet reported, e.g. right after discovery; also always null for button
            }
            updateVirtualComponentChannel(thingHandler, profile, vc);
        }
    }

    /**
     * Applies the value changes of a NotifyStatus message (keyed like "boolean:200") to the discovered components
     * and their channels.
     *
     * @return true if at least one channel was updated
     */
    public static boolean updateVirtualComponentValues(ShellyThingInterface thingHandler, ShellyDeviceProfile profile,
            Map<String, JsonObject> changes) {
        boolean updated = false;
        for (Map.Entry<String, JsonObject> change : changes.entrySet()) {
            JsonElement value = change.getValue().get("value");
            if (value == null) {
                continue; // only other attributes changed
            }
            String key = change.getKey();
            String type = key.substring(0, key.indexOf(':'));
            int id;
            try {
                id = Integer.parseInt(key.substring(key.indexOf(':') + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            for (ShellyVCComponent vc : profile.vComponents) {
                if (vc.type.equals(type) && vc.id == id) {
                    vc.value = value;
                    updateVirtualComponentChannel(thingHandler, profile, vc);
                    updated = true;
                    break;
                }
            }
        }
        return updated;
    }

    /**
     * Fires a Virtual Button's trigger channel. Unlike a physical input's push event this can't reuse
     * {@link ShellyThingInterface#triggerButton}, which assumes a fixed input-channel naming convention and also
     * updates {@code CHANNEL_LAST_UPDATE} on the group - neither applies to a vcomponent's trigger channel.
     */
    public static void triggerVirtualButton(ShellyThingInterface thingHandler, ShellyDeviceProfile profile, int id,
            String trigger) {
        String thingName = thingHandler.getThingName();
        for (ShellyVCComponent vc : profile.vComponents) {
            if (SHELLY2_VCOMP_BUTTON.equals(vc.type) && vc.id == id) {
                LOGGER.debug("{}: Virtual Button {} triggered: {}", thingName, id, trigger);
                for (String group : ShellyChannelDefinitions.getVirtualComponentChannelGroups(profile, vc)) {
                    thingHandler.triggerChannel(group, CHANNEL_VCOMP_BUTTON + id, trigger);
                }
                return;
            }
        }
        LOGGER.debug("{}: Virtual Button {} not found in profile, ignoring event", thingName, id);
    }

    private static void updateVirtualComponentChannel(ShellyThingInterface thingHandler, ShellyDeviceProfile profile,
            ShellyVCComponent vc) {
        JsonElement jvalue = vc.value;
        State state = jvalue != null ? toVirtualComponentState(vc.type, jvalue) : null;
        if (state != null) {
            for (String group : ShellyChannelDefinitions.getVirtualComponentChannelGroups(profile, vc)) {
                thingHandler.updateChannel(group, vc.type + vc.id, state);
            }
        }
    }

    /**
     * @return the channel state for a Boolean/Number/Text/Enum component's reported value, or null for a type that
     *         has no state channel (Group carries a member array, Button is stateless)
     */
    private static @Nullable State toVirtualComponentState(String type, JsonElement value) {
        // A component can report a JSON null instead of a value - an Enum with no default_value does so after a
        // reboot. getAsBoolean()/getAsDouble()/getAsString() throw on anything but a primitive, and an invented
        // OFF/0/"" would read like a genuine device value, so publish UNDEF in that case.
        boolean reported = value.isJsonPrimitive();
        return switch (type) {
            case CHANNEL_VCOMP_BOOLEAN -> reported ? OnOffType.from(value.getAsBoolean()) : UnDefType.UNDEF;
            case CHANNEL_VCOMP_NUMBER -> reported ? new DecimalType(value.getAsDouble()) : UnDefType.UNDEF;
            case CHANNEL_VCOMP_TEXT, CHANNEL_VCOMP_ENUM ->
                reported ? getStringType(value.getAsString()) : UnDefType.UNDEF;
            default -> null;
        };
    }

    /**
     * Dispatches a command sent to a Boolean/Number/Text/Enum virtual component channel to the matching
     * {@code <Type>.Set} RPC call. Group and Button have no command path: Group is a pure grouping container and
     * Button is stateless/event-only, neither ever gets a channel (see {@link #updateVirtualComponentStatus}).
     */
    public static void handleVirtualComponentCommand(ShellyThingInterface thingHandler, String channel, Command command)
            throws ShellyApiException {
        String thingName = thingHandler.getThingName();
        ShellyVCComponent vc = thingHandler.getProfile().vComponents.stream()
                .filter(c -> (c.type + c.id).equals(channel)).findFirst().orElse(null);
        if (vc == null) {
            LOGGER.debug("{}: Unknown Virtual Component channel {}, command ignored", thingName, channel);
            return;
        }

        ShellyApiInterface api = thingHandler.getApi();
        switch (vc.type) {
            case CHANNEL_VCOMP_BOOLEAN:
                api.setVirtualBoolean(vc.id, command == OnOffType.ON);
                break;
            case CHANNEL_VCOMP_NUMBER:
                // The device rejects a value outside the configured range / longer than max_len anyway, but only
                // as an RPC error after the fact - checking here turns that into a log line naming the limit.
                double number = getNumber(command);
                Double min = vc.min, max = vc.max;
                if ((min != null && number < min) || (max != null && number > max)) {
                    LOGGER.warn("{}: Value {} is outside the range {}..{} configured for Virtual Number {}, ignoring",
                            thingName, number, min, max, vc.id);
                    return;
                }
                api.setVirtualNumber(vc.id, number);
                break;
            case CHANNEL_VCOMP_TEXT:
                String text = getString(command);
                Integer maxLen = vc.maxLen;
                if (maxLen != null && text.length() > maxLen) {
                    LOGGER.warn("{}: Text is {} characters, more than the {} configured for Virtual Text {}, ignoring",
                            thingName, text.length(), maxLen, vc.id);
                    return;
                }
                api.setVirtualText(vc.id, text);
                break;
            case CHANNEL_VCOMP_ENUM:
                api.setVirtualEnum(vc.id, getString(command));
                break;
            default:
                LOGGER.debug("{}: Command not supported for Virtual Component type {}", thingName, vc.type);
                break;
        }
    }
}
