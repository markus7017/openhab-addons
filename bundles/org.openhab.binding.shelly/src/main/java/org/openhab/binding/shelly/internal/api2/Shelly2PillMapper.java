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

import static org.openhab.binding.shelly.internal.api2.dto.ShellyPillJsonDTO.SHELLY2_PILL_COMPONENT_BASE_ID;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2DevConfigInput;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2InputStatus;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2RelayStatus;

/**
 * {@link Shelly2PillMapper} maps the peripheral components of The Pill (instance ids 200+n) onto the component
 * slots processed for other devices: switches keep their id (relays are looked up by id), inputs get their
 * position as id (inputs are processed by index) and sensors take the Add-On slots (100+n).
 * Status of a switch or input missing in the configured ids is skipped, because the peripheral mode can change
 * before the profile is refreshed.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class Shelly2PillMapper {

    private Shelly2PillMapper() {
    }

    public static List<Integer> getSwitchIds(Shelly2GetConfigResult pill) {
        List<Integer> ids = new ArrayList<>();
        addId(ids, pill.switch200, 0);
        addId(ids, pill.switch201, 1);
        addId(ids, pill.switch202, 2);
        return ids;
    }

    public static List<Integer> getInputIds(Shelly2GetConfigResult pill) {
        List<Integer> ids = new ArrayList<>();
        addId(ids, pill.input200, 0);
        addId(ids, pill.input201, 1);
        addId(ids, pill.input202, 2);
        return ids;
    }

    public static void mapConfig(Shelly2GetConfigResult pill, Shelly2GetConfigResult dc) {
        dc.switch0 = pill.switch200;
        dc.switch1 = pill.switch201;
        dc.switch2 = pill.switch202;

        List<Shelly2DevConfigInput> inputs = new ArrayList<>();
        addIfPresent(inputs, pill.input200);
        addIfPresent(inputs, pill.input201);
        addIfPresent(inputs, pill.input202);
        dc.input0 = inputs.size() > 0 ? inputs.get(0) : null;
        dc.input1 = inputs.size() > 1 ? inputs.get(1) : null;
        dc.input2 = inputs.size() > 2 ? inputs.get(2) : null;
    }

    public static void mapStatus(Shelly2DeviceStatusResult pill, Shelly2DeviceStatusResult ds, List<Integer> switchIds,
            List<Integer> inputIds) {
        ds.switch0 = mapSwitch(pill.switch200, 0, ds.switch0, switchIds);
        ds.switch1 = mapSwitch(pill.switch201, 1, ds.switch1, switchIds);
        ds.switch2 = mapSwitch(pill.switch202, 2, ds.switch2, switchIds);

        mapInput(pill.input200, 0, ds, inputIds);
        mapInput(pill.input201, 1, ds, inputIds);
        mapInput(pill.input202, 2, ds, inputIds);

        ds.temperature100 = pill.temperature200 != null ? pill.temperature200 : ds.temperature100;
        ds.temperature101 = pill.temperature201 != null ? pill.temperature201 : ds.temperature101;
        ds.temperature102 = pill.temperature202 != null ? pill.temperature202 : ds.temperature102;
        ds.temperature103 = pill.temperature203 != null ? pill.temperature203 : ds.temperature103;
        ds.temperature104 = pill.temperature204 != null ? pill.temperature204 : ds.temperature104;
        ds.humidity100 = pill.humidity200 != null ? pill.humidity200 : ds.humidity100;
        ds.voltmeter100 = pill.voltmeter200 != null ? pill.voltmeter200 : ds.voltmeter100;
    }

    private static void addId(List<Integer> ids, @Nullable Object component, int pin) {
        if (component != null) {
            ids.add(SHELLY2_PILL_COMPONENT_BASE_ID + pin);
        }
    }

    private static <T> void addIfPresent(List<T> list, @Nullable T value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static @Nullable Shelly2RelayStatus mapSwitch(@Nullable Shelly2RelayStatus rs, int pin,
            @Nullable Shelly2RelayStatus current, List<Integer> switchIds) {
        int id = SHELLY2_PILL_COMPONENT_BASE_ID + pin;
        if (rs == null || !switchIds.contains(id)) {
            return current;
        }
        if (rs.id == null) { // NotifyStatus omits the id
            rs.id = id;
        }
        return rs;
    }

    private static void mapInput(@Nullable Shelly2InputStatus is, int pin, Shelly2DeviceStatusResult ds,
            List<Integer> inputIds) {
        int idx = inputIds.indexOf(SHELLY2_PILL_COMPONENT_BASE_ID + pin);
        if (is == null || idx < 0) {
            return;
        }
        is.id = idx;
        switch (idx) {
            case 0 -> ds.input0 = is;
            case 1 -> ds.input1 = is;
            default -> ds.input2 = is;
        }
    }
}
