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

import static org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.SHELLY2_EVENT_CFGCHANGED;
import static org.openhab.binding.shelly.internal.api2.dto.ShellyPillJsonDTO.*;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2DevConfigInput;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceConfig.Shelly2GetConfigResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2InputStatus;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2NotifyEvent;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2RelayStatus;

/**
 * {@link Shelly2PillMapper} maps the peripheral components of The Pill (instance ids 200+n) onto the component
 * slots processed for other devices: switches keep their id (relays are looked up by id), inputs get their
 * position as id (inputs are processed by index) and sensors take the Add-On slots (100+n).
 * Status of a switch or input missing in the configured components is skipped, because the peripheral mode can
 * change before the profile is refreshed.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class Shelly2PillMapper {
    private Shelly2PillMapper() {
    }

    /**
     * Input events carry the component id (200+pin), but inputs are addressed by their position among the configured
     * inputs. Events of inputs which are not configured are removed. Configuration changes are kept, because a
     * peripheral mode change reports a new input before the profile is refreshed.
     */
    public static void mapInputEvents(List<Shelly2NotifyEvent> events, List<String> components) {
        events.removeIf(e -> isInputEvent(e) && getInputIndex(components, e.component) < 0);
        for (Shelly2NotifyEvent e : events) {
            if (isInputEvent(e)) {
                e.id = getInputIndex(components, e.component);
            }
        }
    }

    private static boolean isInputEvent(Shelly2NotifyEvent e) {
        return isInputComponent(e.component) && !SHELLY2_EVENT_CFGCHANGED.equals(e.event);
    }

    /**
     * @param type component type prefix, e.g. "switch:"
     * @param index 0-based peripheral index
     * @return component key, e.g. "switch:201" for index 1
     */
    public static String getComponentKey(String type, int index) {
        return type + (SHELLY2_PILL_ID + index);
    }

    public static boolean isInputComponent(@Nullable String key) {
        return key != null && key.startsWith(SHELLY2_PILL_KEY_INPUT);
    }

    /**
     * @return position of the input among the configured inputs, -1 if not configured
     */
    public static int getInputIndex(List<String> components, @Nullable String key) {
        return components.stream().filter(Shelly2PillMapper::isInputComponent).toList().indexOf(key);
    }

    /**
     * @param type component type prefix, e.g. "switch:"
     * @param position position of the component among the configured components of this type
     * @return 0-based peripheral index (pin) of the component, -1 if not configured
     */
    public static int getPin(List<String> components, String type, int position) {
        List<String> keys = components.stream().filter(key -> key.startsWith(type)).toList();
        if (position < 0 || position >= keys.size()) {
            return -1;
        }
        return Integer.parseInt(keys.get(position).substring(type.length())) - SHELLY2_PILL_ID;
    }

    /**
     * @return position of the component with the given pin among the configured components of this type, -1 if not
     *         configured
     */
    public static int getPosition(List<String> components, String type, int pin) {
        return components.stream().filter(key -> key.startsWith(type)).toList().indexOf(getComponentKey(type, pin));
    }

    public static void mapConfig(Shelly2GetConfigResult pill, Shelly2GetConfigResult dc) {
        dc.switch0 = pill.switch200;
        dc.switch1 = pill.switch201;
        dc.switch2 = pill.switch202;

        List<Shelly2DevConfigInput> inputs = getConfiguredInputs(pill);
        dc.input0 = !inputs.isEmpty() ? inputs.get(0) : null;
        dc.input1 = inputs.size() > 1 ? inputs.get(1) : null;
        dc.input2 = inputs.size() > 2 ? inputs.get(2) : null;
    }

    public static void mapStatus(Shelly2DeviceStatusResult pill, Shelly2DeviceStatusResult ds,
            List<String> components) {
        mapSwitches(pill, ds, components);
        mapInputs(pill, ds, components);
        mapSensors(pill, ds);
    }

    private static List<Shelly2DevConfigInput> getConfiguredInputs(Shelly2GetConfigResult pill) {
        List<Shelly2DevConfigInput> inputs = new ArrayList<>();
        addIfPresent(inputs, pill.input200);
        addIfPresent(inputs, pill.input201);
        addIfPresent(inputs, pill.input202);
        return inputs;
    }

    private static <T> void addIfPresent(List<T> list, @Nullable T value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static void mapSwitches(Shelly2DeviceStatusResult pill, Shelly2DeviceStatusResult ds,
            List<String> components) {
        ds.switch0 = mapSwitch(pill.switch200, 0, ds.switch0, components);
        ds.switch1 = mapSwitch(pill.switch201, 1, ds.switch1, components);
        ds.switch2 = mapSwitch(pill.switch202, 2, ds.switch2, components);
    }

    private static @Nullable Shelly2RelayStatus mapSwitch(@Nullable Shelly2RelayStatus rs, int index,
            @Nullable Shelly2RelayStatus current, List<String> components) {
        if (rs == null || !components.contains(getComponentKey(SHELLY2_PILL_KEY_SWITCH, index))) {
            return current;
        }
        if (rs.id == null) { // NotifyStatus omits the id
            rs.id = SHELLY2_PILL_ID + index;
        }
        return rs;
    }

    private static void mapInputs(Shelly2DeviceStatusResult pill, Shelly2DeviceStatusResult ds,
            List<String> components) {
        mapInput(pill.input200, 0, ds, components);
        mapInput(pill.input201, 1, ds, components);
        mapInput(pill.input202, 2, ds, components);
    }

    private static void mapInput(@Nullable Shelly2InputStatus is, int index, Shelly2DeviceStatusResult ds,
            List<String> components) {
        int position = getInputIndex(components, getComponentKey(SHELLY2_PILL_KEY_INPUT, index));
        if (is == null || position < 0) {
            return;
        }
        is.id = position;
        switch (position) {
            case 0 -> ds.input0 = is;
            case 1 -> ds.input1 = is;
            default -> ds.input2 = is;
        }
    }

    private static void mapSensors(Shelly2DeviceStatusResult pill, Shelly2DeviceStatusResult ds) {
        ds.temperature100 = pill.temperature200 != null ? pill.temperature200 : ds.temperature100;
        ds.temperature101 = pill.temperature201 != null ? pill.temperature201 : ds.temperature101;
        ds.temperature102 = pill.temperature202 != null ? pill.temperature202 : ds.temperature102;
        ds.temperature103 = pill.temperature203 != null ? pill.temperature203 : ds.temperature103;
        ds.temperature104 = pill.temperature204 != null ? pill.temperature204 : ds.temperature104;
        ds.humidity100 = pill.humidity200 != null ? pill.humidity200 : ds.humidity100;
        ds.voltmeter100 = pill.voltmeter200 != null ? pill.voltmeter200 : ds.voltmeter100;
    }
}
