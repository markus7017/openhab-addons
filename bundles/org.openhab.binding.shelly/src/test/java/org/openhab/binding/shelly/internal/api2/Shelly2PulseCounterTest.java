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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2DeviceStatusResult;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2DeviceStatus.Shelly2InputStatus;

import com.google.gson.Gson;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings({ "null" })
public class Shelly2PulseCounterTest {

    private final Gson gson = new Gson();

    private Shelly2InputStatus input2(String json) {
        return Objects
                .requireNonNull(gson.fromJson("{\"input:2\":" + json + "}", Shelly2DeviceStatusResult.class)).input2;
    }

    @Test
    public void countModeStatusFillsPulseCounter() {
        ShellySettingsStatus status = new ShellySettingsStatus();

        Shelly2ApiClient.updatePulseCounter(status, input2(
                "{\"id\":2,\"counts\":{\"total\":177,\"by_minute\":[12,10,8],\"minute_ts\":1700000000},\"freq\":0.25}"));

        assertThat(status.pulseCounter.total, is(177));
        assertThat(status.pulseCounter.lastMinute, is(12.0));
        assertThat(status.pulseCounter.frequency, is(0.25));
    }

    @Test
    public void partialNotifyStatusKeepsPreviousValues() {
        ShellySettingsStatus status = new ShellySettingsStatus();
        Shelly2ApiClient.updatePulseCounter(status,
                input2("{\"id\":2,\"counts\":{\"total\":177,\"by_minute\":[12,10,8]},\"freq\":0.25}"));

        Shelly2ApiClient.updatePulseCounter(status, input2("{\"counts\":{\"total\":180}}"));

        assertThat(status.pulseCounter.total, is(180));
        assertThat(status.pulseCounter.lastMinute, is(12.0));
        assertThat(status.pulseCounter.frequency, is(0.25));
    }
}
