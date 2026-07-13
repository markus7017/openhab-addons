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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link XSenseDtoUtil} provides null-safe access helpers for {@link
 * org.openhab.binding.xsense.internal.api.dto.XSenseApiDto} fields, all of which are nullable
 * boxed types since the cloud API does not guarantee the presence of any field.
 *
 * Use these helpers for descriptive/optional fields where a fallback value is meaningful (e.g.
 * display names). Fields that drive control flow (ids used for lookups or as map keys) should
 * keep an explicit {@code null} check instead, since silently substituting a default there would
 * hide a malformed API response rather than surface it.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public final class XSenseDtoUtil {

    private XSenseDtoUtil() {
    }

    /**
     * Returns the value, or {@code fallback} when it is null.
     */
    public static <T> T orDefault(@Nullable T value, T fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Returns the string value, or {@code ""} when it is null.
     */
    public static String orEmpty(@Nullable String value) {
        return orDefault(value, "");
    }

    /**
     * Returns the integer value, or {@code 0} when it is null.
     */
    public static int orZero(@Nullable Integer value) {
        return orDefault(value, 0);
    }

    /**
     * Masks an email address for display/logging: first character + *** + domain.
     */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
