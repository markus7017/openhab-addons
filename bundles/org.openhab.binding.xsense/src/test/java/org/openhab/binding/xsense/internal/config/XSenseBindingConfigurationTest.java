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

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
public class XSenseBindingConfigurationTest {

    @Test
    public void nullMapYieldsDefaults() {
        XSenseBindingConfiguration config = XSenseBindingConfiguration.fromConfig(null);
        assertEquals(XSenseBindingConfiguration.DEFAULT_REFRESH_INTERVAL_SEC, config.refreshInterval);
    }

    @Test
    public void emptyMapYieldsDefaults() {
        XSenseBindingConfiguration config = XSenseBindingConfiguration.fromConfig(Map.of());
        assertEquals(XSenseBindingConfiguration.DEFAULT_REFRESH_INTERVAL_SEC, config.refreshInterval);
    }

    @Test
    public void numberValueIsApplied() {
        XSenseBindingConfiguration config = XSenseBindingConfiguration
                .fromConfig(Map.of("refreshInterval", new BigDecimal(120)));
        assertEquals(120, config.refreshInterval);
    }

    @Test
    public void stringValueIsParsed() {
        XSenseBindingConfiguration config = XSenseBindingConfiguration.fromConfig(Map.of("refreshInterval", "600"));
        assertEquals(600, config.refreshInterval);
    }

    @Test
    public void valueBelowMinimumIsClamped() {
        XSenseBindingConfiguration config = XSenseBindingConfiguration.fromConfig(Map.of("refreshInterval", 5));
        assertEquals(XSenseBindingConfiguration.MIN_REFRESH_INTERVAL_SEC, config.refreshInterval);
    }

    @Test
    public void unparsableStringKeepsDefault() {
        XSenseBindingConfiguration config = XSenseBindingConfiguration
                .fromConfig(Map.of("refreshInterval", "not-a-number"));
        assertEquals(XSenseBindingConfiguration.DEFAULT_REFRESH_INTERVAL_SEC, config.refreshInterval);
    }
}
