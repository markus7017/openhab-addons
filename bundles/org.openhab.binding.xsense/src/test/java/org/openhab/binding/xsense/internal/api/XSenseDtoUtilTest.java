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
package org.openhab.binding.xsense.internal.api;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

@NonNullByDefault
public class XSenseDtoUtilTest {

    @Test
    public void orDefaultReturnsValueWhenPresent() {
        assertEquals("value", XSenseDtoUtil.orDefault("value", "fallback"));
    }

    @Test
    public void orDefaultReturnsFallbackWhenNull() {
        assertEquals("fallback", XSenseDtoUtil.orDefault(null, "fallback"));
    }

    @Test
    public void orEmptyReturnsEmptyStringWhenNull() {
        assertEquals("", XSenseDtoUtil.orEmpty(null));
        assertEquals("value", XSenseDtoUtil.orEmpty("value"));
    }

    @Test
    public void orZeroReturnsZeroWhenNull() {
        assertEquals(0, XSenseDtoUtil.orZero(null));
        assertEquals(5, XSenseDtoUtil.orZero(5));
    }
}
