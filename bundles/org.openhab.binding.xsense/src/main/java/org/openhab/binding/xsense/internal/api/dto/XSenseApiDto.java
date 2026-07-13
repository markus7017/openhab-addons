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
package org.openhab.binding.xsense.internal.api.dto;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;

/**
 * The {@link XSenseApiDto} defines the data transfer objects of the X-Sense cloud API.
 *
 * All fields are nullable boxed types since the API does not guarantee the presence of any field;
 * callers must null-check before use, e.g. via {@link org.openhab.binding.xsense.internal.api.XSenseDtoUtil}.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseApiDto {

    /**
     * Common response envelope of the app API (POST https://api.x-sense-iot.com/app). Every
     * bizCode call returns this envelope; {@code reData} holds the bizCode specific payload.
     */
    public static class ApiResponse {
        /** HTTP-like result code of the app API; 200 signals success. */
        public @Nullable Integer reCode;
        /** Human readable result message, mainly useful for logging on error. */
        public @Nullable String reMsg;
        /** Application specific error code, set when {@link #reCode} is not 200. */
        public @Nullable String errCode;
        /** BizCode specific payload, to be parsed into the matching DTO. */
        public @Nullable JsonElement reData;
    }

    /**
     * Response of bizCode 101001: Cognito client information (unauthenticated call, needed before
     * any login can be performed).
     */
    public static class ClientInfo {
        /** Cognito app client id used for all InitiateAuth/RespondToAuthChallenge calls. */
        public @Nullable String clientId;
        /** Base64 encoded Cognito app client secret with garbage padding, see the API client. */
        public @Nullable String clientSecret;
        /** AWS region hosting the Cognito user pool, e.g. "us-east-1". */
        public @Nullable String cgtRegion;
        /** Cognito user pool id, format "&lt;region&gt;_&lt;poolName&gt;". */
        public @Nullable String userPoolId;
    }

    /**
     * Response of bizCode 101003: temporary AWS credentials for AWS IoT access (shadow REST calls
     * and the upcoming MQTT live-update phase).
     */
    public static class AwsCredentials {
        public @Nullable String accessKeyId;
        public @Nullable String secretAccessKey;
        public @Nullable String sessionToken;
        /** Expiration timestamp of the credentials, "yyyy-MM-dd HH:mm:ss" plus zone offset. */
        public @Nullable String expiration;
    }

    /**
     * Element of the bizCode 102007 response: a house (home) registered in the X-Sense account.
     */
    public static class House {
        /** Technical id of the house, stable across renames; used as the home thing's houseId. */
        public @Nullable String houseId;
        /** Display name as configured in the X-Sense app; may change at any time. */
        public @Nullable String houseName;
        public @Nullable String houseRegion;
        /** AWS IoT region for MQTT shadow access of stations in this house. */
        public @Nullable String mqttRegion;
        /** AWS IoT endpoint host for MQTT shadow access of stations in this house. */
        public @Nullable String mqttServer;
        public @Nullable String loraBand;
        public @Nullable String createTime;
    }

    /**
     * Response of bizCode 103007: base stations of a house including their attached devices.
     */
    public static class StationList {
        public @Nullable List<Station> stations;
        /** Display order of {@link #stations} by stationId, as configured in the X-Sense app. */
        public @Nullable List<String> stationSort;
    }

    /**
     * A base station (e.g. SBS50) as returned by bizCode 103007.
     */
    public static class Station {
        public @Nullable String stationId;
        /** Display name as configured in the X-Sense app; may change at any time. */
        public @Nullable String stationName;
        /** Serial number, stable technical id used as the station thing's representation property. */
        public @Nullable String stationSn;
        /** Station model code, e.g. "SBS50". */
        public @Nullable String category;
        public @Nullable String roomId;
        /** Security mode of the station: "Disarmed", "Home" or "Away" (see XSenseSafeMode). */
        public @Nullable String safeMode;
        /** Online state of the station: 1 = online, other values (or null) = offline/unknown. */
        public @Nullable Integer onLine;
        public @Nullable List<Device> devices;
        /** Display order of {@link #devices} by deviceId, as configured in the X-Sense app. */
        public @Nullable List<String> deviceSort;
    }

    /**
     * A sensor attached to a base station as returned by bizCode 103007.
     */
    public static class Device {
        public @Nullable String deviceId;
        /** Display name as configured in the X-Sense app; may change at any time. */
        public @Nullable String deviceName;
        /** Serial number, stable technical id used as the sensor thing's representation property. */
        public @Nullable String deviceSn;
        /** Device model code, e.g. "XS01-M"; mapped to a thing type via the constants class. */
        public @Nullable String deviceType;
        public @Nullable String roomId;
        /** Name of the room the device is assigned to in the X-Sense app. */
        public @Nullable String roomName;
    }
}
