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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUSCAMERA;

import java.lang.reflect.Field;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudio;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraAudioOutput;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraEnable;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraMotion;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig.Shelly2CameraNightVision;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraStatus;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.provider.ShellyTranslationProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.UnDefType;

import com.google.gson.Gson;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault({})
@SuppressWarnings("null")
public class ShellyCameraHandlerTest {
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
        assertThat(ShellyCameraHandler.mergeStatus(null, delta), is(sameInstance(delta)));
    }

    @Test
    public void mergeStatusKeepsFieldsMissingInDelta() {
        Shelly2DeviceStatusResult result = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class);
        assertNotNull(result);
        Shelly2CameraStatus cached = result.camera0;
        assertNotNull(cached);
        Shelly2CameraStatus delta = new Shelly2CameraStatus();
        delta.motion = false;

        Shelly2CameraStatus merged = ShellyCameraHandler.mergeStatus(cached, delta);

        assertThat(merged, is(sameInstance(cached)));
        assertThat(merged.motion, is(false));
        assertThat(merged.arm, is(true));
        assertThat(merged.privacy, is(false));
        assertThat(merged.streamer, is("running"));
    }

    @Test
    public void applyEventUpdatesMatchingField() {
        Shelly2CameraStatus status = new Shelly2CameraStatus();

        assertTrue(ShellyCameraHandler.applyEvent(status, "motion"));
        assertThat(status.motion, is(true));
        assertTrue(ShellyCameraHandler.applyEvent(status, "motion_end"));
        assertThat(status.motion, is(false));
        assertTrue(ShellyCameraHandler.applyEvent(status, "disarmed"));
        assertThat(status.arm, is(false));
        assertTrue(ShellyCameraHandler.applyEvent(status, "armed"));
        assertThat(status.arm, is(true));
        assertTrue(ShellyCameraHandler.applyEvent(status, "privacy_on"));
        assertThat(status.privacy, is(true));
        assertTrue(ShellyCameraHandler.applyEvent(status, "privacy_off"));
        assertThat(status.privacy, is(false));
    }

    @Test
    public void applyEventIgnoresUnknownEvent() {
        Shelly2CameraStatus status = new Shelly2CameraStatus();
        assertFalse(ShellyCameraHandler.applyEvent(status, "media_ready"));
        assertThat(status.motion, is(nullValue()));
    }

    @Test
    public void computeVolumeHandlesSupportedCommands() {
        assertThat(ShellyCameraHandler.computeVolume(50, new PercentType(30)), is(30));
        assertThat(ShellyCameraHandler.computeVolume(50, OnOffType.ON), is(100));
        assertThat(ShellyCameraHandler.computeVolume(50, OnOffType.OFF), is(0));
        assertThat(ShellyCameraHandler.computeVolume(50, IncreaseDecreaseType.INCREASE), is(60));
        assertThat(ShellyCameraHandler.computeVolume(95, IncreaseDecreaseType.INCREASE), is(100));
        assertThat(ShellyCameraHandler.computeVolume(5, IncreaseDecreaseType.DECREASE), is(0));
        assertThat(ShellyCameraHandler.computeVolume(null, IncreaseDecreaseType.INCREASE), is(10));
        assertThat(ShellyCameraHandler.computeVolume(50, new DecimalType(150)), is(100));
        assertThat(ShellyCameraHandler.computeVolume(50, new StringType("loud")), is(nullValue()));
    }

    @Test
    public void buildConfigInvertsMuteToMicrophoneEnable() {
        Shelly2CameraConfig config = ShellyCameraHandler.buildConfig(CHANNEL_MEDIA_MUTE, OnOffType.ON, null);

        assertThat(config.audio.input.enable, is(false));
        assertThat(config.audio.output, is(nullValue()));
        assertThat(config.led, is(nullValue()));
    }

    @Test
    public void buildConfigStepsVolumeFromCachedValue() {
        Shelly2CameraConfig current = gson.fromJson(CONFIG_JSON, Shelly2GetConfigResult.class).camera0;

        Shelly2CameraConfig config = ShellyCameraHandler.buildConfig(CHANNEL_MEDIA_VOLUME,
                IncreaseDecreaseType.DECREASE, current);

        assertThat(config.audio.output.volume, is(90));
        assertThat(config.audio.input, is(nullValue()));
    }

    @Test
    public void buildConfigRejectsUnsupportedChannelOrCommand() {
        assertThat(ShellyCameraHandler.buildConfig("unknown", OnOffType.ON, null), is(nullValue()));
        assertThat(ShellyCameraHandler.buildConfig(CHANNEL_MEDIA_VOLUME, new StringType("loud"), null),
                is(nullValue()));
    }

    @Test
    public void armedCommandCallsSetCamera() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.handleDeviceCommand(channel(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_ARMED), OnOffType.OFF);

        verify(api).setCamera(false, null);
        verify(api, never()).setCameraConfig(any());
    }

    @Test
    public void rtspCommandSendsPartialConfig() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.handleDeviceCommand(channel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_RTSP), OnOffType.ON);

        verify(api).setCameraConfig(argThat(c -> c.rtsp != null && Boolean.TRUE.equals(c.rtsp.enable) && c.audio == null
                && c.motion == null && c.nightVision == null));
    }

    @Test
    public void playSoundCommandIsForwarded() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.handleDeviceCommand(channel(CHANNEL_GROUP_MEDIA, CHANNEL_MEDIA_PLAY_SOUND), new StringType("alert"));

        verify(api).playCameraSound("alert");
        verify(handler, never()).updateDeviceStatus(any());
    }

    @Test
    public void unsupportedCommandIsIgnored() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.handleDeviceCommand(channel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_STREAMER), new StringType("x"));

        verifyNoInteractions(api);
    }

    @Test
    public void updateDeviceStatusInvertsLedAndMicrophone() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.updateDeviceStatus(profile.status);

        verify(handler).updateChannel(CHANNEL_GROUP_DEV_STATUS, CHANNEL_LED_STATUS_DISABLE, OnOffType.OFF);
        verify(handler).updateChannel(CHANNEL_GROUP_MEDIA, CHANNEL_MEDIA_MUTE, OnOffType.OFF);
        verify(handler).updateChannel(CHANNEL_GROUP_MEDIA, CHANNEL_MEDIA_VOLUME, new PercentType(100));
        verify(handler).updateChannel(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_ARMED, OnOffType.ON);
        verify(handler).updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION, OnOffType.ON);
    }

    @Test
    public void motionTimestampIsUpdatedOnRisingEdgeOnly() throws Exception {
        ShellyCameraHandler handler = createHandler();
        doReturn(OnOffType.OFF).when(handler).getChannelValue(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION);
        handler.updateDeviceStatus(profile.status);

        doReturn(OnOffType.ON).when(handler).getChannelValue(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION);
        handler.updateDeviceStatus(profile.status);

        verify(handler, times(1)).updateChannel(eq(CHANNEL_GROUP_SENSOR), eq(CHANNEL_SENSOR_MOTION_TS),
                any(DateTimeType.class));
    }

    private ShellyApiInterface api = mock(ShellyApiInterface.class);
    private ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUSCAMERA);

    @BeforeAll
    static void initChannelDefinitions() {
        ShellyTranslationProvider messages = mock(ShellyTranslationProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        new ShellyChannelDefinitions(messages);
    }

    private ShellyCameraHandler createHandler() throws Exception {
        ShellyCameraHandler handler = mock(ShellyCameraHandler.class, CALLS_REAL_METHODS);
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID("shelly", "shellypluscamera", "test"));
        profile.cameraConfig = gson.fromJson(CONFIG_JSON, Shelly2GetConfigResult.class).camera0;
        profile.status.camera = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class).camera0;
        Field apiField = ShellyBaseHandler.class.getDeclaredField("api");
        apiField.setAccessible(true);
        apiField.set(handler, api);
        handler.profile = profile;
        doReturn(thing).when(handler).getThing();
        doReturn(false).when(handler).updateThingChannels(any(), any());
        doReturn(true).when(handler).updateChannel(anyString(), anyString(), any());
        doReturn(UnDefType.NULL).when(handler).getChannelValue(anyString(), anyString());
        return handler;
    }

    private static ChannelUID channel(String group, String channel) {
        return new ChannelUID(new ThingUID("shelly", "shellypluscamera", "test"), group, channel);
    }
}
