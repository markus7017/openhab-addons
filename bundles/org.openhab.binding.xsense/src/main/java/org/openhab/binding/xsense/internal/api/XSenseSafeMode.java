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

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link XSenseSafeMode} enumerates the security modes of a base station. The X-Sense app
 * writes the capitalized values ("Disarmed", "Home", "Away") to the appMode shadow; the channel
 * uses the same values, so this enum is the single place to adapt should the wire format differ.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public enum XSenseSafeMode {
    DISARMED("Disarmed"),
    HOME("Home"),
    AWAY("Away");

    private final String value;

    XSenseSafeMode(String value) {
        this.value = value;
    }

    /**
     * Returns the value used both on the safeMode channel and in the appMode shadow payload.
     */
    public String getValue() {
        return value;
    }

    /**
     * Maps a cloud-reported safe mode to the enum, tolerating casing and known synonyms.
     * Returns null for unknown values so that callers can surface them instead of guessing.
     */
    public static @Nullable XSenseSafeMode fromCloud(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "disarmed", "disarm", "off", "0" -> DISARMED;
            case "home" -> HOME;
            case "away" -> AWAY;
            default -> null;
        };
    }

    /**
     * Maps a channel command to the enum (case-insensitive match on the channel value) or null
     * for unsupported commands.
     */
    public static @Nullable XSenseSafeMode fromCommand(String command) {
        for (XSenseSafeMode mode : values()) {
            if (mode.value.equalsIgnoreCase(command.trim())) {
                return mode;
            }
        }
        return null;
    }
}
