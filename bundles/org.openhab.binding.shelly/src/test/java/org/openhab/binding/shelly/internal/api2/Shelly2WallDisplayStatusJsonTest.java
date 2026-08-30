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
package org.openhab.binding.shelly.internal.api2;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult.Shelly2DeviceStatusPower;
import org.openhab.binding.shelly.internal.api2.dto.ShellyMediaJsonDTO.Shelly2DeviceStatusMedia;
import org.openhab.binding.shelly.internal.api2.dto.ShellyMediaJsonDTO.Shelly2DeviceStatusMedia.Shelly2DeviceStatusMediaPlayback;
import org.openhab.binding.shelly.internal.api2.dto.ShellyMediaJsonDTO.Shelly2DeviceStatusMedia.Shelly2DeviceStatusMediaPlayback.Shelly2DeviceStatusMediaMeta;
import org.openhab.binding.shelly.internal.api2.dto.ShellyThermostatJsonDTO.Shelly2DeviceStatusThermostat;
import org.openhab.binding.shelly.internal.util.ShellyUtils;

import com.google.gson.Gson;

/**
 * Tests the JSON mapping of the Wall Display specific components of {@link Shelly2DeviceStatusResult}.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class Shelly2WallDisplayStatusJsonTest {

    private final Gson gson = new Gson();

    @Test
    void singletonMediaKeyMapsToMediaStatus() throws ShellyApiException {
        String json = """
                {"media":{"playback":{"enable":true,"buffering":false,"volume":4,"media_type":"RADIO",
                "media_meta":{"title":"Some Song","artist":"Some Artist","album":"Some Album",
                "duration":215000,"position":42000,"thumb":"http://1.2.3.4/thumb.png"}}}}
                """;

        Shelly2DeviceStatusMediaMeta meta = mediaMetaOf(json);
        assertThat(meta.title, is(equalTo("Some Song")));
        assertThat(meta.artist, is(equalTo("Some Artist")));
        assertThat(meta.album, is(equalTo("Some Album")));
    }

    @Test
    void indexedMediaKeyMapsToMediaStatus() throws ShellyApiException {
        String json = """
                {"media:0":{"playback":{"enable":true,"volume":10,"media_type":"AUDIO",
                "media_meta":{"title":"Indexed Song"}}}}
                """;

        Shelly2DeviceStatusMediaMeta meta = mediaMetaOf(json);
        assertThat(meta.title, is(equalTo("Indexed Song")));
    }

    @Test
    void mediaPlaybackAttributesAreMapped() throws ShellyApiException {
        String json = """
                {"media":{"playback":{"enable":true,"buffering":false,"volume":7,"media_type":"RADIO"}}}
                """;

        Shelly2DeviceStatusResult status = ShellyUtils.fromJson(gson, json, Shelly2DeviceStatusResult.class);
        Shelly2DeviceStatusMedia media = status.media;
        assertNotNull(media);
        Shelly2DeviceStatusMediaPlayback playback = media.playback;
        assertNotNull(playback);
        assertThat(playback.enable, is(equalTo(Boolean.TRUE)));
        assertThat(playback.buffering, is(equalTo(Boolean.FALSE)));
        assertThat(playback.volume, is(equalTo(7)));
        assertThat(playback.mediaType, is(equalTo("RADIO")));
    }

    @Test
    void thermostatKeyMapsToThermostatStatus() throws ShellyApiException {
        String json = """
                {"thermostat:0":{"enable":true,"target_C":21.5,"current_C":20.3,"output":true,
                "schedules":{"enable":false}}}
                """;

        Shelly2DeviceStatusResult status = ShellyUtils.fromJson(gson, json, Shelly2DeviceStatusResult.class);
        Shelly2DeviceStatusThermostat thermostat = status.thermostat0;
        assertNotNull(thermostat);
        assertThat(thermostat.enable, is(equalTo(Boolean.TRUE)));
        assertThat(thermostat.targetC, is(equalTo(21.5)));
        assertThat(thermostat.currentC, is(equalTo(20.3)));
        assertThat(thermostat.output, is(equalTo(Boolean.TRUE)));
    }

    @Test
    void thermostatUsageFlagsAndSecondPowerSourceAreMapped() throws ShellyApiException {
        String json = """
                {"relay_in_thermostat":true,"sensor_in_thermostat":false,
                "devicepower:0":{"id":0,"external":{"present":true}},
                "devicepower:1":{"id":1,"battery":{"V":2.9,"percent":74}}}
                """;

        Shelly2DeviceStatusResult status = ShellyUtils.fromJson(gson, json, Shelly2DeviceStatusResult.class);
        assertThat(status.relayInThermostat, is(equalTo(Boolean.TRUE)));
        assertThat(status.sensorInThermostat, is(equalTo(Boolean.FALSE)));

        Shelly2DeviceStatusPower power1 = status.devicepower1;
        assertNotNull(power1);
        assertNotNull(power1.battery);
        assertThat(power1.battery.percent, is(equalTo(74.0)));
    }

    @Test
    void statusWithoutWallDisplayComponentsLeavesFieldsNull() throws ShellyApiException {
        Shelly2DeviceStatusResult status = ShellyUtils.fromJson(gson, "{\"switch:0\":{\"id\":0,\"output\":true}}",
                Shelly2DeviceStatusResult.class);

        assertThat(status.media, is(nullValue()));
        assertThat(status.thermostat0, is(nullValue()));
        assertThat(status.relayInThermostat, is(nullValue()));
        assertThat(status.sensorInThermostat, is(nullValue()));
        assertThat(status.devicepower1, is(nullValue()));
    }

    private Shelly2DeviceStatusMediaMeta mediaMetaOf(String json) throws ShellyApiException {
        Shelly2DeviceStatusResult status = ShellyUtils.fromJson(gson, json, Shelly2DeviceStatusResult.class);
        Shelly2DeviceStatusMedia media = status.media;
        assertNotNull(media);
        Shelly2DeviceStatusMediaPlayback playback = media.playback;
        assertNotNull(playback);
        Shelly2DeviceStatusMediaMeta meta = playback.mediaMeta;
        assertNotNull(meta);
        return meta;
    }
}
