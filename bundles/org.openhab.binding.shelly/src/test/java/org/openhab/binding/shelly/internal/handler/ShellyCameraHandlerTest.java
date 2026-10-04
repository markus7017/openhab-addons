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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUSCAMERA;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraConfig;
import org.openhab.binding.shelly.internal.api2.dto.ShellyCameraJsonDTO.Shelly2CameraStatus;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.provider.ShellyTranslationProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.UnDefType;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

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
    private static final String ZONES_JSON = """
            {"camera:0":{"id":0},"camerazone:200":{"id":200,"enable":true,"type":"motion","name":"Entrance"},
            "camerazone:201":{"id":201,"enable":true,"type":"privacy","name":"Window"},
            "camerazone:202":{"id":202,"enable":false,"type":"motion","name":"Off"},
            "camerazone:203":{"id":203,"enable":true,"type":"motion"}}
            """;

    private final Gson gson = new Gson();

    @Test
    public void mergeStatusKeepsFieldsMissingInDelta() {
        Shelly2CameraStatus cached = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class).camera0;
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
        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_NIGHT_VISION, new StringType("auto"));
        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_IR_LEDS, OnOffType.ON);
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
        verify(handler, times(1)).updateChannel(eq(CHANNEL_GROUP_CAMERA), eq(CHANNEL_CAMERA_LAST_EVENT_IMAGE),
                any(RawType.class));
    }

    @Test
    public void parseZonesKeepsEnabledMotionZonesOnly() {
        Map<Integer, String> zones = ShellyCameraHandler.parseZones(gson,
                JsonParser.parseString(ZONES_JSON).getAsJsonObject());

        assertThat(zones, is(Map.of(200, "Entrance", 203, "")));
    }

    @Test
    public void createCameraChannelsAddsZoneChannelsLabelledByName() {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID("shelly", "shellypluscamera", "test"));
        Shelly2CameraStatus status = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class).camera0;

        Map<String, Channel> channels = ShellyChannelDefinitions.createCameraChannels(thing, null, status,
                Map.of(200, "Entrance", 203, ""));

        assertThat(channels.get("zones#motion200").getLabel(), is("Entrance"));
        assertTrue(channels.get("zones#motion203").getLabel().endsWith(" 203"));
        assertTrue(channels.containsKey("camera#lastEventZone"));
        assertTrue(channels.containsKey("camera#snapshot"));
        assertFalse(ShellyChannelDefinitions.createCameraChannels(thing, null, status, Map.of())
                .containsKey("camera#lastEventZone"));
    }

    @Test
    public void activityChannelsKeepTheirOwnDefinition() {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID("shelly", "shellypluscamera", "test"));
        Shelly2CameraStatus status = gson.fromJson(STATUS_JSON, Shelly2DeviceStatusResult.class).camera0;

        Map<String, Channel> channels = ShellyChannelDefinitions.createCameraChannels(thing, null, status,
                Map.of(200, "Entrance"));

        assertThat(channels.get("camera#lastEvent").getChannelTypeUID().getId(), is("cameraLastEvent"));
        assertThat(channels.get("camera#lastEventTimestamp").getAcceptedItemType(), is("DateTime"));
        assertThat(channels.get("camera#lastEventZone").getChannelTypeUID().getId(), is("cameraLastEventZone"));
        assertThat(channels.get("camera#lastEventImage").getAcceptedItemType(), is("Image"));
        assertThat(channels.get("camera#takeSnapshot").getAcceptedItemType(), is("Switch"));
    }

    @Test
    public void takeSnapshotFetchesImageAndResetsSwitch() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.handleDeviceCommand(channel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_TAKE_SNAPSHOT), OnOffType.ON);

        verify(handler).updateChannel(eq(CHANNEL_GROUP_CAMERA), eq(CHANNEL_CAMERA_SNAPSHOT), any(RawType.class));
        verify(handler).updateChannel("camera#takeSnapshot", OnOffType.ON, true);
        verify(handler).updateChannel("camera#takeSnapshot", OnOffType.OFF, true);
        verify(handler, never()).updateDeviceStatus(any());
    }

    @Test
    public void zoneMotionUpdatesZoneAndLastEventZone() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.onCameraZoneEvent(200, "motion");

        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA_ZONES, "motion200", OnOffType.ON);
        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_ZONE, new StringType("Entrance"));
    }

    @Test
    public void zoneMotionEndOnlyClearsZone() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.onCameraZoneEvent(200, "motion_end");

        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA_ZONES, "motion200", OnOffType.OFF);
        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_CAMERA), eq(CHANNEL_CAMERA_LAST_EVENT_ZONE), any());
    }

    @Test
    public void motionStatusChangeSetsLastEventAndTriggers() throws Exception {
        ShellyCameraHandler handler = createHandler();
        doReturn(OnOffType.OFF).when(handler).getChannelValue(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION);

        handler.updateDeviceStatus(profile.status);

        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT, new StringType("MOTION"));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_CAMERA), eq(CHANNEL_CAMERA_LAST_EVENT_TS),
                any(DateTimeType.class));
        verify(handler).triggerChannel(CHANNEL_GROUP_SENSOR, CHANNEL_EVENT_TRIGGER, "MOTION");
        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_CAMERA), eq(CHANNEL_CAMERA_LAST_EVENT_ZONE), any());
    }

    @Test
    public void disarmStatusChangeClearsLastEventZone() throws Exception {
        ShellyCameraHandler handler = createHandler();
        doReturn(OnOffType.ON).when(handler).getChannelValue(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION);
        doReturn(OnOffType.ON).when(handler).getChannelValue(CHANNEL_GROUP_CONTROL, CHANNEL_CAMERA_ARMED);
        profile.status.camera.arm = false;

        handler.updateDeviceStatus(profile.status);

        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT, new StringType("DISARMED"));
        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_ZONE, UnDefType.UNDEF);
        verify(handler).triggerChannel(CHANNEL_GROUP_SENSOR, CHANNEL_EVENT_TRIGGER, "DISARMED");
    }

    @Test
    public void initialStatusDoesNotRaiseEvents() throws Exception {
        ShellyCameraHandler handler = createHandler();

        handler.updateDeviceStatus(profile.status);

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_CAMERA), eq(CHANNEL_CAMERA_LAST_EVENT), any());
        verify(handler, never()).triggerChannel(anyString(), anyString(), anyString());
    }

    @Test
    public void motionSnapshotUpdatesBothImageChannels() throws Exception {
        ShellyCameraHandler handler = createHandler();
        when(api.getCameraSnapshot()).thenReturn(new byte[] { 1, 2, 3 });

        handler.refreshSnapshot(true);
        handler.refreshSnapshot(false);

        RawType image = new RawType(new byte[] { 1, 2, 3 }, "image/jpeg");
        verify(handler, times(2)).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_SNAPSHOT, image);
        verify(handler, times(1)).updateChannel(CHANNEL_GROUP_CAMERA, CHANNEL_CAMERA_LAST_EVENT_IMAGE, image);
    }

    @Test
    public void snapshotIsSkippedInPrivacyMode() throws Exception {
        ShellyCameraHandler handler = createHandler();
        profile.status.camera.privacy = true;

        handler.refreshSnapshot(true);

        verify(api, never()).getCameraSnapshot();
    }

    @Test
    public void motionEndResetsZoneChannels() throws Exception {
        ShellyCameraHandler handler = createHandler();
        profile.status.camera.motion = false;

        handler.updateDeviceStatus(profile.status);

        verify(handler).updateChannel(CHANNEL_GROUP_CAMERA_ZONES, "motion200", OnOffType.OFF);
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
        profile.cameraZones = Map.of(200, "Entrance");
        Field apiField = ShellyBaseHandler.class.getDeclaredField("api");
        apiField.setAccessible(true);
        apiField.set(handler, api);
        when(api.getCameraSnapshot()).thenReturn(new byte[] { 1 });
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        doAnswer(i -> {
            ((Runnable) i.getArgument(0)).run();
            return null;
        }).when(scheduler).execute(any());
        Field schedulerField = BaseThingHandler.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(handler, scheduler);
        handler.profile = profile;
        doReturn(thing).when(handler).getThing();
        doReturn(false).when(handler).updateThingChannels(any(), any());
        doReturn(true).when(handler).updateChannel(anyString(), anyString(), any());
        doReturn(true).when(handler).updateChannel(anyString(), any(), anyBoolean());
        doReturn(UnDefType.NULL).when(handler).getChannelValue(anyString(), anyString());
        doNothing().when(handler).triggerChannel(anyString(), anyString(), anyString());
        return handler;
    }

    private static ChannelUID channel(String group, String channel) {
        return new ChannelUID(new ThingUID("shelly", "shellypluscamera", "test"), group, channel);
    }
}
