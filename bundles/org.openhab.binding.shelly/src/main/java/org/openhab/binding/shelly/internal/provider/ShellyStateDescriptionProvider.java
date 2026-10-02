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

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.BINDING_ID;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.provider.ShellyChannelDefinitions.NumberRange;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.BaseDynamicStateDescriptionProvider;
import org.openhab.core.thing.i18n.ChannelTypeI18nLocalizationService;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.thing.type.DynamicStateDescriptionProvider;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * This class provides the list of valid inputs for the input channel of a source.
 *
 * @author Markus Michels - Initial contribution
 *
 */
@NonNullByDefault
@Component(service = { DynamicStateDescriptionProvider.class, ShellyStateDescriptionProvider.class })
public class ShellyStateDescriptionProvider extends BaseDynamicStateDescriptionProvider {
    private final ThingRegistry thingRegistry;

    @Activate
    public ShellyStateDescriptionProvider(final @Reference EventPublisher eventPublisher, //
            final @Reference ItemChannelLinkRegistry itemChannelLinkRegistry, //
            final @Reference ChannelTypeI18nLocalizationService channelTypeI18nLocalizationService,
            @Reference ThingRegistry thingRegistry) {
        this.eventPublisher = eventPublisher;
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
        this.channelTypeI18nLocalizationService = channelTypeI18nLocalizationService;
        this.thingRegistry = thingRegistry;
    }

    @Override
    public @Nullable StateDescription getStateDescription(Channel channel,
            @Nullable StateDescription originalStateDescription, @Nullable Locale locale) {
        ChannelTypeUID uid = channel.getChannelTypeUID();
        if (uid == null || !BINDING_ID.equals(uid.getBindingId())) {
            return null;
        }

        Thing thing = thingRegistry.get(channel.getUID().getThingUID());
        if (thing == null) {
            return null;
        }
        ShellyThingInterface handler = (ShellyThingInterface) thing.getHandler();
        if (handler == null) {
            return null;
        }

        String channelId = channel.getUID().getId();
        List<StateOption> stateOptions = handler.getStateOptions(channelId);
        NumberRange numberRange = handler.getNumberRange(channelId);
        if (stateOptions == null && numberRange == null) {
            return null;
        }

        // Virtual Component channel types declare no <state>, so there is no original description to extend
        StateDescriptionFragmentBuilder builder = originalStateDescription != null
                ? StateDescriptionFragmentBuilder.create(originalStateDescription)
                : StateDescriptionFragmentBuilder.create();
        if (stateOptions != null) {
            builder.withOptions(stateOptions);
        }
        if (numberRange != null) {
            Double min = numberRange.min;
            Double max = numberRange.max;
            Double step = numberRange.step;
            String unit = numberRange.unit;
            if (min != null) {
                builder.withMinimum(BigDecimal.valueOf(min));
            }
            if (max != null) {
                builder.withMaximum(BigDecimal.valueOf(max));
            }
            if (step != null) {
                builder.withStep(BigDecimal.valueOf(step));
            }
            if (unit != null && !unit.isBlank()) {
                builder.withPattern("%.2f " + unit);
            }
        }
        return builder.build().toStateDescription();
    }
}
