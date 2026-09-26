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
package org.openhab.binding.shelly.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1PM;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiInterface;
import org.openhab.binding.shelly.internal.api.ShellyApiResult;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link ShellyBaseHandler#handleApiException}'s bounded 429 retry: the first throttled response
 * schedules exactly one retry cycle, and a second consecutive throttled response does not re-arm it, bounding
 * the retry to a single cycle instead of polling a throttled device every poll interval.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault({})
@SuppressWarnings("null")
class ShellyBaseHandlerThrottleRetryTest {

    @Test
    void firstThrottledResponseSchedulesOneRetry() throws Exception {
        ShellyBaseHandler handler = buildHandler();
        handler.scheduledUpdates = 0;

        boolean retry = invokeHandleApiException(handler, throttled());

        assertTrue(retry);
        assertEquals(2, handler.scheduledUpdates);
        verify(handler, never()).setThingOfflineAndDisconnect(any(), any(), any());
    }

    @Test
    void secondConsecutiveThrottledResponseDoesNotReArm() throws Exception {
        ShellyBaseHandler handler = buildHandler();
        handler.scheduledUpdates = 1;

        boolean retry = invokeHandleApiException(handler, throttled());

        assertTrue(retry);
        assertEquals(1, handler.scheduledUpdates);
        verify(handler, never()).setThingOfflineAndDisconnect(any(), any(), any());
    }

    private ShellyBaseHandler buildHandler() throws Exception {
        ShellyBaseHandler handler = mock(ShellyBaseHandler.class, CALLS_REAL_METHODS);
        ShellyApiInterface api = mock(ShellyApiInterface.class);
        ShellyDeviceProfile profile = new ShellyDeviceProfile(THING_TYPE_SHELLYPLUS1PM);
        profile.initialized = true;
        profile.alwaysOn = false;

        setField(handler, "api", api);
        setField(handler, "logger", LoggerFactory.getLogger(ShellyBaseHandler.class));
        handler.profile = profile;
        return handler;
    }

    private static ShellyApiException throttled() {
        ShellyApiResult result = ShellyApiResult.builder().httpCode(HttpStatus.TOO_MANY_REQUESTS_429).build();
        return new ShellyApiException(result);
    }

    private static boolean invokeHandleApiException(ShellyBaseHandler handler, ShellyApiException e) throws Exception {
        Method m = ShellyBaseHandler.class.getDeclaredMethod("handleApiException", ShellyApiException.class);
        m.setAccessible(true);
        return (boolean) m.invoke(handler, e);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getSuperclass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
