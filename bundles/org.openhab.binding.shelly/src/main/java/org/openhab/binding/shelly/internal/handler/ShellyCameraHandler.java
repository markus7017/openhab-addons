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
import static org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api1.Shelly1CoapServer;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudio;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudioOutput;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraEnable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraMotion;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraNightVision;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraStatus;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraZoneConfig;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.provider.ShellyStateDescriptionProvider;
import org.openhab.binding.shelly.internal.provider.ShellyTranslationProvider;
import org.openhab.core.i18n.LocationProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The {@link ShellyCameraHandler} implements status mapping, channel updates and command handling for the Shelly
 * Camera
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyCameraHandler extends ShellyBaseHandler {
    private static final int VOLUME_STEP = 10;

    public ShellyCameraHandler(final Thing thing, final ShellyTranslationProvider translationProvider,
            final ShellyBindingRuntimeConfig bindingConfig, ShellyThingTable thingTable,
            final Shelly1CoapServer coapServer, final HttpClient httpClient, WebSocketClient webSocketClient,
            final LocationProvider locationProvider, ShellyStateDescriptionProvider stateDescriptionProvider) {
        super(thing, translationProvider, bindingConfig, thingTable, coapServer, httpClient, webSocketClient,
                locationProvider, stateDescriptionProvider);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType && CHANNEL_CAMERA_SNAPSHOT.equals(channelUID.getIdWithoutGroup())) {
            scheduler.execute(() -> refreshSnapshot(false));
            return;
        }
        super.handleCommand(channelUID, command);
    }

    @Override
    public void onCameraZoneEvent(int zone, String event) {
        boolean motion = SHELLY2_EVENT_CAMERA_MOTION.equals(event);
        updateChannel(CHANNEL_GROUP_CAMERA_ZONES, CHANNEL_CAMERA_ZONE_MOTION + zone, OnOffType.from(motion));
        if (motion) {
            String name = profile.cameraZones.getOrDefault(zone, "");
            updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_ZONE,
                    getStringType(name.isEmpty() ? String.valueOf(zone) : name));
        }
    }

    /**
     * Derives camera events from status changes, the device reports them by NotifyStatus without a NotifyEvent
     */
    private boolean detectEvent(String group, String channel, @Nullable Boolean value, String onEvent,
            String offEvent) {
        State previous = getChannelValue(group, channel);
        if (value == null || !(previous instanceof OnOffType) || previous == OnOffType.from(value)) {
            return false;
        }
        String event = (value ? onEvent : offEvent).toUpperCase(Locale.ROOT);
        triggerChannel(CHANNEL_GROUP_SENSOR, CHANNEL_EVENT_TRIGGER, event);
        if (SHELLY2_EVENT_CAMERA_MOTION_END.equalsIgnoreCase(event)) {
            return false;
        }
        boolean updated = updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT, getStringType(event));
        updated |= updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_TS, getTimestamp());
        if (!SHELLY2_EVENT_CAMERA_MOTION.equalsIgnoreCase(event)) {
            updated |= updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_ZONE, UnDefType.UNDEF);
        }
        return updated;
    }

    void refreshSnapshot(boolean motionEvent) {
        Shelly2CameraStatus cs = profile.status.camera;
        if (cs == null || Boolean.TRUE.equals(cs.privacy)) {
            return; // the device answers 502 while privacy mode is on
        }
        try {
            RawType image = new RawType(api.getCameraSnapshot(), SHELLY2_CAMERA_SNAPSHOT_MIME_TYPE);
            updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_SNAPSHOT, image);
            if (motionEvent) {
                updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_IMAGE, image);
            }
        } catch (ShellyApiException e) {
            logger.debug("{}: Unable to fetch camera snapshot: {}", thingName, e.toString());
        }
    }

    @Override
    public boolean handleDeviceCommand(ChannelUID channelUID, Command command) throws ShellyApiException {
        String channel = channelUID.getIdWithoutGroup();
        boolean on = command == OnOffType.ON;
        switch (channel) {
            case CHANNEL_CAMERA_ARMED:
                api.setCamera(on, null);
                break;
            case CHANNEL_CAMERA_PRIVACY:
                api.setCamera(null, on);
                break;
            case CHANNEL_CAMERA_TAKE_SNAPSHOT:
                if (!on) {
                    return false;
                }
                // auto-update only predicts ON, publish ON and OFF so the UI sees both state changes
                String takeSnapshot = mkChannelId(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_TAKE_SNAPSHOT);
                updateChannel(takeSnapshot, OnOffType.ON, true);
                scheduler.execute(() -> {
                    refreshSnapshot(false);
                    updateChannel(takeSnapshot, OnOffType.OFF, true);
                });
                return false;
            case CHANNEL_MEDIA_PLAY_SOUND:
                api.playCameraSound(command.toString());
                return false;
            default:
                Shelly2CameraConfig config = buildConfig(channel, command, profile.cameraConfig);
                if (config == null) {
                    return false;
                }
                api.setCameraConfig(config);
        }
        updateDeviceStatus(profile.status);
        return false;
    }

    @Override
    public boolean updateDeviceStatus(ShellySettingsStatus status) throws ShellyApiException {
        Shelly2CameraStatus cs = status.camera;
        Shelly2CameraConfig config = profile.cameraConfig;
        updateThingChannels(Map.of(),
                ShellyChannelDefinitions.createCameraChannels(getThing(), config, cs, profile.cameraZones));

        boolean updated = false;
        if (cs != null) {
            if (Boolean.TRUE.equals(cs.motion)
                    && getChannelValue(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION) != OnOffType.ON) {
                updated |= updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION_TS, getTimestamp());
                scheduler.execute(() -> refreshSnapshot(true));
            }
            updated |= detectEvent(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION, cs.motion, SHELLY2_EVENT_CAMERA_MOTION,
                    SHELLY2_EVENT_CAMERA_MOTION_END);
            updated |= detectEvent(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_ARMED, cs.arm, SHELLY2_EVENT_CAMERA_ARMED,
                    SHELLY2_EVENT_CAMERA_DISARMED);
            updated |= detectEvent(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_PRIVACY, cs.privacy,
                    SHELLY2_EVENT_CAMERA_PRIVACY_ON, SHELLY2_EVENT_CAMERA_PRIVACY_OFF);
            if (Boolean.FALSE.equals(cs.motion)) {
                // no motion in any zone, also recovers zone channels from a missed camerazone.motion_end
                for (Integer zone : profile.cameraZones.keySet()) {
                    updated |= updateChannel(CHANNEL_GROUP_CAMERA_ZONES, CHANNEL_CAMERA_ZONE_MOTION + zone,
                            OnOffType.OFF);
                }
            }
            updated |= updateSwitch(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION, cs.motion);
            updated |= updateSwitch(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_ARMED, cs.arm);
            updated |= updateSwitch(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_PRIVACY, cs.privacy);
            updated |= updateString(CHANNEL_CAMERA_STREAMER, cs.streamer);
        }
        if (config == null) {
            return updated;
        }
        Shelly2CameraNightVision nightVision = config.nightVision;
        if (nightVision != null) {
            updated |= updateString(CHANNEL_CAMERA_NIGHT_VISION, nightVision.mode);
            updated |= updateSwitch(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_IR_LEDS, nightVision.irLeds);
        }
        Shelly2CameraMotion motion = config.motion;
        if (motion != null) {
            updated |= updateString(CHANNEL_CAMERA_MOTION_SENSITIVITY, motion.sensitivity);
            updated |= updateSwitch(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_RECORD_ON_MOTION, getEnable(motion.recording));
        }
        updated |= updateSwitch(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_RTSP, getEnable(config.rtsp));
        updated |= updateSwitch(CHANNEL_GROUP_DEV_STATUS, CHANNEL_LED_STATUS_DISABLE, negate(getEnable(config.led)));
        updated |= updateSwitch(CHANNEL_GROUP_MEDIA, CHANNEL_MEDIA_SOUNDS, getEnable(config.sounds));
        Shelly2CameraAudio audio = config.audio;
        updated |= updateSwitch(CHANNEL_GROUP_MEDIA, CHANNEL_MEDIA_MUTE,
                negate(getEnable(audio != null ? audio.input : null)));
        Integer volume = getVolume(config);
        if (volume != null) {
            updated |= updateChannel(CHANNEL_GROUP_MEDIA, CHANNEL_MEDIA_VOLUME, new PercentType(clampVolume(volume)));
        }
        return updated;
    }

    // NotifyStatus only carries the changed fields, merge them into the cached status instead of replacing it
    public static Shelly2CameraStatus mergeStatus(@Nullable Shelly2CameraStatus cached, Shelly2CameraStatus delta) {
        if (cached == null) {
            return delta;
        }
        cached.arm = delta.arm != null ? delta.arm : cached.arm;
        cached.privacy = delta.privacy != null ? delta.privacy : cached.privacy;
        cached.motion = delta.motion != null ? delta.motion : cached.motion;
        cached.streamer = delta.streamer != null ? delta.streamer : cached.streamer;
        return cached;
    }

    public static Map<Integer, String> parseZones(Gson gson, JsonObject config) {
        Map<Integer, String> zones = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
            if (!entry.getKey().startsWith(SHELLY2_CAMERAZONE_COMPONENT_PREFIX) || !entry.getValue().isJsonObject()) {
                continue;
            }
            Shelly2CameraZoneConfig zone = gson.fromJson(entry.getValue(), Shelly2CameraZoneConfig.class);
            if (zone == null || !SHELLY2_CAMERAZONE_TYPE_MOTION.equals(zone.type)
                    || Boolean.FALSE.equals(zone.enable)) {
                continue;
            }
            Integer id = zone.id;
            if (id != null) {
                zones.put(id, getString(zone.name).trim());
            }
        }
        return zones;
    }

    public static boolean applyEvent(Shelly2CameraStatus status, String event) {
        switch (event) {
            case SHELLY2_EVENT_CAMERA_MOTION, SHELLY2_EVENT_CAMERA_MOTION_END:
                status.motion = SHELLY2_EVENT_CAMERA_MOTION.equals(event);
                return true;
            case SHELLY2_EVENT_CAMERA_ARMED, SHELLY2_EVENT_CAMERA_DISARMED:
                status.arm = SHELLY2_EVENT_CAMERA_ARMED.equals(event);
                return true;
            case SHELLY2_EVENT_CAMERA_PRIVACY_ON, SHELLY2_EVENT_CAMERA_PRIVACY_OFF:
                status.privacy = SHELLY2_EVENT_CAMERA_PRIVACY_ON.equals(event);
                return true;
            default:
                return false;
        }
    }

    // Camera.SetConfig accepts a partial config, so only the setting addressed by the channel is sent
    static @Nullable Shelly2CameraConfig buildConfig(String channel, Command command,
            @Nullable Shelly2CameraConfig current) {
        boolean on = command == OnOffType.ON;
        Shelly2CameraConfig config = new Shelly2CameraConfig();
        Shelly2CameraNightVision nightVision = new Shelly2CameraNightVision();
        Shelly2CameraMotion motion = new Shelly2CameraMotion();
        Shelly2CameraAudio audio = new Shelly2CameraAudio();
        switch (channel) {
            case CHANNEL_CAMERA_NIGHT_VISION:
                nightVision.mode = command.toString();
                config.nightVision = nightVision;
                break;
            case CHANNEL_CAMERA_IR_LEDS:
                nightVision.irLeds = on;
                config.nightVision = nightVision;
                break;
            case CHANNEL_CAMERA_MOTION_SENSITIVITY:
                motion.sensitivity = command.toString();
                config.motion = motion;
                break;
            case CHANNEL_CAMERA_RECORD_ON_MOTION:
                motion.recording = newEnable(on);
                config.motion = motion;
                break;
            case CHANNEL_CAMERA_RTSP:
                config.rtsp = newEnable(on);
                break;
            case CHANNEL_MEDIA_SOUNDS:
                config.sounds = newEnable(on);
                break;
            case CHANNEL_MEDIA_MUTE:
                audio.input = newEnable(!on);
                config.audio = audio;
                break;
            case CHANNEL_MEDIA_VOLUME:
                Integer volume = computeVolume(current != null ? getVolume(current) : null, command);
                if (volume == null) {
                    return null;
                }
                Shelly2CameraAudioOutput output = new Shelly2CameraAudioOutput();
                output.volume = volume;
                audio.output = output;
                config.audio = audio;
                break;
            default:
                return null;
        }
        return config;
    }

    static @Nullable Integer computeVolume(@Nullable Integer currentVolume, Command command) {
        if (command instanceof OnOffType) {
            return command == OnOffType.ON ? 100 : 0;
        }
        if (command instanceof IncreaseDecreaseType) {
            int step = command == IncreaseDecreaseType.INCREASE ? VOLUME_STEP : -VOLUME_STEP;
            return clampVolume((currentVolume != null ? currentVolume : 0) + step);
        }
        if (command instanceof DecimalType number) {
            return clampVolume(number.intValue());
        }
        return null;
    }

    private static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }

    private static @Nullable Integer getVolume(Shelly2CameraConfig config) {
        Shelly2CameraAudio audio = config.audio;
        Shelly2CameraAudioOutput output = audio != null ? audio.output : null;
        return output != null ? output.volume : null;
    }

    private static @Nullable Boolean getEnable(@Nullable Shelly2CameraEnable setting) {
        return setting != null ? setting.enable : null;
    }

    private static @Nullable Boolean negate(@Nullable Boolean value) {
        return value != null ? !value : null;
    }

    private static Shelly2CameraEnable newEnable(boolean enable) {
        Shelly2CameraEnable setting = new Shelly2CameraEnable();
        setting.enable = enable;
        return setting;
    }

    private boolean updateSwitch(String group, String channel, @Nullable Boolean value) {
        return value != null && updateChannel(group, channel, OnOffType.from(value));
    }

    private boolean updateString(String channel, @Nullable String value) {
        return value != null && updateChannel(CHANNEL_GROUP_CAMERA, channel, getStringType(value));
    }
}
