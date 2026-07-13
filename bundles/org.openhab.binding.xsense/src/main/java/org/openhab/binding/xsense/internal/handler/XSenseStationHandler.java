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

import static org.openhab.binding.xsense.internal.XSenseBindingConstants.CHANNEL_PATH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.xsense.internal.XSenseBindingConstants;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Device;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Station;
import org.openhab.binding.xsense.internal.config.XSenseStationConfiguration;
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
 * The {@link XSenseStationHandler} represents an X-Sense base station (SBS50). It receives the
 * station state from the home handler and propagates the attached device list to its sensor
 * child handlers.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseStationHandler extends BaseBridgeHandler {

    // Written in initialize(), read via getStationSn() from the home handler's update path
    private volatile XSenseStationConfiguration config = new XSenseStationConfiguration();
    private final XSenseChannelState channelState = new XSenseChannelState();
    private final XSenseChannelLabeler labeler = new XSenseChannelLabeler();

    public XSenseStationHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        config = getConfigAs(XSenseStationConfiguration.class);
        if (config.stationSn.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error-station-sn");
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

    public String getStationSn() {
        return config.stationSn;
    }

    /**
     * Requests an inventory refresh via the home handler.
     */
    public void requestRefresh() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof XSenseHomeHandler homeHandler) {
            homeHandler.requestRefresh();
        }
    }

    /**
     * Applies the current inventory data to this thing: status, properties, path channel and
     * propagation to child sensor handlers. This is the single state-application entry point for
     * the station level, called by the home handler after each inventory poll. It is intentionally
     * source-agnostic: a later MQTT/WSS live-update phase can feed presence/config changes through
     * the same method instead of introducing a parallel update path.
     *
     * @param station the station data, or null when the station is no longer part of the home
     * @param homePath the hierarchy path of the home this station belongs to
     */
    public void updateFromInventory(@Nullable Station station, XSensePath homePath) {
        if (station == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, "@text/offline.station-not-found");
            return;
        }
        XSensePath path = homePath.withStation(config.stationSn, station.stationName);
        updateProperties(station, path);
        applyChannelLabels(station.stationName);
        Integer onLine = station.onLine;
        if (onLine != null && onLine == 1) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "@text/offline.station-offline");
        }
        updateChannel(CHANNEL_PATH, new StringType(path.toJson()));
        notifySensorHandlers(station.devices, path);
    }

    private void updateProperties(Station station, XSensePath path) {
        Map<String, String> properties = new HashMap<>();
        String category = station.category;
        if (category != null) {
            properties.put(Thing.PROPERTY_MODEL_ID, category);
        }
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, config.stationSn);
        properties.put(Thing.PROPERTY_VENDOR, "X-Sense");
        properties.put(XSenseBindingConstants.PROPERTY_UNIQUE_ID, path.getUniqueId());
        updateProperties(properties);
    }

    private void notifySensorHandlers(@Nullable List<Device> devices, XSensePath path) {
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof XSenseSensorHandler sensorHandler) {
                sensorHandler.updateFromInventory(findDevice(devices, sensorHandler.getDeviceSn()), path);
            }
        }
    }

    private static @Nullable Device findDevice(@Nullable List<Device> devices, String deviceSn) {
        if (devices != null) {
            for (Device device : devices) {
                if (deviceSn.equals(device.deviceSn)) {
                    return device;
                }
            }
        }
        return null;
    }

    private void applyChannelLabels(@Nullable String name) {
        List<Channel> channels = labeler.relabel(getThing(), name);
        if (channels != null) {
            updateThing(editThing().withChannels(channels).build());
        }
    }

    private void updateChannel(String channelId, State state) {
        if (channelState.update(channelId, state)) {
            updateState(channelId, state);
        }
    }
}
