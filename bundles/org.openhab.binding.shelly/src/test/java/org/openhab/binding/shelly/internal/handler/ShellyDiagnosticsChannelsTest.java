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
package org.openhab.binding.shelly.internal.handler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLY1;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYBLUBUTTON1;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api1.Shelly1ApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions;
import org.openhab.binding.shelly.internal.provider.ShellyTranslationProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyDiagnosticsChannelsTest {

    private static final ThingUID THING_UID = new ThingUID("shelly", "shellyplus1", "test");

    @BeforeAll
    static void initChannelDefinitions() {
        ShellyTranslationProvider messages = mock(ShellyTranslationProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenReturn("mocked");
        new ShellyChannelDefinitions(messages);
    }

    private static Thing thing() {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(THING_UID);
        return thing;
    }

    @Test
    void createDiagnosticsChannelsGen1ReturnsNothing() {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLY1);
        ShellySettingsStatus status = new ShellySettingsStatus();
        status.ramTotal = 50000L;
        status.ramFree = 20000L;
        status.fsSize = 1000000L;
        status.fsFree = 400000L;
        status.restartRequired = false;

        Map<String, Channel> channels = ShellyChannelDefinitions.createDiagnosticsChannels(thing(), profile, status);

        assertThat(channels.isEmpty(), is(true));
    }

    @Test
    void createDiagnosticsChannelsGen2AddsOnlyReportedFields() {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        ShellySettingsStatus status = new ShellySettingsStatus();
        status.ramTotal = 50000L;
        status.fsFree = 400000L;

        Map<String, Channel> channels = ShellyChannelDefinitions.createDiagnosticsChannels(thing(), profile, status);

        // 2 reported utilization fields + 5 always-on binding-computed health stat channels
        assertThat(channels.size(), is(7));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_TOTALMEM), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_FREEFS), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_FREEMEM), is(false));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_TOTALFS), is(false));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_RESTARTREQ), is(false));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_RESTARTS), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_TIMEOUTERRORS), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_ALARMS), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_LASTALARM), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_PROTOCOLERRORS), is(true));
        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_MAXITEMP), is(false));
    }

    @Test
    void createDiagnosticsChannelsAddsMaxTempOnlyWhenDeviceReportsInternalTemp() {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        ShellySettingsStatus status = new ShellySettingsStatus();
        status.temperature = 42.5;

        Map<String, Channel> channels = ShellyChannelDefinitions.createDiagnosticsChannels(thing(), profile, status);

        assertThat(channels.containsKey(CHANNEL_GROUP_DIAG + "#" + CHANNEL_DIAG_MAXITEMP), is(true));
    }

    @Test
    void createDiagnosticsChannelsGen2AddsAllFieldsWhenReported() {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        ShellySettingsStatus status = new ShellySettingsStatus();
        status.ramTotal = 50000L;
        status.ramFree = 20000L;
        status.fsSize = 1000000L;
        status.fsFree = 400000L;
        status.restartRequired = false;

        Map<String, Channel> channels = ShellyChannelDefinitions.createDiagnosticsChannels(thing(), profile, status);

        // 5 reported utilization fields + 5 always-on binding-computed health stat channels
        assertThat(channels.size(), is(10));
    }

    @Test
    void createDiagnosticsChannelsSkipsBlu() {
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYBLUBUTTON1);
        ShellySettingsStatus status = new ShellySettingsStatus();

        Map<String, Channel> channels = ShellyChannelDefinitions.createDiagnosticsChannels(thing(), profile, status);

        assertThat(channels.isEmpty(), is(true));
    }

    @Test
    void updateDeviceStatusPublishesDiagnosticValues() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        when(handler.getProfile()).thenReturn(profile);
        Thing thing = thing();
        when(handler.getThing()).thenReturn(thing);
        when(handler.areChannelsCreated()).thenReturn(true);

        ShellySettingsStatus status = new ShellySettingsStatus();
        status.ramTotal = 50000L;
        status.ramFree = 20000L;
        status.fsSize = 1000000L;
        status.fsFree = 400000L;
        status.restartRequired = true;

        ShellyComponents.updateDeviceStatus(handler, status);

        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_TOTALMEM),
                argThat(s -> s instanceof QuantityType<?> && ((QuantityType<?>) s).longValue() == 50000));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_FREEMEM),
                argThat(s -> s instanceof QuantityType<?> && ((QuantityType<?>) s).longValue() == 20000));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_TOTALFS),
                argThat(s -> s instanceof QuantityType<?> && ((QuantityType<?>) s).longValue() == 1000000));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_FREEFS),
                argThat(s -> s instanceof QuantityType<?> && ((QuantityType<?>) s).longValue() == 400000));
        verify(handler).updateChannel(CHANNEL_GROUP_DIAG, CHANNEL_DIAG_RESTARTREQ, OnOffType.ON);
    }

    @Test
    void updateDeviceStatusSkipsDiagnosticsWhenNotReported() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        when(handler.getProfile()).thenReturn(profile);
        Thing thing = thing();
        when(handler.getThing()).thenReturn(thing);
        when(handler.areChannelsCreated()).thenReturn(true);

        ShellyComponents.updateDeviceStatus(handler, new ShellySettingsStatus());

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_DIAG), anyString(), any());
    }

    @Test
    void updateDeviceStatusSkipsDiagnosticsForGen1() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLY1);
        when(handler.getProfile()).thenReturn(profile);
        Thing thing = thing();
        when(handler.getThing()).thenReturn(thing);
        when(handler.areChannelsCreated()).thenReturn(true);

        ShellySettingsStatus status = new ShellySettingsStatus();
        status.ramTotal = 50000L;
        status.ramFree = 20000L;

        ShellyComponents.updateDeviceStatus(handler, status);

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_DIAG), anyString(), any());
    }

    @Test
    void updateDiagnosticsStatsPublishesCountersAndLastAlarm() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        when(handler.getProfile()).thenReturn(profile);

        ShellyDeviceStats stats = new ShellyDeviceStats();
        stats.restarts.set(2);
        stats.timeoutErrors.set(3);
        stats.alarms.set(1);
        stats.protocolErrors.set(4);
        stats.maxInternalTemp.set(55.4);
        stats.lastAlarm.set(new ShellyDeviceStats.ShellyDeviceAlarm("OVERTEMP", 0));

        ShellyComponents.updateDiagnosticsStats(handler, stats);

        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_RESTARTS),
                argThat(s -> s instanceof DecimalType d && d.intValue() == 2));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_TIMEOUTERRORS),
                argThat(s -> s instanceof DecimalType d && d.intValue() == 3));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_ALARMS),
                argThat(s -> s instanceof DecimalType d && d.intValue() == 1));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_PROTOCOLERRORS),
                argThat(s -> s instanceof DecimalType d && d.intValue() == 4));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_MAXITEMP),
                argThat(s -> s instanceof QuantityType<?> q && Math.abs(q.doubleValue() - 55.4) < 0.01));
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_LASTALARM),
                argThat(s -> s instanceof StringType && s.toString().startsWith("OVERTEMP")));
    }

    @Test
    void updateDiagnosticsStatsSkipsMaxTempWhenNeverMeasured() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1);
        when(handler.getProfile()).thenReturn(profile);

        ShellyComponents.updateDiagnosticsStats(handler, new ShellyDeviceStats());

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_MAXITEMP), any());
        verify(handler).updateChannel(eq(CHANNEL_GROUP_DIAG), eq(CHANNEL_DIAG_LASTALARM), eq(new StringType("")));
    }

    @Test
    void updateDiagnosticsStatsSkipsForGen1() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLY1);
        when(handler.getProfile()).thenReturn(profile);

        ShellyComponents.updateDiagnosticsStats(handler, new ShellyDeviceStats());

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_DIAG), anyString(), any());
    }

    @Test
    void updateDiagnosticsStatsSkipsForBlu() {
        ShellyThingInterface handler = mock(ShellyThingInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYBLUBUTTON1);
        when(handler.getProfile()).thenReturn(profile);

        ShellyComponents.updateDiagnosticsStats(handler, new ShellyDeviceStats());

        verify(handler, never()).updateChannel(eq(CHANNEL_GROUP_DIAG), anyString(), any());
    }
}
