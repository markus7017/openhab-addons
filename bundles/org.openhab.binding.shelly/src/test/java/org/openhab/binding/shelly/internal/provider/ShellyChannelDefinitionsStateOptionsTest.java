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
package org.openhab.binding.shelly.internal.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.types.StateOption;

/**
 * Tests {@link ShellyChannelDefinitions#getStateOptions(String)}, {@link ShellyChannelDefinitions#addStateOption}
 * and {@link ShellyChannelDefinitions#clearStateOptions}: options are kept per-instance channel id (e.g.
 * {@code ChannelUID.getId()}), not per channel type, so several channels that share one channel type (TRV profile,
 * roller favorites, several Virtual Enum components on the same Thing) each keep their own independent list.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyChannelDefinitionsStateOptionsTest {

    private static ShellyChannelDefinitions newInstance() {
        ShellyTranslationProvider messages = mock(ShellyTranslationProvider.class);
        when(messages.get(anyString(), any(Object[].class))).thenReturn("mocked");
        return new ShellyChannelDefinitions(messages);
    }

    @Test
    void twoVirtualEnumsOnTheSameThingKeepSeparateOptionLists() {
        ShellyChannelDefinitions channelDefinitions = newInstance();
        channelDefinitions.addStateOption("vcomponents#enum200", "low", "low");
        channelDefinitions.addStateOption("vcomponents#enum200", "high", "high");
        channelDefinitions.addStateOption("vcomponents#enum201", "on", "on");

        List<StateOption> enum200 = channelDefinitions.getStateOptions("vcomponents#enum200");
        List<StateOption> enum201 = channelDefinitions.getStateOptions("vcomponents#enum201");

        assertThat(enum200, is(List.of(new StateOption("low", "low"), new StateOption("high", "high"))));
        assertThat(enum201, is(List.of(new StateOption("on", "on"))));
    }

    @Test
    void getStateOptionsReturnsEmptyListForUnknownChannelId() {
        ShellyChannelDefinitions channelDefinitions = newInstance();
        channelDefinitions.addStateOption("vcomponents#enum200", "low", "low");

        assertThat(channelDefinitions.getStateOptions("vcomponents#enum299").isEmpty(), is(true));
    }

    @Test
    void clearStateOptionsOnlyRemovesTheGivenChannelId() {
        ShellyChannelDefinitions channelDefinitions = newInstance();
        channelDefinitions.addStateOption("vcomponents#enum200", "low", "low");
        channelDefinitions.addStateOption("vcomponents#enum201", "on", "on");

        channelDefinitions.clearStateOptions("vcomponents#enum200");

        assertThat(channelDefinitions.getStateOptions("vcomponents#enum200").isEmpty(), is(true));
        assertThat(channelDefinitions.getStateOptions("vcomponents#enum201"), is(List.of(new StateOption("on", "on"))));
    }

    @Test
    void trvProfileAndRollerFavoriteOptionsDoNotBleedIntoEachOther() {
        ShellyChannelDefinitions channelDefinitions = newInstance();
        channelDefinitions.addStateOption("control#profile", "1", "1: Home");
        channelDefinitions.addStateOption("rollerControl#rollerFav", "1", "1: Open");

        assertThat(channelDefinitions.getStateOptions("control#profile"), is(List.of(new StateOption("1", "1: Home"))));
        assertThat(channelDefinitions.getStateOptions("rollerControl#rollerFav"),
                is(List.of(new StateOption("1", "1: Open"))));
    }
}
