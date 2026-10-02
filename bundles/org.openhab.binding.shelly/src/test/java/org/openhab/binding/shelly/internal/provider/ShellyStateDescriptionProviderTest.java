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
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.BINDING_ID;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions.NumberRange;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.i18n.ChannelTypeI18nLocalizationService;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateOption;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyStateDescriptionProviderTest {

    private static final ThingUID THING_UID = new ThingUID(THING_TYPE_SHELLYPLUS1, "test");

    private final ThingRegistry thingRegistry = mock(ThingRegistry.class);
    private final ShellyThingInterface handler = mock(ShellyThingInterface.class,
            withSettings().extraInterfaces(ThingHandler.class));
    private final ShellyStateDescriptionProvider provider = new ShellyStateDescriptionProvider(
            mock(EventPublisher.class), mock(ItemChannelLinkRegistry.class),
            mock(ChannelTypeI18nLocalizationService.class), thingRegistry);

    @Test
    void enumOptionsAreProvidedWithoutAnOriginalStateDescription() {
        Channel channel = channel("vgroup200#enum201", "vcompEnum");
        when(handler.getStateOptions("vgroup200#enum201"))
                .thenReturn(List.of(new StateOption("eco", "Eco"), new StateOption("comfort", "Comfort")));

        StateDescription description = Objects.requireNonNull(provider.getStateDescription(channel, null, null));

        assertThat(description.getOptions(), hasSize(2));
        assertThat(description.getOptions().get(1).getLabel(), is("Comfort"));
    }

    @Test
    void numberRangeIsProvidedWithoutAnOriginalStateDescription() {
        Channel channel = channel("vcomponents#number200", "vcompNumber");
        when(handler.getNumberRange("vcomponents#number200")).thenReturn(new NumberRange(5.0, 30.0, 0.5, "°C"));

        StateDescription description = Objects.requireNonNull(provider.getStateDescription(channel, null, null));

        assertThat(description.getMinimum(), is(BigDecimal.valueOf(5.0)));
        assertThat(description.getMaximum(), is(BigDecimal.valueOf(30.0)));
        assertThat(description.getStep(), is(BigDecimal.valueOf(0.5)));
        assertThat(description.getPattern(), is("%.2f °C"));
    }

    @Test
    void channelWithoutDynamicDataGetsNoDescription() {
        Channel channel = channel("vcomponents#text202", "vcompText");
        when(handler.getStateOptions("vcomponents#text202")).thenReturn(null);

        assertThat(provider.getStateDescription(channel, null, null), is(nullValue()));
    }

    private Channel channel(String channelId, String channelType) {
        Thing thing = mock(Thing.class);
        when(thing.getHandler()).thenReturn((ThingHandler) handler);
        when(thingRegistry.get(THING_UID)).thenReturn(thing);
        return ChannelBuilder.create(new ChannelUID(THING_UID, channelId))
                .withType(new ChannelTypeUID(BINDING_ID, channelType)).build();
    }
}
