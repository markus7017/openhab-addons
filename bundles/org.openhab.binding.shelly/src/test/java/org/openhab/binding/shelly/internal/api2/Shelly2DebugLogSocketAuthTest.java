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

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * @author Markus Michels - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault({})
class Shelly2DebugLogSocketAuthTest {

    private @Mock WebSocketClient webSocketClient;
    private @Mock Shelly2DebugLogListener listener;

    private Shelly2DebugLogSocket socket;

    @BeforeEach
    void setUp() {
        socket = new Shelly2DebugLogSocket("test", new InetSocketAddress("127.0.0.1", 80), webSocketClient, listener);
    }

    private ClientUpgradeRequest captureUpgradeRequest() throws Exception {
        ArgumentCaptor<ClientUpgradeRequest> captor = ArgumentCaptor.forClass(ClientUpgradeRequest.class);
        verify(webSocketClient).connect(any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void authHeaderIsSetOnUpgradeRequestWhenProvided() throws Exception {
        socket.connect("Digest username=\"admin\", response=\"abc\"");

        assertThat(captureUpgradeRequest().getHeader("Authorization"),
                is("Digest username=\"admin\", response=\"abc\""));
    }

    @Test
    void noAuthHeaderWhenNotProvided() throws Exception {
        socket.connect(null);

        assertThat(captureUpgradeRequest().getHeader("Authorization"), is(nullValue()));
    }
}
