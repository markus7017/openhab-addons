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
import static org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.SHELLY2_EVENT_CFGCHANGED;
import static org.openhab.binding.shelly.internal.api2.Shelly2PillMapper.getComponentKey;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyPillJsonDTO.*;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2NotifyEvent;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2NotifyEventData;

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
    public void digitalIoConfigKeepsSwitchIdAndCompactsInputs() {
        Shelly2GetConfigResult pill = config(
                "{\"input:200\":{\"id\":200},\"switch:201\":{\"id\":201},\"input:202\":{\"id\":202}}");
        Shelly2GetConfigResult dc = config("{}");

        Shelly2PillMapper.mapConfig(pill, dc);

        assertThat(dc.switch1.id, is(SHELLY2_PILL_ID + 1));
        assertThat(dc.input0.id, is(SHELLY2_PILL_ID));
        assertThat(dc.input1.id, is(SHELLY2_PILL_ID + 2));
        assertThat(dc.input2, is(nullValue()));
    }

    @Test
    public void inputStatusAndEventGetPositionAsId() {
        List<String> components = List.of(getComponentKey(SHELLY2_PILL_KEY_INPUT, 0),
                getComponentKey(SHELLY2_PILL_KEY_INPUT, 2), getComponentKey(SHELLY2_PILL_KEY_SWITCH, 1));
        Shelly2DeviceStatusResult pill = status(
                "{\"input:200\":{\"id\":200,\"state\":false},\"input:202\":{\"id\":202,\"state\":true}}");
        Shelly2DeviceStatusResult ds = status("{}");

        Shelly2PillMapper.mapStatus(pill, ds, components);

        assertThat(ds.input0.id, is(0));
        assertThat(ds.input1.id, is(1));
        assertThat(ds.input1.state, is(true));
        assertThat(Shelly2PillMapper.getInputIndex(components, getComponentKey(SHELLY2_PILL_KEY_INPUT, 2)), is(1));
        assertThat(Shelly2PillMapper.getInputIndex(components, getComponentKey(SHELLY2_PILL_KEY_INPUT, 1)), is(-1));
    }

    @Test
    public void inputEventsGetPositionAndUnconfiguredInputsAreRemoved() {
        String input0 = getComponentKey(SHELLY2_PILL_KEY_INPUT, 0);
        String input1 = getComponentKey(SHELLY2_PILL_KEY_INPUT, 1);
        String input2 = getComponentKey(SHELLY2_PILL_KEY_INPUT, 2);
        String switch1 = getComponentKey(SHELLY2_PILL_KEY_SWITCH, 1);
        List<String> components = List.of(input0, input2, switch1);
        String json = "{\"events\":[{\"component\":\"" + input2 + "\",\"id\":" + (SHELLY2_PILL_ID + 2)
                + "},{\"component\":\"" + input1 + "\",\"id\":" + (SHELLY2_PILL_ID + 1) + "},{\"component\":\""
                + switch1 + "\",\"id\":" + (SHELLY2_PILL_ID + 1) + "}]}";
        List<Shelly2NotifyEvent> events = Objects
                .requireNonNull(Objects.requireNonNull(gson.fromJson(json, Shelly2NotifyEventData.class)).events);

        Shelly2PillMapper.mapInputEvents(events, components);

        assertThat(events.size(), is(2));
        assertThat(events.get(0).component, is(input2));
        assertThat(events.get(0).id, is(1));
        assertThat(events.get(1).component, is(switch1));
        assertThat(events.get(1).id, is(SHELLY2_PILL_ID + 1));
    }

    @Test
    public void configChangedOfUnconfiguredInputIsKept() {
        String input1 = getComponentKey(SHELLY2_PILL_KEY_INPUT, 1);
        String json = "{\"events\":[{\"component\":\"" + input1 + "\",\"id\":" + (SHELLY2_PILL_ID + 1) + ",\"event\":\""
                + SHELLY2_EVENT_CFGCHANGED + "\"}]}";
        List<Shelly2NotifyEvent> events = Objects
                .requireNonNull(Objects.requireNonNull(gson.fromJson(json, Shelly2NotifyEventData.class)).events);

        Shelly2PillMapper.mapInputEvents(events, List.of(getComponentKey(SHELLY2_PILL_KEY_INPUT, 0)));

        assertThat(events.size(), is(1));
        assertThat(events.get(0).event, is(SHELLY2_EVENT_CFGCHANGED));
    }

    @Test
    public void notifyStatusWithoutIdGetsComponentId() {
        Shelly2DeviceStatusResult params = status("{\"switch:201\":{\"output\":true}}");

        Shelly2PillMapper.mapStatus(params, params,
                List.of(getComponentKey(SHELLY2_PILL_KEY_SWITCH, 0), getComponentKey(SHELLY2_PILL_KEY_SWITCH, 1)));

        assertThat(params.switch1.id, is(SHELLY2_PILL_ID + 1));
        assertThat(params.switch1.output, is(true));
    }

    @Test
    public void unconfiguredComponentsAreSkipped() {
        Shelly2DeviceStatusResult pill = status(
                "{\"switch:201\":{\"id\":201,\"output\":true},\"input:200\":{\"id\":200,\"state\":true}}");
        Shelly2DeviceStatusResult ds = status("{}");

        Shelly2PillMapper.mapStatus(pill, ds, List.of());

        assertThat(ds.switch1, is(nullValue()));
        assertThat(ds.input0, is(nullValue()));
    }

    @Test
    public void sensorsMapToAddonSlots() {
        Shelly2DeviceStatusResult pill = status("{\"temperature:200\":{\"id\":200,\"tC\":21.5},"
                + "\"humidity:200\":{\"id\":200,\"rh\":45.0},\"voltmeter:200\":{\"id\":200,\"voltage\":0.58}}");
        Shelly2DeviceStatusResult ds = status("{}");

        Shelly2PillMapper.mapStatus(pill, ds, List.of());

        assertThat(ds.temperature100.tC, is(21.5));
        assertThat(ds.humidity100.rh, is(45.0));
        assertThat(ds.voltmeter100.voltage, is(0.58));
    }
}
