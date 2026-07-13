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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.State;

/**
 * The {@link XSenseChannelState} tracks the last published state per channel so a handler can skip
 * redundant {@code updateState} calls on unchanged inventory polls. Shared by the home, station and
 * sensor handlers, which otherwise each poll the same {@code Map<String, State>} independently.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseChannelState {

    private final Map<String, State> cache = new ConcurrentHashMap<>();

    /**
     * Records the new state and reports whether it differs from the last known value for this
     * channel.
     *
     * @param channelId the channel to update
     * @param state the new state
     * @return true if the caller should publish the state (it changed, or the channel was unknown)
     */
    public boolean update(String channelId, State state) {
        return !state.equals(cache.put(channelId, state));
    }

    /**
     * Forgets the last known state of a channel, so the next {@link #update} call always reports a
     * change. Used before a forced refresh (e.g. on a {@code RefreshType} command).
     */
    public void invalidate(String channelId) {
        cache.remove(channelId);
    }
}
