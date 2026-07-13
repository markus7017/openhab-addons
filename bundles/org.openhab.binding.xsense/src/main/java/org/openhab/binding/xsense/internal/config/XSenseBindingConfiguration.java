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
package org.openhab.binding.xsense.internal.config;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link XSenseBindingConfiguration} contains the binding level configuration
 * (Settings → Add-on Settings → X-Sense Binding). It provides the defaults for thing level
 * options that are not configured explicitly.
 *
 * The OSGi framework provides the configuration as a plain map, this class maps it into a typed
 * structure.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseBindingConfiguration {

    public static final int DEFAULT_REFRESH_INTERVAL_SEC = 300;
    public static final int MIN_REFRESH_INTERVAL_SEC = 30;

    public int refreshInterval = DEFAULT_REFRESH_INTERVAL_SEC;

    /**
     * Maps the framework provided binding configuration into a typed configuration, applying
     * defaults and minimum limits for missing or invalid entries.
     */
    public static XSenseBindingConfiguration fromConfig(@Nullable Map<String, Object> properties) {
        XSenseBindingConfiguration config = new XSenseBindingConfiguration();
        if (properties == null) {
            return config;
        }
        Object interval = properties.get("refreshInterval");
        if (interval instanceof Number number) {
            config.refreshInterval = Math.max(MIN_REFRESH_INTERVAL_SEC, number.intValue());
        } else if (interval instanceof String string && !string.isBlank()) {
            try {
                config.refreshInterval = Math.max(MIN_REFRESH_INTERVAL_SEC, Integer.parseInt(string.trim()));
            } catch (NumberFormatException e) {
                // Keep default on unparsable value
            }
        }
        return config;
    }
}
