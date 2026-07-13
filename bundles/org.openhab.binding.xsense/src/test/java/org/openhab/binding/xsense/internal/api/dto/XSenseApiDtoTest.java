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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.ApiResponse;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.AwsCredentials;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.ClientInfo;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Device;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.House;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Station;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.StationList;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@NonNullByDefault
public class XSenseApiDtoTest {

    private final Gson gson = new Gson();

    @Test
    public void parseSuccessEnvelope() {
        String json = """
                {"reCode":200,"reMsg":"success","reData":{"clientId":"abc"}}
                """;
        ApiResponse response = gson.fromJson(json, ApiResponse.class);
        assertNotNull(response);
        assertEquals(Integer.valueOf(200), response.reCode);
        assertEquals("success", response.reMsg);
        assertNull(response.errCode);
        assertNotNull(response.reData);
    }

    @Test
    public void parseErrorEnvelope() {
        String json = """
                {"reCode":500,"reMsg":"token expired","errCode":"10000008"}
                """;
        ApiResponse response = gson.fromJson(json, ApiResponse.class);
        assertNotNull(response);
        assertEquals(Integer.valueOf(500), response.reCode);
        assertEquals("10000008", response.errCode);
        assertNull(response.reData);
    }

    @Test
    public void parseClientInfo() {
        String json = """
                {"clientId":"abc123","clientSecret":"c2VjcmV0","cgtRegion":"us-east-1","userPoolId":"us-east-1_pool"}
                """;
        ClientInfo info = gson.fromJson(json, ClientInfo.class);
        assertNotNull(info);
        assertEquals("abc123", info.clientId);
        assertEquals("c2VjcmV0", info.clientSecret);
        assertEquals("us-east-1", info.cgtRegion);
        assertEquals("us-east-1_pool", info.userPoolId);
    }

    @Test
    public void parseHouseList() {
        String json = """
                [{"houseId":"h1","houseName":"Home","mqttRegion":"eu-central-1","mqttServer":"mqtt.example.com"},
                 {"houseId":"h2"}]
                """;
        List<House> houses = gson.fromJson(json, TypeToken.getParameterized(List.class, House.class).getType());
        assertNotNull(houses);
        assertEquals(2, houses.size());
        House first = houses.get(0);
        assertEquals("h1", first.houseId);
        assertEquals("Home", first.houseName);
        assertEquals("mqtt.example.com", first.mqttServer);
        House second = houses.get(1);
        assertEquals("h2", second.houseId);
        assertNull(second.houseName);
    }

    @Test
    public void parseStationListWithDevices() {
        String json = """
                {"stations":[{"stationSn":"SN123","stationName":"Hallway","category":"SBS50","onLine":1,
                  "devices":[{"deviceSn":"DEV1","deviceName":"Kitchen","deviceType":"XS01-M","roomName":"Kitchen"},
                             {"deviceSn":"DEV2","deviceType":"STH51"}]}]}
                """;
        StationList stationList = gson.fromJson(json, StationList.class);
        assertNotNull(stationList);
        List<Station> stations = stationList.stations;
        assertNotNull(stations);
        assertEquals(1, stations.size());
        Station station = stations.get(0);
        assertEquals("SN123", station.stationSn);
        assertEquals("SBS50", station.category);
        assertEquals(Integer.valueOf(1), station.onLine);
        List<Device> devices = station.devices;
        assertNotNull(devices);
        assertEquals(2, devices.size());
        Device first = devices.get(0);
        assertEquals("DEV1", first.deviceSn);
        assertEquals("XS01-M", first.deviceType);
        assertEquals("Kitchen", first.roomName);
        Device second = devices.get(1);
        assertEquals("DEV2", second.deviceSn);
        assertNull(second.deviceName);
        assertNull(second.roomName);
    }

    @Test
    public void parseEmptyStationList() {
        StationList stationList = gson.fromJson("{}", StationList.class);
        assertNotNull(stationList);
        assertNull(stationList.stations);
    }

    @Test
    public void parseStationWithSafeModeAndSecurityDevices() {
        String json = """
                {"stations":[{"stationSn":"SN123","category":"SBS50","onLine":1,"safeMode":"Home",
                  "devices":[{"deviceSn":"DEV1","deviceType":"SDS0A"},
                             {"deviceSn":"DEV2","deviceType":"SMS0A"},
                             {"deviceSn":"DEV3","deviceType":"SKP0A"},
                             {"deviceSn":"DEV4","deviceType":"SAL100"}]}]}
                """;
        StationList stationList = gson.fromJson(json, StationList.class);
        assertNotNull(stationList);
        List<Station> stations = stationList.stations;
        assertNotNull(stations);
        Station station = stations.get(0);
        assertEquals("Home", station.safeMode);
        List<Device> devices = station.devices;
        assertNotNull(devices);
        assertEquals(4, devices.size());
        assertEquals("SDS0A", devices.get(0).deviceType);
        assertEquals("SKP0A", devices.get(2).deviceType);
    }

    @Test
    public void parseStationWithoutSafeMode() {
        String json = """
                {"stations":[{"stationSn":"SN123"}]}
                """;
        StationList stationList = gson.fromJson(json, StationList.class);
        assertNotNull(stationList);
        List<Station> stations = stationList.stations;
        assertNotNull(stations);
        assertNull(stations.get(0).safeMode);
    }

    @Test
    public void parseAwsCredentials() {
        String json = """
                {"accessKeyId":"ASIAEXAMPLE","secretAccessKey":"secret/key","sessionToken":"token==",
                 "expiration":"2026-07-12 10:15:00+00:00"}
                """;
        AwsCredentials credentials = gson.fromJson(json, AwsCredentials.class);
        assertNotNull(credentials);
        assertEquals("ASIAEXAMPLE", credentials.accessKeyId);
        assertEquals("secret/key", credentials.secretAccessKey);
        assertEquals("token==", credentials.sessionToken);
        assertEquals("2026-07-12 10:15:00+00:00", credentials.expiration);
    }

    @Test
    public void parseIncompleteAwsCredentials() {
        AwsCredentials credentials = gson.fromJson("{\"accessKeyId\":\"ASIAEXAMPLE\"}", AwsCredentials.class);
        assertNotNull(credentials);
        assertEquals("ASIAEXAMPLE", credentials.accessKeyId);
        assertNull(credentials.secretAccessKey);
        assertNull(credentials.sessionToken);
        assertNull(credentials.expiration);
    }
}
