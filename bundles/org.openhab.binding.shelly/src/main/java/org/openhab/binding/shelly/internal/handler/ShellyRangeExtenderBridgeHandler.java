/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.shelly.internal.config.ShellyRangeExtenderConfiguration;
import org.openhab.binding.shelly.internal.provider.ShellyTranslationProvider;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ShellyRangeExtenderBridgeHandler} implements access linked Shelly devices for a Shelly hub device with enabled
 * range extender
 *
 * @author Markus Michels - Initial contribution
 *
 */
@NonNullByDefault
public class ShellyRangeExtenderBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(ShellyRangeExtenderBridgeHandler.class);
    private final String thingId;
    private ShellyRangeExtenderConfiguration config;

    public ShellyRangeExtenderBridgeHandler(Bridge bridge, ShellyTranslationProvider translationProvider) {
        super(bridge);

        thingId = getThing().getUID().getId();
        config = getConfigAs(ShellyRangeExtenderConfiguration.class);
    }

    /**
     * Initializes the bridge.
     */
    @Override
    public void initialize() {
    }

    /**
     * This routine is called every time the Thing configuration has been changed
     */

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);

        try {
            stateChanged(ThingStatus.UNKNOWN, ThingStatusDetail.HANDLER_CONFIGURATION_PENDING,
                    "Thing config updated, re-initialize");
            config = getConfigAs(ShellyRangeExtenderConfiguration.class);
            // initializeThing("handleConfigurationUpdate");
        } catch (Exception e) {
            // logger.warn("{}: {}", messages.get("init-failed", e.toString()), thingId);
        }

    }

    /**
     * Empty handleCommand for Account Thing
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            return;
        }
        String channelId = channelUID.getIdWithoutGroup();
        logger.debug("{}: Undefined command '{}' for channel {}", thingId, command, channelId);
    }

    /**
     * Notify all listeners about status changes
     *
     * @param status New status
     * @param detail Status details
     * @param message Message
     */
    void stateChanged(ThingStatus status, ThingStatusDetail detail, String message) {
        updateStatus(status, detail, message);
        // this.vehicleInformationListeners.forEach(discovery -> discovery.stateChanged(status, detail, message));
    }

}
