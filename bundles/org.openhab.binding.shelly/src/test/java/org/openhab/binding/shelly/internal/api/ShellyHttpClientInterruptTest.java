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
package org.openhab.binding.shelly.internal.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.config.ShellyApiConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyBindingRuntimeConfig;
import org.openhab.core.net.NetworkAddressService;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
class ShellyHttpClientInterruptTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptedRequestKeepsThreadInterruptFlag() throws Exception {
        ShellyHttpClient client = buildClient();

        assertThrows(ShellyApiException.class, () -> client.httpPost("/rpc", "{}"));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void interruptedGetRequestIsNotRetried() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        ShellyHttpClient client = buildClient(httpClient);

        assertThrows(ShellyApiException.class, () -> client.httpRequest("/status"));
        verify(httpClient, times(1)).newRequest(anyString());
    }

    private static ShellyHttpClient buildClient() throws Exception {
        return buildClient(mock(HttpClient.class));
    }

    private static ShellyHttpClient buildClient(HttpClient httpClient) throws Exception {
        Request request = mock(Request.class);
        when(request.method(anyString())).thenReturn(request);
        when(request.timeout(anyLong(), any(TimeUnit.class))).thenReturn(request);
        when(request.header(any(HttpHeader.class), anyString())).thenReturn(request);
        when(request.send()).thenThrow(new InterruptedException());
        when(request.getURI()).thenReturn(URI.create("http://192.168.1.2/rpc"));
        when(request.getMethod()).thenReturn("POST");

        when(httpClient.newRequest(anyString())).thenReturn(request);

        ShellyBindingConfiguration raw = ShellyBindingConfiguration
                .fromProperties(Map.of(ShellyBindingConfiguration.CONFIG_LOCAL_IP, "192.168.1.1"));
        ShellyBindingRuntimeConfig bindingConfig = new ShellyBindingRuntimeConfig(raw, 8080,
                mock(NetworkAddressService.class));
        ShellyApiConfiguration config = new ShellyApiConfiguration(bindingConfig, "test", "");
        return new ShellyHttpClient("test", config, httpClient);
    }
}
