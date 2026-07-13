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
package org.openhab.binding.xsense.internal.discovery;

import static org.openhab.binding.xsense.internal.XSenseBindingConstants.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.xsense.internal.XSenseBindingConstants;
import org.openhab.binding.xsense.internal.api.XSenseDtoUtil;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Device;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.House;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Station;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.StationList;
import org.openhab.binding.xsense.internal.handler.XSenseAccountHandler;
import org.openhab.binding.xsense.internal.handler.XSenseHomeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link XSenseCloudDiscoveryService} discovers the full account tree from the cloud
 * inventory: homes below the account, and stations with their sensors below an already added home.
 * Discovery is therefore a two-step process (account &rarr; home, then home &rarr; stations +
 * sensors together) rather than gating each level behind its own parent; each poll re-publishes
 * every level whose home already exists.
 *
 * @author Markus Michels - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = ThingHandlerService.class)
@NonNullByDefault
public class XSenseCloudDiscoveryService extends AbstractThingHandlerDiscoveryService<XSenseAccountHandler> {

    private static final int SCAN_TIMEOUT_SEC = 30;
    private static final Set<ThingTypeUID> DISCOVERABLE_THING_TYPES = Set.of(THING_TYPE_HOME, THING_TYPE_STATION,
            THING_TYPE_SMOKE, THING_TYPE_CO, THING_TYPE_SMOKECO, THING_TYPE_HEAT, THING_TYPE_WATER,
            THING_TYPE_THERMOHYGRO);

    private final Logger logger = LoggerFactory.getLogger(XSenseCloudDiscoveryService.class);

    public XSenseCloudDiscoveryService() {
        super(XSenseAccountHandler.class, DISCOVERABLE_THING_TYPES, SCAN_TIMEOUT_SEC, true);
    }

    @Override
    public void initialize() {
        // Registered as a lambda (not "this") so this @Component class doesn't itself implement
        // CloudDataListener - openHAB core's BaseThingHandlerFactory would otherwise try to
        // register this ThingHandlerService as an OSGi service under that unrelated interface too
        // (getAllInterfaces() walks all interfaces, not just ThingHandlerService subtypes),
        // which fails at registration time (nested-interface canonical name mismatch).
        thingHandler.setCloudDataListener(this::publishResults);
        super.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        thingHandler.setCloudDataListener(null);
        removeOlderResults(Instant.now(), thingHandler.getThing().getUID());
    }

    @Override
    protected void startScan() {
        // Runs on the scan's own background thread (framework-managed, bounded by
        // SCAN_TIMEOUT_SEC), so it is safe to block here for the cloud round-trip - unlike
        // requestRefresh(), which is fire-and-forget and would leave publishResults() running
        // against the stale pre-refresh inventory.
        thingHandler.pollNow();
        publishResults();
    }

    /**
     * Publishes discovery results for every level whose parent thing already exists: homes are
     * always published below the account, stations and their sensors together below an already
     * added home (see {@link #publishStation}).
     */
    private void publishResults() {
        String logId = "xsense-" + thingHandler.getAccountId();
        ThingUID accountUID = thingHandler.getThing().getUID();
        Map<String, StationList> stationsByHouseId = thingHandler.getStationsByHouseId();
        List<House> houses = thingHandler.getHouses();
        logger.debug("{}: publishing discovery results for {} house(s)", logId, houses.size());
        for (House house : houses) {
            String houseId = house.houseId;
            if (houseId == null) {
                continue;
            }
            publishHome(accountUID, houseId, house);
            ThingUID homeUID = findChildThingUID(thingHandler.getThing().getThings(), houseId);
            if (homeUID == null) {
                logger.debug("{}: house {}: home thing not added yet, skipping its stations", logId, houseId);
                continue;
            }
            StationList stationList = stationsByHouseId.get(houseId);
            List<Station> stations = stationList != null ? stationList.stations : null;
            if (stations == null || stations.isEmpty()) {
                logger.debug("{}: house {}: no stations in the last inventory poll", logId, houseId);
                continue;
            }
            logger.debug("{}: house {}: publishing {} station(s)", logId, houseId, stations.size());
            for (Station station : stations) {
                publishStation(homeUID, houseId, house, station);
            }
        }
    }

