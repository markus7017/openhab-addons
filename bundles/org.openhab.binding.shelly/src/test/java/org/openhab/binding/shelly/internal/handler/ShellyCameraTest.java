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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudio;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudioOutput;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraEnable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraMotion;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraNightVision;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraStatus;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;

import com.google.gson.Gson;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyCameraTest {
    private static final String CONFIG_JSON = """
            {"camera:0":{"id":0,"name":null,"led":{"enable":true},"audio":{"input":{"enable":true},"output":{"volume":100}},
            "sounds":{"enable":true},"motion":{"sensitivity":"medium","recording":{"enable":true}},
            "night_vision":{"mode":"auto","ir_leds":true,"light_threshold":50,"sensitivity":50},"rtsp":{"enable":false},
            "video":{"brightness":50,"contrast":50,"flip":false,"mirror":false,"saturation":50,"sharpness":50,"tint":0,
            "temperature":0,"antiflicker":"50Hz"},"streams":{"0":{"bitrate":2048,"resolution":"1280x720@25"},
            "1":{"bitrate":512,"resolution":"640x360@10"}}}}
            """;
    private static final String STATUS_JSON = """
            {"camera:0":{"id":0,"arm":true,"privacy":false,"streamer":"running","motion":true,"streams":0,
            "recording_encryption":{"configured":false},"streamer_version":"115"}}
            """;

    private final Gson gson = new Gson();

    @Test
    public void configIsParsedFromRealDevicePayload() {
        Shelly2GetConfigResult result = gson.fromJson(CONFIG_JSON, Shelly2GetConfigResult.class);
        assertNotNull(result);
        Shelly2CameraConfig config = result.camera0;
        assertNotNull(config);

        Shelly2CameraEnable led = config.led;
        assertNotNull(led);
        assertThat(led.enable, is(true));
        Shelly2CameraAudio audio = config.audio;
        assertNotNull(audio);
        Shelly2CameraEnable input = audio.input;
        assertNotNull(input);
        assertThat(input.enable, is(true));
        Shelly2CameraAudioOutput output = audio.output;
        assertNotNull(output);
        assertThat(output.volume, is(100));
        Shelly2CameraMotion motion = config.motion;
        assertNotNull(motion);
        assertThat(motion.sensitivity, is("medium"));
        Shelly2CameraNightVision nightVision = config.nightVision;
        assertNotNull(nightVision);
        assertThat(nightVision.mode, is("auto"));
        assertThat(nightVision.irLeds, is(true));
        Shelly2CameraEnable rtsp = config.rtsp;
        assertNotNull(rtsp);
        assertThat(rtsp.enable, is(false));
    }

    @Test
    public void statusIsParsedFromRealDevicePayload() {
        Shelly2DeviceStatusResult result = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class);
        assertNotNull(result);
        Shelly2CameraStatus status = result.camera0;
        assertNotNull(status);
        assertThat(status.arm, is(true));
        assertThat(status.privacy, is(false));
        assertThat(status.motion, is(true));
        assertThat(status.streamer, is("running"));
        assertThat(status.streams, is(0));
    }

    @Test
    public void mergeStatusWithoutCacheReturnsDelta() {
        Shelly2CameraStatus delta = new Shelly2CameraStatus();
        assertThat(ShellyCamera.mergeStatus(null, delta), is(sameInstance(delta)));
    }

    @Test
    public void mergeStatusKeepsFieldsMissingInDelta() {
        Shelly2DeviceStatusResult result = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class);
        assertNotNull(result);
        Shelly2CameraStatus cached = result.camera0;
        assertNotNull(cached);
        Shelly2CameraStatus delta = new Shelly2CameraStatus();
        delta.motion = false;

        Shelly2CameraStatus merged = ShellyCamera.mergeStatus(cached, delta);

        assertThat(merged, is(sameInstance(cached)));
        assertThat(merged.motion, is(false));
        assertThat(merged.arm, is(true));
        assertThat(merged.privacy, is(false));
        assertThat(merged.streamer, is("running"));
    }

    @Test
    public void applyEventUpdatesMatchingField() {
        Shelly2CameraStatus status = new Shelly2CameraStatus();

        assertTrue(ShellyCamera.applyEvent(status, "motion"));
        assertThat(status.motion, is(true));
        assertTrue(ShellyCamera.applyEvent(status, "motion_end"));
        assertThat(status.motion, is(false));
        assertTrue(ShellyCamera.applyEvent(status, "disarmed"));
        assertThat(status.arm, is(false));
        assertTrue(ShellyCamera.applyEvent(status, "armed"));
        assertThat(status.arm, is(true));
        assertTrue(ShellyCamera.applyEvent(status, "privacy_on"));
        assertThat(status.privacy, is(true));
        assertTrue(ShellyCamera.applyEvent(status, "privacy_off"));
        assertThat(status.privacy, is(false));
    }

    @Test
    public void applyEventIgnoresUnknownEvent() {
        Shelly2CameraStatus status = new Shelly2CameraStatus();
        assertFalse(ShellyCamera.applyEvent(status, "media_ready"));
        assertThat(status.motion, is(nullValue()));
    }

    @Test
    public void computeVolumeHandlesSupportedCommands() {
        assertThat(ShellyCamera.computeVolume(50, new PercentType(30)), is(30));
        assertThat(ShellyCamera.computeVolume(50, OnOffType.ON), is(100));
        assertThat(ShellyCamera.computeVolume(50, OnOffType.OFF), is(0));
        assertThat(ShellyCamera.computeVolume(50, IncreaseDecreaseType.INCREASE), is(60));
        assertThat(ShellyCamera.computeVolume(95, IncreaseDecreaseType.INCREASE), is(100));
        assertThat(ShellyCamera.computeVolume(5, IncreaseDecreaseType.DECREASE), is(0));
        assertThat(ShellyCamera.computeVolume(null, IncreaseDecreaseType.INCREASE), is(10));
        assertThat(ShellyCamera.computeVolume(50, new DecimalType(150)), is(100));
        assertThat(ShellyCamera.computeVolume(50, new StringType("loud")), is(nullValue()));
    }
}
