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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonObject;

/**
 * The {@link XSensePath} describes where an entity is located in the account/home/station/device
 * hierarchy. It provides the JSON representation for the info#path channel and the internal
 * unique id of the entity.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSensePath {

    private static final String UNIQUE_ID_SEPARATOR = "/";

    private final String account;
    private final @Nullable String homeId;
    private final @Nullable String homeName;
    private final @Nullable String stationSn;
    private final @Nullable String stationName;
    private final @Nullable String deviceSn;
    private final @Nullable String deviceName;

    private XSensePath(String account, @Nullable String homeId, @Nullable String homeName, @Nullable String stationSn,
            @Nullable String stationName, @Nullable String deviceSn, @Nullable String deviceName) {
        this.account = account;
        this.homeId = homeId;
        this.homeName = homeName;
        this.stationSn = stationSn;
        this.stationName = stationName;
        this.deviceSn = deviceSn;
        this.deviceName = deviceName;
    }

    /**
     * Creates the root path for an account (account thing UID id, not the email address).
     */
    public static XSensePath account(String account) {
        return new XSensePath(account, null, null, null, null, null, null);
    }

    /**
     * Returns a new path with the home level appended; deeper levels are dropped, this builds a
     * path for a home entity itself or as the base for {@link #withStation}.
     */
    public XSensePath withHome(String homeId, @Nullable String homeName) {
        return new XSensePath(account, homeId, homeName, null, null, null, null);
    }

    /**
     * Returns a new path with the station level appended; deeper levels are dropped, this builds a
     * path for a station entity itself or as the base for {@link #withDevice}. Must be called on a
     * path that already has a home level.
     */
    public XSensePath withStation(String stationSn, @Nullable String stationName) {
        return new XSensePath(account, homeId, homeName, stationSn, stationName, null, null);
    }

    /**
     * Returns a new path with the device level appended, building the full path for a sensor
     * entity. Must be called on a path that already has home and station levels.
     */
    public XSensePath withDevice(String deviceSn, @Nullable String deviceName) {
        return new XSensePath(account, homeId, homeName, stationSn, stationName, deviceSn, deviceName);
    }

    /**
     * Returns the JSON representation for the info#path channel, e.g.
     * {"account":"a03c377df2","home":{"id":"h1","name":"Home"},"station":{"sn":"SN1","name":"Hall"},
     * "device":{"sn":"D1","name":"Kitchen"}}. Levels below the entity are omitted.
     */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("account", account);
        addLevel(json, "home", "id", homeId, homeName);
        addLevel(json, "station", "sn", stationSn, stationName);
        addLevel(json, "device", "sn", deviceSn, deviceName);
        return json.toString();
    }

    /**
     * Returns the internal unique id of the entity: the technical ids of all levels joined by '/'.
     */
    public String getUniqueId() {
        StringBuilder sb = new StringBuilder(account);
        appendIdIfPresent(sb, homeId);
        appendIdIfPresent(sb, stationSn);
        appendIdIfPresent(sb, deviceSn);
        return sb.toString();
    }

    private static void addLevel(JsonObject json, String level, String idKey, @Nullable String id,
            @Nullable String name) {
        if (id == null) {
            return;
        }
        JsonObject levelJson = new JsonObject();
        levelJson.addProperty(idKey, id);
        if (name != null) {
            levelJson.addProperty("name", name);
        }
        json.add(level, levelJson);
    }

    private static void appendIdIfPresent(StringBuilder sb, @Nullable String id) {
        if (id != null) {
            sb.append(UNIQUE_ID_SEPARATOR).append(id);
        }
    }
}
