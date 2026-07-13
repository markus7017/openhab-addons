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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.xsense.internal.XSenseBindingConstants;
import org.openhab.binding.xsense.internal.api.XSenseApiClient;
import org.openhab.binding.xsense.internal.api.XSenseApiException;
import org.openhab.binding.xsense.internal.api.XSenseSafeMode;
import org.openhab.binding.xsense.internal.api.XSenseShadowRequests;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.House;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.Station;
import org.openhab.binding.xsense.internal.api.dto.XSenseApiDto.StationList;
import org.openhab.binding.xsense.internal.config.XSenseAccountConfiguration;
import org.openhab.binding.xsense.internal.config.XSenseBindingConfiguration;
import org.openhab.binding.xsense.internal.discovery.XSenseCloudDiscoveryService;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link XSenseAccountHandler} manages the connection to the X-Sense cloud account: it owns the
 * API client, performs the login and polls the house/station/device inventory.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseAccountHandler extends BaseBridgeHandler {

    /**
     * Listener notified after each successful inventory refresh (used by the discovery service).
     */
    public interface CloudDataListener {
        void onCloudDataChanged();
    }

    /**
     * Immutable snapshot of the cloud inventory so that houses and stations are always consistent.
     */
    private static class Inventory {
        static final Inventory EMPTY = new Inventory(List.of(), Map.of());

        final List<House> houses;
        final Map<String, StationList> stationsByHouseId;

        Inventory(List<House> houses, Map<String, StationList> stationsByHouseId) {
            this.houses = houses;
            this.stationsByHouseId = stationsByHouseId;
        }
    }

    private static final long RETRY_DELAY_MIN_SEC = 30;
    private static final long RETRY_DELAY_MAX_SEC = 900;

    private final Logger logger = LoggerFactory.getLogger(XSenseAccountHandler.class);
    private final Bundle bundle = FrameworkUtil.getBundle(XSenseAccountHandler.class);
    private final XSenseApiClient apiClient;
    private final Supplier<XSenseBindingConfiguration> bindingConfig;
    private final TranslationProvider i18nProvider;
    private final LocaleProvider localeProvider;

    private volatile XSenseAccountConfiguration config = new XSenseAccountConfiguration();
    // Only accessed from the synchronized connect()/poll() methods
    private long retryDelay = RETRY_DELAY_MIN_SEC;

    // Written under the handler monitor, but read from dispose() and other threads
    private volatile boolean disposed;
    private volatile @Nullable ScheduledFuture<?> pollJob;
    private volatile @Nullable ScheduledFuture<?> reconnectJob;
    private volatile @Nullable CloudDataListener cloudDataListener;

    // Latest inventory snapshot, replaced atomically after each poll
    private volatile Inventory inventory = Inventory.EMPTY;

    public XSenseAccountHandler(Bridge bridge, HttpClient httpClient,
            Supplier<XSenseBindingConfiguration> bindingConfig, TranslationProvider i18nProvider,
            LocaleProvider localeProvider) {
        super(bridge);
        apiClient = new XSenseApiClient(httpClient);
        this.bindingConfig = bindingConfig;
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;
    }

    @Override
    public void initialize() {
        disposed = false;
        config = getConfigAs(XSenseAccountConfiguration.class);
        if (config.email.isBlank() || config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.config-error-credentials");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        applyLabel();
        scheduler.execute(this::connect);
    }

    /**
     * Sets the thing label to "X-Sense Account (&lt;account id&gt;)" so the account is
     * identifiable in the UI without exposing the email address.
     */
    private void applyLabel() {
        String accountId = getAccountId();
        Locale locale = localeProvider.getLocale();
        String defaultLabel = "X-Sense Account (" + accountId + ")";
        String label = i18nProvider.getText(bundle, "account.label", defaultLabel, locale, accountId);
        if (label == null) {
            label = defaultLabel;
        }
        if (!label.equals(getThing().getLabel())) {
            updateThing(editThing().withLabel(label).build());
        }
    }

    @Override
    public void dispose() {
        // Set first: a poll()/connect() still in flight must not schedule new jobs afterwards
        disposed = true;
        stopJob(pollJob);
        pollJob = null;
        stopJob(reconnectJob);
        reconnectJob = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::poll);
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(XSenseCloudDiscoveryService.class);
    }

    public void setCloudDataListener(@Nullable CloudDataListener listener) {
        cloudDataListener = listener;
    }

    /**
     * Returns the houses of the last successful inventory poll.
     */
    public List<House> getHouses() {
        return inventory.houses;
    }

    /**
     * Returns the stations (including attached devices) of the last successful poll, keyed by house id.
     */
    public Map<String, StationList> getStationsByHouseId() {
        return inventory.stationsByHouseId;
    }

    /**
     * Returns the id of this account used in path/unique ids and log prefixes: the account thing's
     * UID id segment (e.g. "a03c377df2"), not the email address, so neither the UI nor the log
     * expose the full account email.
     */
    public String getAccountId() {
        return thing.getUID().getId();
    }

    /**
     * Log line prefix ("xsense-&lt;id&gt;") so all messages for one account can be grepped together,
     * e.g. across the account, home and station handlers.
     */
    private String logId() {
        return "xsense-" + getAccountId();
    }

    /**
     * Triggers an inventory refresh in the background, e.g. on child initialization or a manual
     * rescan request from the XSense Manager. Fire-and-forget: callers that need the refreshed
     * inventory before continuing must use {@link #pollNow()} instead.
     */
    public void requestRefresh() {
        scheduler.execute(this::poll);
    }

    /**
     * Runs an inventory refresh synchronously on the calling thread and returns once it completes.
     * Used by the discovery service's manual scan, which otherwise runs {@link #requestRefresh()}
     * asynchronously and would publish results from the stale inventory before the new poll
     * finishes.
     */
    public void pollNow() {
        poll();
    }

    /**
     * Triggers a full reconnect including a new cloud login, e.g. from the XSense Manager.
     */
    public void reconnect() {
        scheduler.execute(this::connect);
    }

    /**
     * Returns whether the cloud session is currently established (for the XSense Manager).
     */
    public boolean isLoggedIn() {
        return apiClient.isLoggedIn();
    }

    /**
     * Requests a new safe mode (arm/disarm) for a base station by writing the appMode device
     * shadow through the AWS IoT REST API, the same request the X-Sense app issues. The new state
     * is not applied optimistically; it is confirmed by the next inventory poll.
     *
     * @param house the house the station belongs to (provides the AWS IoT region)
     * @param stationSn serial number of the station
     * @param mode the requested safe mode
     * @throws XSenseApiException if the house lacks the AWS IoT region or the request fails
     */
    public void setStationSafeMode(House house, String stationSn, XSenseSafeMode mode) throws XSenseApiException {
        String mqttRegion = house.mqttRegion;
        if (mqttRegion == null || mqttRegion.isBlank()) {
            throw new XSenseApiException("Home provides no AWS IoT region (mqttRegion)");
        }
        String thingName = XSenseShadowRequests.stationThingName(XSenseBindingConstants.STATION_TYPE_SBS50, stationSn);
        String body = XSenseShadowRequests.appModeDesiredState(mode, stationSn, apiClient.getUserId());
        apiClient.updateStationShadow(mqttRegion, thingName, XSenseShadowRequests.SHADOW_NAME_APP_MODE, body);
    }

    /**
     * Performs the Cognito login and, on success, starts inventory polling. On failure, either
     * gives up (bad credentials) or schedules a reconnect with exponential backoff.
     */
    private synchronized void connect() {
        if (disposed) {
            return;
        }
        String logId = logId();
        try {
            logger.debug("{}: logging in", logId);
            apiClient.login(config.email, config.password);
            retryDelay = RETRY_DELAY_MIN_SEC;
            startPolling();
        } catch (XSenseApiException e) {
            logger.debug("{}: login failed: {}", logId, e.getMessage());
            if (e.isAuthenticationFailure()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/offline.config-error-login [\"" + e.getMessage() + "\"]");
                // No automatic retry: a new login only succeeds after a configuration change
                return;
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.communication-error [\"" + e.getMessage() + "\"]");
            scheduleReconnect();
        }
    }

    private void startPolling() {
        if (disposed) {
            return;
        }
        stopJob(pollJob);
        pollJob = scheduler.scheduleWithFixedDelay(this::poll, 0, effectiveRefreshInterval(), TimeUnit.SECONDS);
    }

    /**
     * Returns the polling interval: the thing configuration when set, otherwise the binding
     * configuration default.
     */
    private long effectiveRefreshInterval() {
        int thingInterval = config.refreshInterval;
        if (thingInterval > 0) {
            return Math.max(XSenseBindingConfiguration.MIN_REFRESH_INTERVAL_SEC, thingInterval);
        }
        return bindingConfig.get().refreshInterval;
    }

    /**
     * Fetches the current house/station inventory, replaces the snapshot atomically and notifies
     * home handlers and the discovery service. On an authentication failure, stops polling and
     * schedules a reconnect; other failures leave polling running for the next attempt.
     */
    private synchronized void poll() {
        if (disposed || !apiClient.isLoggedIn()) {
            // Initial login still pending or session lost, connect()/reconnect handles this case
            return;
        }
        String logId = logId();
        try {
            List<House> newHouses = apiClient.getHouses();
            logger.debug("{}: fetched {} house(s): {}", logId, newHouses.size(),
                    newHouses.stream().map(h -> h.houseId).toList());
            Map<String, StationList> newStations = new HashMap<>();
            for (House house : newHouses) {
                String houseId = house.houseId;
                if (houseId != null) {
                    StationList stationList = apiClient.getStations(houseId);
                    List<Station> stations = stationList.stations;
                    logger.debug("{}: house {}: fetched {} station(s): {}", logId, houseId,
                            stations != null ? stations.size() : 0,
                            stations != null ? stations.stream().map(s -> s.stationSn).toList() : List.of());
                    newStations.put(houseId, stationList);
                }
            }
            inventory = new Inventory(List.copyOf(newHouses), Map.copyOf(newStations));
            retryDelay = RETRY_DELAY_MIN_SEC;
            updateStatus(ThingStatus.ONLINE);
            notifyHomeHandlers();
            CloudDataListener listener = cloudDataListener;
            if (listener != null) {
                listener.onCloudDataChanged();
            }
        } catch (XSenseApiException e) {
            logger.debug("{}: inventory poll failed: {}", logId, e.getMessage());
            if (e.isAuthenticationFailure()) {
                stopJob(pollJob);
                pollJob = null;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "@text/offline.communication-error-session [\"" + e.getMessage() + "\"]");
                scheduleReconnect();
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "@text/offline.communication-error [\"" + e.getMessage() + "\"]");
            }
        }
    }

    /**
     * Pushes the latest inventory snapshot to every home child handler, matched by houseId.
     */
    private void notifyHomeHandlers() {
        Inventory snapshot = inventory;
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof XSenseHomeHandler homeHandler) {
                String houseId = homeHandler.getHouseId();
                homeHandler.updateFromInventory(findHouse(snapshot.houses, houseId),
                        snapshot.stationsByHouseId.get(houseId), getAccountId());
            }
        }
    }

    private static @Nullable House findHouse(List<House> houses, String houseId) {
        for (House house : houses) {
            if (houseId.equals(house.houseId)) {
                return house;
            }
        }
        return null;
    }

    /**
     * Schedules the next {@link #connect()} attempt with exponential backoff, doubling the delay
     * up to {@link #RETRY_DELAY_MAX_SEC} on every consecutive failure.
     */
    private void scheduleReconnect() {
        if (disposed) {
            return;
        }
        stopJob(reconnectJob);
        reconnectJob = scheduler.schedule(this::connect, retryDelay, TimeUnit.SECONDS);
        retryDelay = Math.min(retryDelay * 2, RETRY_DELAY_MAX_SEC);
    }

    private static void stopJob(@Nullable ScheduledFuture<?> job) {
        if (job != null) {
            job.cancel(true);
        }
    }
}
