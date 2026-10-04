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
package org.openhab.binding.shelly.internal.api2.dto;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@link ShellyCameraJsonDTO} includes constants and structures used for the Shelly Camera's JSON mapping and
 * processing (Camera component).
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyCameraJsonDTO {
    public static final String SHELLYRPC_METHOD_CAMERA_SET = "Camera.Set";
    public static final String SHELLYRPC_METHOD_CAMERA_GETCONFIG = "Camera.GetConfig";
    public static final String SHELLYRPC_METHOD_CAMERA_SETCONFIG = "Camera.SetConfig";
    public static final String SHELLYRPC_METHOD_CAMERA_PLAYSOUND = "Camera.PlaySound";

    public static final String SHELLY2_CAMERA_COMPONENT_PREFIX = "camera:";

    public static final String SHELLY2_EVENT_CAMERA_MOTION = "motion";
    public static final String SHELLY2_EVENT_CAMERA_MOTION_END = "motion_end";
    public static final String SHELLY2_EVENT_CAMERA_ARMED = "armed";
    public static final String SHELLY2_EVENT_CAMERA_DISARMED = "disarmed";
    public static final String SHELLY2_EVENT_CAMERA_PRIVACY_ON = "privacy_on";
    public static final String SHELLY2_EVENT_CAMERA_PRIVACY_OFF = "privacy_off";

    public static final String SHELLY2_CAMERA_RTSP_STREAM_MAIN = "/stream/0";
    public static final String SHELLY2_CAMERA_RTSP_STREAM_SUB = "/stream/1";

    public static class Shelly2CameraStatus {
        public @Nullable Integer id;
        public @Nullable Boolean arm;
        public @Nullable Boolean privacy;
        public @Nullable Boolean motion;
        public @Nullable String streamer; // stopped|starting|running|stopping|unknown
        public @Nullable Integer streams; // number of active streams
        public String @Nullable [] errors;
    }

    public static class Shelly2CameraConfig {
        public static class Shelly2CameraEnable {
            public @Nullable Boolean enable;
        }

        public static class Shelly2CameraAudioOutput {
            public @Nullable Integer volume; // 0..100
        }

        public static class Shelly2CameraAudio {
            public @Nullable Shelly2CameraEnable input;
            public @Nullable Shelly2CameraAudioOutput output;
        }

        public static class Shelly2CameraMotion {
            public @Nullable String sensitivity; // low|medium|high
            public @Nullable Shelly2CameraEnable recording;
        }

        public static class Shelly2CameraNightVision {
            public @Nullable String mode; // auto|day|night
            @SerializedName("ir_leds")
            public @Nullable Boolean irLeds;
        }

        public @Nullable Integer id;
        public @Nullable String name;
        public @Nullable Shelly2CameraEnable led;
        public @Nullable Shelly2CameraAudio audio;
        public @Nullable Shelly2CameraEnable sounds;
        public @Nullable Shelly2CameraMotion motion;
        @SerializedName("night_vision")
        public @Nullable Shelly2CameraNightVision nightVision;
        public @Nullable Shelly2CameraEnable rtsp;
    }

    public static class Shelly2CameraSetParams {
        public int id;
        public @Nullable Boolean arm;
        public @Nullable Boolean privacy;
    }

    public static class Shelly2CameraSetConfigParams {
        public int id;
        public @Nullable Shelly2CameraConfig config;
    }

    public static class Shelly2CameraPlaySoundParams {
        public int id;
        public @Nullable String sound; // alert|ding-dong|notification
    }
}
