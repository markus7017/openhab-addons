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

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@NonNullByDefault
public class XSensePathTest {

    private final Gson gson = new Gson();

    @Test
    public void accountOnlyPath() {
        XSensePath path = XSensePath.account("user@example.com");
        assertEquals("{\"account\":\"user@example.com\"}", path.toJson());
        assertEquals("user@example.com", path.getUniqueId());
    }

    @Test
    public void homePathContainsIdAndName() {
        XSensePath path = XSensePath.account("user@example.com").withHome("h1", "My Home");
        assertEquals("{\"account\":\"user@example.com\",\"home\":{\"id\":\"h1\",\"name\":\"My Home\"}}", path.toJson());
        assertEquals("user@example.com/h1", path.getUniqueId());
    }

    @Test
    public void fullDevicePathIsParsableJson() {
        XSensePath path = XSensePath.account("user@example.com").withHome("h1", "My Home")
                .withStation("SN123", "Hallway").withDevice("DEV1", "Kitchen");

        JsonObject json = gson.fromJson(path.toJson(), JsonObject.class);
        assertNotNull(json);
        assertEquals("user@example.com", json.get("account").getAsString());
        assertEquals("h1", json.getAsJsonObject("home").get("id").getAsString());
        assertEquals("My Home", json.getAsJsonObject("home").get("name").getAsString());
        assertEquals("SN123", json.getAsJsonObject("station").get("sn").getAsString());
        assertEquals("Hallway", json.getAsJsonObject("station").get("name").getAsString());
        assertEquals("DEV1", json.getAsJsonObject("device").get("sn").getAsString());
        assertEquals("Kitchen", json.getAsJsonObject("device").get("name").getAsString());

        assertEquals("user@example.com/h1/SN123/DEV1", path.getUniqueId());
    }

    @Test
    public void missingNamesAreOmitted() {
        XSensePath path = XSensePath.account("user@example.com").withHome("h1", null).withStation("SN123", null);

        JsonObject json = gson.fromJson(path.toJson(), JsonObject.class);
        assertNotNull(json);
        assertFalse(json.getAsJsonObject("home").has("name"));
        assertFalse(json.getAsJsonObject("station").has("name"));
        assertFalse(json.has("device"));
    }

    @Test
    public void specialCharactersAreEscaped() {
        XSensePath path = XSensePath.account("user@example.com").withHome("h1", "Say \"Home\"");

        JsonObject json = gson.fromJson(path.toJson(), JsonObject.class);
        assertNotNull(json);
        assertEquals("Say \"Home\"", json.getAsJsonObject("home").get("name").getAsString());
    }
}
