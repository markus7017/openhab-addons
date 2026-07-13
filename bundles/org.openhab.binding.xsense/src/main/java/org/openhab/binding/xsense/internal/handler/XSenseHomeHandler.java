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

import static org.openhab.binding.xsense.internal.XSenseBindingConstants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.House;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Station;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.StationList;
import org.openhab.binding.xsense.internal.config.XSenseHomeConfiguration;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;

/**
 * The {@link XSenseHomeHandler} represents a home (house) of the X-Sense account. It receives the
 * house state from the account handler's inventory polls and propagates the station list to its
 * base station child handlers.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseHomeHandler extends BaseBridgeHandler {

    // Written in initialize(), read via getHouseId() from the account handler's poll thread
    private volatile XSenseHomeConfiguration config = new XSenseHomeConfiguration();
    // Latest house data from the inventory poll, needed by station handlers for shadow commands
    private volatile @Nullable House house;
    private final XSenseChannelState channelState = new XSenseChannelState();
    private final XSenseChannelLabeler labeler = new XSenseChannelLabeler();

    public XSenseHomeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        config = getConfigAs(XSenseHomeConfiguration.class);
        if (config.houseId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error-house-id");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        requestRefresh();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            channelState.invalidate(channelUID.getId());
            requestRefresh();
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            requestRefresh();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    public String getHouseId() {
        return config.houseId;
    }

    /**
     * Returns the house data of the last inventory poll, or null before the first poll or after
     * the house disappeared from the account.
     */
    public @Nullable House getHouse() {
        return house;
    }

    /**
     * Returns the account handler this home belongs to, or null while the bridge is not set.
     */
    public @Nullable XSenseAccountHandler getAccountHandler() {
        Bridge bridge = getBridge();
        return bridge != null && bridge.getHandler() instanceof XSenseAccountHandler accountHandler ? accountHandler
                : null;
    }

    /**
     * Requests an inventory refresh from the account handler.
     */
    public void requestRefresh() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof XSenseAccountHandler accountHandler) {
            accountHandler.requestRefresh();
        }
    }

    /**
     * Applies the current inventory data to this thing: status, properties, path channel and
     * propagation to child station handlers. This is the single state-application entry point for
     * the home level, called by the account handler after each inventory poll. It is intentionally
     * source-agnostic: a later MQTT/WSS live-update phase can feed presence/config changes through
     * the same method instead of introducing a parallel update path.
     *
     * @param house the house data, or null when the house is no longer part of the account
     * @param stationList the stations of the house, or null when unavailable
     * @param accountId the id of the owning account (thing UID id, not the email), used to build
     *            the path channel
     */
    public void updateFromInventory(@Nullable House house, @Nullable StationList stationList, String accountId) {
        this.house = house;
        if (house == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, "@text/offline.home-not-found");
            return;
        }
        XSensePath path = XSensePath.account(accountId).withHome(config.houseId, house.houseName);
        updateProperties(house, path);
        applyChannelLabels(house.houseName);
        updateStatus(ThingStatus.ONLINE);
        updateChannel(CHANNEL_PATH, new StringType(path.toJson()));
        notifyStationHandlers(stationList, path);
    }

    private void applyChannelLabels(@Nullable String name) {
        List<Channel> channels = labeler.relabel(getThing(), name);
        if (channels != null) {
            updateThing(editThing().withChannels(channels).build());
        }
    }

    private void updateProperties(House house, XSensePath path) {
        Map<String, String> properties = new HashMap<>();
        String houseName = house.houseName;
        if (houseName != null) {
            properties.put(PROPERTY_HOUSE_NAME, houseName);
        }
        properties.put(PROPERTY_HOUSE_ID, config.houseId);
        properties.put(PROPERTY_UNIQUE_ID, path.getUniqueId());
        updateProperties(properties);
    }

    private void notifyStationHandlers(@Nullable StationList stationList, XSensePath path) {
        List<Station> stations = stationList != null ? stationList.stations : null;
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof XSenseStationHandler stationHandler) {
                stationHandler.updateFromInventory(findStation(stations, stationHandler.getStationSn()), path);
            }
        }
    }

    private static @Nullable Station findStation(@Nullable List<Station> stations, String stationSn) {
        if (stations != null) {
            for (Station station : stations) {
                if (stationSn.equals(station.stationSn)) {
                    return station;
                }
            }
        }
        return null;
    }

    private void updateChannel(String channelId, State state) {
        if (channelState.update(channelId, state)) {
            updateState(channelId, state);
        }
    }
}
