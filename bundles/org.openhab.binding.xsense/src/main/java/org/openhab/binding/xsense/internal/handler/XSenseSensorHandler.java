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

import static org.openhab.binding.xsense.internal.XSenseBindingConstants.CHANNEL_MUTE;
import static org.openhab.binding.xsense.internal.XSenseBindingConstants.CHANNEL_PATH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.xsense.internal.XSenseBindingConstants;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Device;
import org.openhab.binding.xsense.internal.config.XSenseDeviceConfiguration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link XSenseSensorHandler} represents a sensor attached to an X-Sense base station (smoke,
 * CO, heat, water leak or thermo-hygrometer). In the current phase it maintains the thing status
 * and properties from the cloud inventory; live channel states follow with the MQTT shadow phase.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseSensorHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(XSenseSensorHandler.class);

    // Written in initialize(), read via getDeviceSn() from the station handler's update path
    private volatile XSenseDeviceConfiguration config = new XSenseDeviceConfiguration();
    private final XSenseChannelState channelState = new XSenseChannelState();
    private final XSenseChannelLabeler labeler = new XSenseChannelLabeler();

    public XSenseSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(XSenseDeviceConfiguration.class);
        if (config.deviceSn.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error-device-sn");
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
            return;
        }
        if (CHANNEL_MUTE.equals(channelUID.getId()) && command == OnOffType.ON) {
            scheduler.execute(this::sendMute);
            // Momentary action: there is no cloud readback state, so bounce the switch back to
            // OFF immediately rather than latching it on.
            updateState(channelUID, OnOffType.OFF);
        }
    }

    /**
     * Mutes an active alarm on this device via the station handler. A failure only logs a warning
     * so the thing stays usable.
     */
    private void sendMute() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof XSenseStationHandler stationHandler) {
            stationHandler.muteDevice(config.deviceSn, getThing().getProperties().get(Thing.PROPERTY_MODEL_ID));
        } else {
            logger.warn("xsense-{}: cannot mute, station not available", config.deviceSn);
        }
    }

    /**
     * Mutes this device if it has a mute channel, used by the home-level mute-all cascade. Devices
     * without a control#mute channel (door, motion, keypad, thermo-hygro) are silently skipped.
     */
    void muteIfSupported() {
        if (getThing().getChannel(CHANNEL_MUTE) != null) {
            sendMute();
        }
    }

    /**
     * Requests an inventory refresh via the station handler. Called on initialization too, since
     * otherwise a newly added sensor thing would stay UNKNOWN until the next unrelated poll cycle
     * happens to fire.
     */
    private void requestRefresh() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof XSenseStationHandler stationHandler) {
            stationHandler.requestRefresh();
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    public String getDeviceSn() {
        return config.deviceSn;
    }

    /**
     * Applies the current inventory data to this thing: status, properties and path channel. This
     * is the single state-application entry point for the sensor level, called by the station
     * handler after each inventory poll. It is intentionally source-agnostic: the upcoming MQTT/WSS
     * live-update phase will feed live alarm/measurement channels separately, but presence and
     * property changes are expected to keep flowing through this same method.
     *
     * @param device the device data, or null when the device is no longer attached to the station
     * @param stationPath the hierarchy path of the station this sensor belongs to
     */
    public void updateFromInventory(@Nullable Device device, XSensePath stationPath) {
        if (device == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, "@text/offline.device-not-found");
            return;
        }
        XSensePath path = stationPath.withDevice(config.deviceSn, device.deviceName);
        updateProperties(device, path);
        applyChannelLabels(device.deviceName);
        updateStatus(ThingStatus.ONLINE);
        updateChannel(CHANNEL_PATH, new StringType(path.toJson()));
    }

    private void applyChannelLabels(@Nullable String name) {
        List<Channel> channels = labeler.relabel(getThing(), name);
        if (channels != null) {
            updateThing(editThing().withChannels(channels).build());
        }
    }

    private void updateProperties(Device device, XSensePath path) {
        Map<String, String> properties = new HashMap<>();
        String deviceType = device.deviceType;
        if (deviceType != null) {
            properties.put(Thing.PROPERTY_MODEL_ID, deviceType);
        }
        String roomName = device.roomName;
        if (roomName != null) {
            properties.put(XSenseBindingConstants.PROPERTY_ROOM_NAME, roomName);
        }
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, config.deviceSn);
        properties.put(Thing.PROPERTY_VENDOR, "X-Sense");
        properties.put(XSenseBindingConstants.PROPERTY_UNIQUE_ID, path.getUniqueId());
        updateProperties(properties);
    }

    private void updateChannel(String channelId, State state) {
        if (channelState.update(channelId, state)) {
            updateState(channelId, state);
        }
    }
}
