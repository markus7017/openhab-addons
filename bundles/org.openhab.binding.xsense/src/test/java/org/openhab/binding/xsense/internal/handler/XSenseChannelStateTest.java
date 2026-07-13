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
package org.openhab.binding.xsense.internal.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;

@NonNullByDefault
public class XSenseChannelStateTest {

    @Test
    public void firstUpdateAlwaysReportsChange() {
        XSenseChannelState state = new XSenseChannelState();
        assertTrue(state.update("smoke", OnOffType.OFF));
    }

    @Test
    public void repeatingTheSameStateReportsNoChange() {
        XSenseChannelState state = new XSenseChannelState();
        state.update("smoke", OnOffType.OFF);
        assertFalse(state.update("smoke", OnOffType.OFF));
    }

    @Test
    public void differentStateReportsChange() {
        XSenseChannelState state = new XSenseChannelState();
        state.update("smoke", OnOffType.OFF);
        assertTrue(state.update("smoke", OnOffType.ON));
    }

    @Test
    public void invalidateForcesNextUpdateToReportChange() {
        XSenseChannelState state = new XSenseChannelState();
        state.update("path", new StringType("{}"));
        state.invalidate("path");
        assertTrue(state.update("path", new StringType("{}")));
    }

    @Test
    public void channelsAreTrackedIndependently() {
        XSenseChannelState state = new XSenseChannelState();
        state.update("smoke", OnOffType.OFF);
        assertTrue(state.update("co", OnOffType.OFF));
    }
}
