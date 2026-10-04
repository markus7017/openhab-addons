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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudio;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudioOutput;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraEnable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraMotion;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraNightVision;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraStatus;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;

/**
 * The {@link ShellyCamera} implements status mapping, channel updates and command handling for the Shelly Camera
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyCamera {
    private static final int VOLUME_STEP = 10;

    private ShellyCamera() {
    }

    /**
     * NotifyStatus only carries the changed fields, so they are merged into the cached status instead of replacing it
     *
     * @return the status to keep in the cache
     */
    public static Shelly2CameraStatus mergeStatus(@Nullable Shelly2CameraStatus cached, Shelly2CameraStatus delta) {
        if (cached == null) {
            return delta;
        }
        cached.arm = delta.arm != null ? delta.arm : cached.arm;
        cached.privacy = delta.privacy != null ? delta.privacy : cached.privacy;
        cached.motion = delta.motion != null ? delta.motion : cached.motion;
        cached.streamer = delta.streamer != null ? delta.streamer : cached.streamer;
        cached.streams = delta.streams != null ? delta.streams : cached.streams;
        cached.errors = delta.errors != null ? delta.errors : cached.errors;
        return cached;
    }

    /**
     * @return true if the event is a known camera event and has been applied to the status
     */
    public static boolean applyEvent(Shelly2CameraStatus status, String event) {
        switch (event) {
            case SHELLY2_EVENT_CAMERA_MOTION:
            case SHELLY2_EVENT_CAMERA_MOTION_END:
                status.motion = SHELLY2_EVENT_CAMERA_MOTION.equals(event);
                return true;
            case SHELLY2_EVENT_CAMERA_ARMED:
            case SHELLY2_EVENT_CAMERA_DISARMED:
                status.arm = SHELLY2_EVENT_CAMERA_ARMED.equals(event);
                return true;
            case SHELLY2_EVENT_CAMERA_PRIVACY_ON:
            case SHELLY2_EVENT_CAMERA_PRIVACY_OFF:
                status.privacy = SHELLY2_EVENT_CAMERA_PRIVACY_ON.equals(event);
                return true;
            default:
                return false;
        }
    }

    public static boolean updateChannels(ShellyThingInterface thingHandler, ShellySettingsStatus status) {
        ShellyDeviceProfile profile = thingHandler.getProfile();
        if (!profile.isCamera) {
            return false;
        }
        Shelly2CameraStatus cs = status.camera;
        thingHandler.updateThingChannels(Map.of(),
                ShellyChannelDefinitions.createCameraChannels(thingHandler.getThing(), profile, cs));

        boolean updated = false;
        if (cs != null) {
            updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_ARMED, cs.arm);
            updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_PRIVACY, cs.privacy);
            updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_MOTION, cs.motion);
            updated |= updateString(thingHandler, CHANNEL_CAMERA_STREAMER, cs.streamer);
        }

        Shelly2CameraConfig config = profile.cameraConfig;
        if (config == null) {
            return updated;
        }
        Shelly2CameraNightVision nightVision = config.nightVision;
        if (nightVision != null) {
            updated |= updateString(thingHandler, CHANNEL_CAMERA_NIGHT_VISION, nightVision.mode);
            updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_IR_LEDS, nightVision.irLeds);
        }
        Shelly2CameraMotion motion = config.motion;
        if (motion != null) {
            updated |= updateString(thingHandler, CHANNEL_CAMERA_MOTION_SENSITIVITY, motion.sensitivity);
            updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_RECORD_ON_MOTION, getEnable(motion.recording));
        }
        updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_LED, getEnable(config.led));
        updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_SOUNDS, getEnable(config.sounds));
        updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_RTSP, getEnable(config.rtsp));
        Shelly2CameraAudio audio = config.audio;
        if (audio != null) {
            Boolean micEnabled = getEnable(audio.input);
            updated |= updateSwitch(thingHandler, CHANNEL_CAMERA_MIC_MUTED, micEnabled != null ? !micEnabled : null);
            Integer volume = getVolume(audio);
            if (volume != null) {
                updated |= thingHandler.updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_VOLUME,
                        new PercentType(clampVolume(volume)));
            }
        }
        return updated;
    }

    public static void handleCommand(ShellyThingInterface thingHandler, String channelId, Command command)
            throws ShellyApiException {
        ShellyDeviceProfile profile = thingHandler.getProfile();
        ShellyApiInterface api = thingHandler.getApi();
        boolean on = command == OnOffType.ON;
        Shelly2CameraConfig config = new Shelly2CameraConfig();
        switch (channelId) {
            case CHANNEL_CAMERA_ARMED:
                api.setCamera(on, null);
                break;
            case CHANNEL_CAMERA_PRIVACY:
                api.setCamera(null, on);
                break;
            case CHANNEL_CAMERA_PLAY_SOUND:
                String sound = getString(command);
                if (!sound.isEmpty()) {
                    api.playCameraSound(sound);
                }
                return;
            case CHANNEL_CAMERA_NIGHT_VISION:
                Shelly2CameraNightVision mode = new Shelly2CameraNightVision();
                mode.mode = getString(command);
                config.nightVision = mode;
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_IR_LEDS:
                Shelly2CameraNightVision irLeds = new Shelly2CameraNightVision();
                irLeds.irLeds = on;
                config.nightVision = irLeds;
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_MOTION_SENSITIVITY:
                Shelly2CameraMotion sensitivity = new Shelly2CameraMotion();
                sensitivity.sensitivity = getString(command);
                config.motion = sensitivity;
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_RECORD_ON_MOTION:
                Shelly2CameraMotion recording = new Shelly2CameraMotion();
                recording.recording = newEnable(on);
                config.motion = recording;
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_LED:
                config.led = newEnable(on);
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_SOUNDS:
                config.sounds = newEnable(on);
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_RTSP:
                config.rtsp = newEnable(on);
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_MIC_MUTED:
                Shelly2CameraAudio input = new Shelly2CameraAudio();
                input.input = newEnable(!on);
                config.audio = input;
                api.setCameraConfig(config);
                break;
            case CHANNEL_CAMERA_VOLUME:
                Shelly2CameraConfig current = profile.cameraConfig;
                Shelly2CameraAudio currentAudio = current != null ? current.audio : null;
                Integer volume = computeVolume(currentAudio != null ? getVolume(currentAudio) : null, command);
                if (volume == null) {
                    return;
                }
                Shelly2CameraAudioOutput output = new Shelly2CameraAudioOutput();
                output.volume = volume;
                Shelly2CameraAudio audio = new Shelly2CameraAudio();
                audio.output = output;
                config.audio = audio;
                api.setCameraConfig(config);
                break;
            default:
                return;
        }
        updateChannels(thingHandler, profile.status);
    }

    /**
     * @return the new volume (0..100) for the given command, null if the command is not supported
     */
    public static @Nullable Integer computeVolume(@Nullable Integer currentVolume, Command command) {
        if (command instanceof PercentType percent) {
            return percent.intValue();
        }
        if (command instanceof OnOffType) {
            return command == OnOffType.ON ? 100 : 0;
        }
        if (command instanceof IncreaseDecreaseType) {
            int current = currentVolume != null ? currentVolume : 0;
            int step = command == IncreaseDecreaseType.INCREASE ? VOLUME_STEP : -VOLUME_STEP;
            return clampVolume(current + step);
        }
        if (command instanceof DecimalType number) {
            return clampVolume(number.intValue());
        }
        return null;
    }

    private static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }

    private static @Nullable Integer getVolume(Shelly2CameraAudio audio) {
        Shelly2CameraAudioOutput output = audio.output;
        return output != null ? output.volume : null;
    }

    private static @Nullable Boolean getEnable(@Nullable Shelly2CameraEnable setting) {
        return setting != null ? setting.enable : null;
    }

    private static Shelly2CameraEnable newEnable(boolean enable) {
        Shelly2CameraEnable setting = new Shelly2CameraEnable();
        setting.enable = enable;
        return setting;
    }

    private static boolean updateSwitch(ShellyThingInterface thingHandler, String channel, @Nullable Boolean value) {
        return value != null && thingHandler.updateChannel(CHANNEL_GROUP_CAMERA, channel, OnOffType.from(value));
    }

    private static boolean updateString(ShellyThingInterface thingHandler, String channel, @Nullable String value) {
        return value != null && thingHandler.updateChannel(CHANNEL_GROUP_CAMERA, channel, getStringType(value));
    }
}