    /**
     * Publishes a home discovery result below the account. Homes have no prerequisite and are
     * therefore always published once they appear in the account's house list.
     */
    private void publishHome(ThingUID accountUID, String houseId, House house) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_HOUSE_ID, houseId);
        String houseName = house.houseName;
        putIfPresent(properties, PROPERTY_HOUSE_NAME, houseName);

        ThingUID homeUID = new ThingUID(THING_TYPE_HOME, accountUID, houseId);
        String label = "@text/discovery.home.label [ \"%s\" ]".formatted(XSenseDtoUtil.orDefault(houseName, houseId));
        thingDiscovered(DiscoveryResultBuilder.create(homeUID).withBridge(accountUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(CONFIG_HOUSE_ID).build());
    }

    /**
     * Publishes a station discovery result below the given (already existing) home thing, together
     * with its sensor devices - both levels are published in the same step so approving a home
     * surfaces the full station + sensor tree at once instead of a third discovery round.
     */
    private void publishStation(ThingUID homeUID, String houseId, House house, Station station) {
        String stationSn = station.stationSn;
        if (stationSn == null) {
            logger.debug("xsense-{}: station without a serial number in house {}, skipping",
                    thingHandler.getAccountId(), houseId);
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, stationSn);
        properties.put(CONFIG_STATION_SN, stationSn);
        String category = station.category;
        putIfPresent(properties, Thing.PROPERTY_MODEL_ID, category);
        String houseName = house.houseName;
        putIfPresent(properties, PROPERTY_HOUSE_NAME, houseName);
        String homeName = XSenseDtoUtil.orDefault(houseName, houseId);

        ThingUID stationUID = new ThingUID(THING_TYPE_STATION, homeUID, stationSn);
        String stationName = station.stationName;
        String modelId = XSenseDtoUtil.orDefault(category, STATION_TYPE_SBS50);
        String label = "@text/discovery.station.label [ \"%s\", \"%s\", \"%s\" ]".formatted(homeName,
                XSenseDtoUtil.orDefault(stationName, stationSn), modelId);
        thingDiscovered(DiscoveryResultBuilder.create(stationUID).withBridge(homeUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(Thing.PROPERTY_SERIAL_NUMBER).build());

        publishDevices(homeName, station, stationUID);
    }

    /**
     * Publishes the sensor devices of a station. Published unconditionally alongside the station
     * itself (see {@link #publishStation}) rather than gated behind the station thing being added,
     * so approving a home surfaces its whole station + sensor tree in one discovery step.
     */
    private void publishDevices(String homeName, Station station, ThingUID stationUID) {
        String logId = "xsense-" + station.stationSn;
        List<Device> devices = station.devices;
        if (devices == null || devices.isEmpty()) {
            logger.debug("{}: no devices in the last inventory poll", logId);
            return;
        }
        logger.debug("{}: publishing {} device(s)", logId, devices.size());
        for (Device device : devices) {
            String deviceSn = device.deviceSn;
            String deviceType = device.deviceType;
            if (deviceSn == null || deviceType == null) {
                continue;
            }
            ThingTypeUID thingTypeUID = XSenseBindingConstants.thingTypeForDeviceType(deviceType);
            if (thingTypeUID == null) {
                logger.debug(
                        "{}: unsupported X-Sense device model {} (SN {}), please report it to the binding developer",
                        logId, deviceType, deviceSn);
                continue;
            }
            Map<String, Object> properties = new HashMap<>();
            properties.put(Thing.PROPERTY_SERIAL_NUMBER, deviceSn);
            properties.put(CONFIG_DEVICE_SN, deviceSn);
            properties.put(Thing.PROPERTY_MODEL_ID, deviceType);
            putIfPresent(properties, PROPERTY_ROOM_NAME, device.roomName);

            String deviceName = device.deviceName;
            String label = "@text/discovery.device.label [ \"%s\", \"%s\", \"%s\" ]".formatted(homeName,
                    XSenseDtoUtil.orDefault(deviceName, deviceSn), deviceType);
            thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(thingTypeUID, stationUID, deviceSn))
                    .withBridge(stationUID).withLabel(label).withProperties(properties)
                    .withRepresentationProperty(Thing.PROPERTY_SERIAL_NUMBER).build());
        }
    }

    /**
     * Adds the key/value pair only when the value is present, since the cloud does not guarantee
     * every descriptive field is set.
     */
    private static void putIfPresent(Map<String, Object> properties, String key, @Nullable String value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    /**
     * Finds the thing UID of the home thing with the given houseId among the account's children.
     */
    private static @Nullable ThingUID findChildThingUID(List<Thing> things, String houseId) {
        for (Thing thing : things) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof XSenseHomeHandler homeHandler && houseId.equals(homeHandler.getHouseId())) {
                return thing.getUID();
            }
        }
        return null;
    }
}
