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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsEMeter;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult.Shelly2DeviceStatusEmData;

/**
 * Covers em1data:N (single-phase clamp) total/returned-energy mapping, i.e. #18166 (Pro EM-50 / EM Mini lifetime
 * totals via EM1Data.GetStatus) and the shared returned-energy path (#20959), plus the relay/input count
 * reconciliation in {@link ShellyDeviceProfile#resolveNumInputs} (#21205).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
public class Shelly2ApiClientTest {

    private ShellySettingsStatus newStatusWithEmeters(int count) {
        ShellySettingsStatus status = new ShellySettingsStatus();
        status.emeters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            status.emeters.add(new ShellySettingsEMeter());
        }
        return status;
    }

    @Test
    void em1DataClampTotalUsedWhenPhaseATotalMissing() {
        // EM1Data.GetStatus (Pro EM-50 / EM Mini single clamp) reports total_act_energy, not a_total_act_energy
        ShellySettingsStatus status = newStatusWithEmeters(1);
        Shelly2DeviceStatusEmData emData = new Shelly2DeviceStatusEmData();
        emData.totalActiveEnergy = 12345.6; // total_act_energy

        Shelly2ApiClient.applyEm1Data(status, 0, emData);

        assertEquals(12345.6, status.emeters.get(0).total, 0.0001);
    }

    @Test
    void em1DataReturnedEnergyMappedFromClamp() {
        ShellySettingsStatus status = newStatusWithEmeters(1);
        Shelly2DeviceStatusEmData emData = new Shelly2DeviceStatusEmData();
        emData.totalActiveEnergy = 100.0;
        emData.totalActiveReturnedEnergy = 42.5; // total_act_ret_energy

        Shelly2ApiClient.applyEm1Data(status, 0, emData);

        assertEquals(42.5, status.emeters.get(0).totalReturned, 0.0001);
    }

    @Test
    void em1DataThreePhasePrefersPhaseATotalOverClampField() {
        // EMData.GetStatus (3-phase) reports a_total_act_energy; getTotalActiveEnergy() must prefer it
        ShellySettingsStatus status = newStatusWithEmeters(1);
        Shelly2DeviceStatusEmData emData = new Shelly2DeviceStatusEmData();
        emData.totalActiveEnergyA = 111.0;
        emData.totalActiveEnergy = 999.0; // must be ignored when phase A is present

        Shelly2ApiClient.applyEm1Data(status, 0, emData);

        assertEquals(111.0, status.emeters.get(0).total, 0.0001);
    }

    @Test
    void em1DataFirstSlotAlsoSetsDeviceTotalForSingleClamp() {
        // Single-clamp devices (EM Mini): slot 0's clamp total doubles as the device-level total
        ShellySettingsStatus status = newStatusWithEmeters(1);
        Shelly2DeviceStatusEmData emData = new Shelly2DeviceStatusEmData();
        emData.totalActiveEnergySum = 555.5; // total_act

        Shelly2ApiClient.applyEm1Data(status, 0, emData);

        assertEquals(555.5, status.totalKWH, 0.0001);
    }

    @Test
    void em1DataNullEmDataLeavesStatusUnchanged() {
        ShellySettingsStatus status = newStatusWithEmeters(1);

        Shelly2ApiClient.applyEm1Data(status, 0, null);

        assertNull(status.emeters.get(0).total);
    }

    @Test
    void em1DataOutOfBoundsSlotIgnored() {
        ShellySettingsStatus status = newStatusWithEmeters(1);
        Shelly2DeviceStatusEmData emData = new Shelly2DeviceStatusEmData();
        emData.totalActiveEnergy = 10.0;

        assertDoesNotThrow(() -> Shelly2ApiClient.applyEm1Data(status, 5, emData));
    }

    @Test
    void resolveNumInputsFloorsToRelayCountWhenDeviceUndercounts() {
        // 2PM with one relay's Input component missing from GetConfig (e.g. disabled) must not lose that
        // relay's lastEvent/eventCount channels just because the device only reported 1 Input component
        assertEquals(2, ShellyDeviceProfile.resolveNumInputs(1, true, false, false, 2));
    }

    @Test
    void resolveNumInputsKeepsDeviceCountWhenAlreadySufficient() {
        assertEquals(2, ShellyDeviceProfile.resolveNumInputs(2, true, false, false, 2));
    }

    @Test
    void resolveNumInputsIgnoresRollerAndDimmerDevices() {
        // Roller: 2 inputs map to 1 combined roller control, not 1:1 per relay - never floor
        assertEquals(0, ShellyDeviceProfile.resolveNumInputs(0, true, true, false, 2));
        // Dimmer: up to 2 inputs control a single light, not 1:1 per relay - never floor
        assertEquals(1, ShellyDeviceProfile.resolveNumInputs(1, true, false, true, 2));
    }
}
