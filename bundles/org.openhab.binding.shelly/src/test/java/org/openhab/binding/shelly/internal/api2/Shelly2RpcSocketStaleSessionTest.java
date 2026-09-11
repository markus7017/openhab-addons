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

import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.shelly.internal.handler.ShellyThingInterface;
import org.openhab.binding.shelly.internal.handler.ShellyThingTable;

/**
 * @author Markus Michels - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault({})
class Shelly2RpcSocketStaleSessionTest {

    private @Mock ShellyThingTable thingTable;
    private @Mock ShellyThingInterface thing;
    private @Mock WebSocketClient webSocketClient;
    private @Mock ScheduledExecutorService scheduler;
    private @Mock Shelly2RpctInterface handler;
    private @Mock Session activeSession;
    private @Mock Session staleSession;

    private Shelly2RpcSocket socket;

    @BeforeEach
    void setUp() {
        socket = new Shelly2RpcSocket(thingTable, false, webSocketClient, scheduler);
        socket.addMessageHandler(handler);
        when(activeSession.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 80));
        when(thingTable.getThing(any(InetSocketAddress.class))).thenReturn(thing);
        when(thing.getThingName()).thenReturn("test-rpc");
        socket.onConnect(activeSession);
    }

    @Test
    void staleSessionCloseIsNotForwardedToHandler() {
        socket.onClose(staleSession, StatusCode.ABNORMAL, "stale");

        verify(handler, never()).onClose(anyBoolean(), anyInt(), anyString());
    }

    @Test
    void staleSessionErrorIsNotForwardedToHandler() {
        socket.onError(staleSession, new IllegalStateException("stale"));

        verify(handler, never()).onError(any());
    }

    @Test
    void activeSessionCloseIsForwardedToHandler() {
        socket.onClose(activeSession, StatusCode.ABNORMAL, "gone");

        verify(handler, times(1)).onClose(false, StatusCode.ABNORMAL, "gone");
    }
}
