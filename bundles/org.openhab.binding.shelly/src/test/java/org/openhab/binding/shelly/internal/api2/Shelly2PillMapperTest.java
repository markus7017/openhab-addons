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

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;

import com.google.gson.Gson;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings({ "null" })
public class Shelly2PillMapperTest {

    private final Gson gson = new Gson();

    private Shelly2GetConfigResult config(String json) {
        return Objects.requireNonNull(gson.fromJson(json, Shelly2GetConfigResult.class));
    }

    private Shelly2DeviceStatusResult status(String json) {
        return Objects.requireNonNull(gson.fromJson(json, Shelly2DeviceStatusResult.class));
    }

    @Test
    public void ssrSwitchesKeepTheirComponentIds() {
        Shelly2GetConfigResult pill = config("{\"switch:200\":{\"id\":200},\"switch:201\":{\"id\":201}}");
        Shelly2GetConfigResult dc = config("{}");

        Shelly2PillMapper.mapConfig(pill, dc);

        assertThat(Shelly2PillMapper.getSwitchIds(pill), is(List.of(200, 201)));
        assertThat(Shelly2PillMapper.getInputIds(pill), is(List.of()));
        assertThat(dc.switch0.id, is(200));
        assertThat(dc.switch1.id, is(201));
        assertThat(dc.switch2, is(nullValue()));
    }

    @Test
    public void digitalIoInputsAreCompacted() {
        Shelly2GetConfigResult pill = config(
                "{\"input:200\":{\"id\":200},\"switch:201\":{\"id\":201},\"input:202\":{\"id\":202}}");
        Shelly2GetConfigResult dc = config("{}");

        Shelly2PillMapper.mapConfig(pill, dc);

        assertThat(Shelly2PillMapper.getInputIds(pill), is(List.of(200, 202)));
        assertThat(Shelly2PillMapper.getSwitchIds(pill), is(List.of(201)));
        assertThat(dc.input0.id, is(200));
        assertThat(dc.input1.id, is(202));
        assertThat(dc.input2, is(nullValue()));
        assertThat(dc.switch1.id, is(201));
    }

    @Test
    public void inputStatusGetsPositionAsId() {
        Shelly2DeviceStatusResult pill = status(
                "{\"input:200\":{\"id\":200,\"state\":false},\"input:202\":{\"id\":202,\"state\":true}}");
        Shelly2DeviceStatusResult ds = status("{}");

        Shelly2PillMapper.mapStatus(pill, ds, List.of(201), List.of(200, 202));

        assertThat(ds.input0.id, is(0));
        assertThat(ds.input0.state, is(false));
        assertThat(ds.input1.id, is(1));
        assertThat(ds.input1.state, is(true));
        assertThat(ds.input2, is(nullValue()));
    }

    @Test
    public void notifyStatusWithoutIdGetsComponentId() {
        Shelly2DeviceStatusResult params = status("{\"switch:201\":{\"output\":true,\"source\":\"HTTP_in\"}}");

        Shelly2PillMapper.mapStatus(params, params, List.of(200, 201), List.of());

        assertThat(params.switch1.id, is(201));
        assertThat(params.switch1.output, is(true));
    }

    @Test
    public void unconfiguredComponentsAreSkipped() {
        Shelly2DeviceStatusResult pill = status(
                "{\"switch:201\":{\"id\":201,\"output\":true},\"input:200\":{\"id\":200,\"state\":true}}");
        Shelly2DeviceStatusResult ds = status("{}");

        Shelly2PillMapper.mapStatus(pill, ds, List.of(), List.of());

        assertThat(ds.switch1, is(nullValue()));
        assertThat(ds.input0, is(nullValue()));
    }

    @Test
    public void sensorsMapToAddonSlots() {
        Shelly2DeviceStatusResult pill = status("{\"temperature:200\":{\"id\":200,\"tC\":21.5},"
                + "\"humidity:200\":{\"id\":200,\"rh\":45.0},\"voltmeter:200\":{\"id\":200,\"voltage\":0.58}}");
        Shelly2DeviceStatusResult ds = status("{}");

        Shelly2PillMapper.mapStatus(pill, ds, List.of(), List.of());

        assertThat(ds.temperature100.tC, is(21.5));
        assertThat(ds.humidity100.rh, is(45.0));
        assertThat(ds.voltmeter100.voltage, is(0.58));
        assertThat(ds.temperature101, is(nullValue()));
    }

    @Test
    public void missingSensorKeepsExistingValue() {
        Shelly2DeviceStatusResult ds = status("{\"voltmeter:100\":{\"id\":100,\"voltage\":1.2}}");

        Shelly2PillMapper.mapStatus(status("{}"), ds, List.of(), List.of());

        assertThat(ds.voltmeter100.voltage, is(1.2));
    }
}
