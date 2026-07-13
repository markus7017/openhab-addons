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
package org.openhab.binding.xsense.internal.manager;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.xsense.internal.manager.XSenseManagerPage.Row;

@NonNullByDefault
public class XSenseManagerPageTest {

    @Test
    public void maskEmailHidesLocalPart() {
        assertEquals("u***@example.com", XSenseManagerPage.maskEmail("user@example.com"));
        assertEquals("***", XSenseManagerPage.maskEmail("u@x"));
        assertEquals("***", XSenseManagerPage.maskEmail("invalid"));
    }

    @Test
    public void escapeHandlesHtmlSpecialCharacters() {
        assertEquals("&lt;b&gt;&amp;&quot;", XSenseManagerPage.escape("<b>&\""));
    }

    @Test
    public void renderContainsRowDataAndActions() {
        Row account = new Row(0, "xsense:account:1", "account", "My Account", "ONLINE", "", "email=u***@example.com",
                "user@example.com", true);
        Row sensor = new Row(3, "xsense:smoke:1:sn:dev", "smoke", "Kitchen", "OFFLINE", "BRIDGE_OFFLINE",
                "deviceSn=DEV1", "user@example.com/h1/SN1/DEV1", false);

        String html = XSenseManagerPage.render(List.of(account, sensor), "/xsense/manager");

        assertTrue(html.contains("My Account"));
        assertTrue(html.contains("Kitchen"));
        assertTrue(html.contains("BRIDGE_OFFLINE"));
        assertTrue(html.contains(XSenseManagerPage.LOGO_URL));
        assertTrue(html.contains("value=\"rescan\""));
        assertTrue(html.contains("value=\"reconnect\""));
        // Actions only on the account row: exactly one rescan and one reconnect form
        assertEquals(2, html.split("<form", -1).length - 1);
    }

    @Test
    public void renderEscapesUntrustedContent() {
        Row row = new Row(0, "xsense:account:1", "account", "<script>alert(1)</script>", "ONLINE", "", "", "", false);
        String html = XSenseManagerPage.render(List.of(row), "/xsense/manager");
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
