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
package org.openhab.binding.shelly.internal.api2;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * {@link Shelly2DebugLogListener} receives decoded log lines (and the closed notification) from a
 * {@link Shelly2DebugLogSocket}.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public interface Shelly2DebugLogListener {

    /**
     * A log line was received from the device.
     *
     * @param level Shelly log level (0=error, 1=warn, 2=info, 3=debug, 4=verbose)
     * @param data Log line text
     */
    void onDebugLogLine(int level, String data);

    /**
     * The debug log WebSocket was closed (by the device, the network, or an error).
     */
    void onDebugLogClosed();
}
