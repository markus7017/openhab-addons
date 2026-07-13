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
package org.openhab.binding.xsense.internal;

import static org.openhab.binding.xsense.internal.XSenseBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.xsense.internal.config.XSenseBindingConfiguration;
import org.openhab.binding.xsense.internal.handler.XSenseAccountHandler;
import org.openhab.binding.xsense.internal.handler.XSenseHomeHandler;
import org.openhab.binding.xsense.internal.handler.XSenseSensorHandler;
import org.openhab.binding.xsense.internal.handler.XSenseStationHandler;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link XSenseHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.xsense", service = ThingHandlerFactory.class)
public class XSenseHandlerFactory extends BaseThingHandlerFactory {

    private final HttpClient httpClient;
    private final TranslationProvider i18nProvider;
    private final LocaleProvider localeProvider;
    // Volatile for safe publication after @Modified callbacks
    private volatile XSenseBindingConfiguration bindingConfig = new XSenseBindingConfiguration();

    @Activate
    public XSenseHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference TranslationProvider i18nProvider, @Reference LocaleProvider localeProvider,
            Map<String, Object> properties) {
        httpClient = httpClientFactory.getCommonHttpClient();
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;
        bindingConfig = XSenseBindingConfiguration.fromConfig(properties);
    }

    @Modified
    protected void modified(Map<String, Object> properties) {
        bindingConfig = XSenseBindingConfiguration.fromConfig(properties);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            // Supplier so that later binding configuration changes reach existing handlers
            return new XSenseAccountHandler((Bridge) thing, httpClient, () -> bindingConfig, i18nProvider,
                    localeProvider);
        }
        if (THING_TYPE_HOME.equals(thingTypeUID)) {
            return new XSenseHomeHandler((Bridge) thing);
        }
        if (THING_TYPE_STATION.equals(thingTypeUID)) {
            return new XSenseStationHandler((Bridge) thing);
        }
        if (SENSOR_THING_TYPES.contains(thingTypeUID)) {
            return new XSenseSensorHandler(thing);
        }
        return null;
    }
}
