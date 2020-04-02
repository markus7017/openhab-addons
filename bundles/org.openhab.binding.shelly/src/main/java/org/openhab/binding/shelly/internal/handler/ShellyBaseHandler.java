/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import static org.eclipse.smarthome.core.thing.Thing.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.util.ShellyUtils.*;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.CommonTriggerEvents;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.shelly.internal.ShellyBindingConstants;
import org.openhab.binding.shelly.internal.api.ShellyApiException;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellyInputState;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsDevice;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiResult;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.coap.ShellyCoapHandler;
import org.openhab.binding.shelly.internal.coap.ShellyCoapServer;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.openhab.binding.shelly.internal.util.ShellyChannelCache;
import org.openhab.binding.shelly.internal.util.ShellyTranslationProvider;
import org.openhab.binding.shelly.internal.util.ShellyVersionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ShellyBaseHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyBaseHandler extends BaseThingHandler implements ShellyDeviceListener {
    protected final Logger logger = LoggerFactory.getLogger(ShellyBaseHandler.class);
    protected final ShellyChannelDefinitionsDTO channelDefinitions;

    public String thingName = "";
    protected ShellyBindingConfiguration bindingConfig = new ShellyBindingConfiguration();
    protected ShellyThingConfiguration config = new ShellyThingConfiguration();
    private final ShellyTranslationProvider messages = new ShellyTranslationProvider();
    private final HttpClient httpClient;
    protected ShellyHttpApi api = new ShellyHttpApi();
    protected ShellyDeviceProfile profile = new ShellyDeviceProfile(); // init empty profile to avoid NPE
    private final ShellyCoapHandler coap;
    private Boolean autoCoIoT = false;
    protected Boolean coiotTriggersRefresh = false;

    protected boolean lockUpdates = false;
    private boolean channelsCreated = false;
    private long lastUptime = 0;
    private long lastAlarmTs = 0;
    private long lastUpdateTs = -1;
    private Integer lastTimeoutErros = -1;

    private @Nullable ScheduledFuture<?> statusJob;
    private int skipUpdate = 0;
    public int scheduledUpdates = 0;
    private int skipCount = UPDATE_SKIP_COUNT;

    // force settings refresh every x seconds
    private int refreshCount = UPDATE_SETTINGS_INTERVAL_SECONDS / UPDATE_STATUS_INTERVAL_SECONDS;
    private boolean refreshSettings = false;

    // delay before enabling channel
    private final int cacheCount = UPDATE_SETTINGS_INTERVAL_SECONDS / UPDATE_STATUS_INTERVAL_SECONDS;
    protected ShellyChannelCache cache = new ShellyChannelCache(this);

    String localIP = "";
    int httpPort = -1;

    /**
     * Constructor
     *
     * @param thing The Thing object
     * @param bindingConfig The binding configuration (beside thing
     *            configuration)
     * @param coapServer coap server instance
     * @param localIP local IP address from networkAddressService
     * @param httpPort from httpService
     */
    public ShellyBaseHandler(final Thing thing, final ShellyTranslationProvider translationProvider,
            final ShellyBindingConfiguration bindingConfig, final ShellyCoapServer coapServer, final String localIP,
            int httpPort, final HttpClient httpClient) {
        super(thing);

        this.messages.initFrom(translationProvider);
        this.channelDefinitions = new ShellyChannelDefinitionsDTO(messages);
        this.bindingConfig = bindingConfig;
        this.httpClient = httpClient;
        this.localIP = localIP;
        this.httpPort = httpPort;

        coap = new ShellyCoapHandler(this, translationProvider, coapServer);
    }

    /**
     * Schedule asynchronous Thing initialization, register thing to event dispatcher
     */
    @Override
    public void initialize() {
        // start background initialization:
        scheduler.schedule(() -> {
            boolean start = true;
            try {
                initializeThingConfig();
                logger.debug("{}: Device config: ipAddress={}, http user/password={}/{}, update interval={}",
                        getThing().getLabel(), config.deviceIp, config.userId.isEmpty() ? "<non>" : config.userId,
                        config.password.isEmpty() ? "<none>" : "***", config.updateInterval);
                setThingStatus(ThingStatus.UNKNOWN);
                start = initializeThing();
            } catch (ShellyApiException e) {
                ShellyApiResult res = e.getApiResult();
                if (isAuthorizationFailed(res)) {
                    start = false;
                }
                logger.debug("{}: Unable to initialize: {}, retrying later", getThing().getLabel(), e.toString());
            } catch (IllegalArgumentException | NullPointerException e) {
                logger.debug("{}: Unable to initialize: {} ({}), retrying later\n{}", getThing().getLabel(),
                        getString(e), e.getClass(), e.getStackTrace());
            } finally {
                // even this initialization failed we start the status update
                // the updateJob will then try to auto-initialize the thing
                // in this case the thing stays in status INITIALIZING
                if (start && (getThing().getStatus() != ThingStatus.OFFLINE)) {
                    startUpdateJob();
                }
            }
        }, 2, TimeUnit.SECONDS);

    }

    /**
     * This routine is called every time the Thing configuration has been changed (e.g. PaperUI)
     */
    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        logger.debug("{}: Thing config updated.", thingName);
        initializeThingConfig();
        startUpdateJob();
        refreshSettings = true; // force re-initialization
    }

    /**
     * Initialize Thing: Initialize API access, get settings and initialize Device Profile
     * If the device is password protected and the credentials are missing or don't match the API access will throw an
     * Exception. In this case the thing type will be changed to shelly-unknown. The user has the option to edit the
     * thing config and set the correct credentials. The thing type will be changed to the requested one if the
     * credentials are correct and the API access is initialized successful.
     *
     * @throws ShellyApiException e.g. http returned non-ok response, check e.getMessage() for details.
     */
    @SuppressWarnings({ "null", "deprecation" })
    private boolean initializeThing() throws ShellyApiException {
        // Get the thing global settings and initialize device capabilities
        cache.clear(); // clear any cached channels
        refreshSettings = false;
        lockUpdates = false;
        if (config.deviceIp.isEmpty()) {
            setThingOffline(ThingStatusDetail.CONFIGURATION_ERROR, "config-status.error.missing-device-ip");
            return false;
        }

        final @Nullable Map<String, String> properties = getThing().getProperties();
        final String thingType = getThing().getThingTypeUID().getId();
        thingName = getString(properties.get(PROPERTY_SERVICE_NAME));
        if (thingName.isEmpty()) {
            thingName = getString(getThing().getUID().getThingTypeId() + "-" + getString(getThing().getUID().getId()))
                    .toLowerCase();
            logger.debug("{}: Thing name derived from UID {}", thingName, getString(getThing().getUID().toString()));
        }
        cache.setThingName(thingName);
        logger.debug("{}: Start initializing thing {}, type {}, ip address {}, CoIoT: {}", thingName,
                getThing().getLabel(), thingType, config.deviceIp, config.eventsCoIoT);

        // Init from thing type to have a basic profile, gets updated when device info is received from API
        api = new ShellyHttpApi(thingName, config, httpClient);
        profile.initFromThingType(thingType);

        // Setup CoAP listener to we get the CoAP message, which triggers initialization even the thing could not be
        // fully initialized here. In this case the CoAP messages triggers auto-initialization (like the Action URL does
        // when enabled)
        if (config.eventsCoIoT || autoCoIoT) {
            coap.start(thingName, config, coiotTriggersRefresh);
        }

        // Initialize API access, exceptions will be catched by initialize()
        ShellySettingsDevice tmpdi = api.getDevInfo();
        ShellySettingsDevice devInfo = tmpdi != null ? tmpdi : new ShellySettingsDevice();
        if (devInfo.auth && config.userId.isEmpty()) {
            setThingOffline(ThingStatusDetail.CONFIGURATION_ERROR, "offline.conf-error-no-credentials");
            return false;
        }

        ShellyDeviceProfile tmpPrf = api.getDeviceProfile(thingType);
        if (this.getThing().getThingTypeUID().equals(THING_TYPE_SHELLYUNKNOWN)) {
            changeThingType(thingName, tmpPrf.mode);
            return false; // force re-initialization
        }
        // Validate device mode
        String reqMode = thingType.contains("-") ? StringUtils.substringAfter(thingType, "-") : "";
        if (!reqMode.isEmpty() && !tmpPrf.mode.equals(reqMode)) {
            setThingOffline(ThingStatusDetail.CONFIGURATION_ERROR, "offline.conf-error-wrong-mode");
            return false;
        }

        logger.debug("{}: Initializing device {}, type {}, Hardware: Rev: {}, batch {}; Firmware: {} / {} ({})",
                thingName, tmpPrf.hostname, tmpPrf.deviceType, tmpPrf.hwRev, tmpPrf.hwBatchId, tmpPrf.fwVersion,
                tmpPrf.fwDate, tmpPrf.fwId);
        logger.debug("{}: Shelly settings info for {}: {}", thingName, tmpPrf.hostname, tmpPrf.settingsJson);
        logger.debug("{}: Device "
                + "hasRelays:{} (numRelays={},isRoller:{} (numRoller={}),isDimmer:{},isPlugS:{},numMeter={},isEMeter:{})"
                + ",isSensor:{},isDS:{},hasBattery:{}{},isSense:{},isLight:{},isBulb:{},isDuo:{},isRGBW2:{},inColor:{},hasLEDs:{}"
                + ",supports urls: btn:{},out:{},push{},roller:{},sensor:{},updatePeriod:{}sec", thingName,
                tmpPrf.hasRelays, tmpPrf.numRelays, tmpPrf.isRoller, tmpPrf.numRollers, tmpPrf.isDimmer, tmpPrf.isPlugS,
                tmpPrf.numMeters, tmpPrf.isEMeter, tmpPrf.isSensor, tmpPrf.isDW, tmpPrf.hasBattery,
                tmpPrf.hasBattery ? " (low battery threshold=" + config.lowBattery + "%)" : "", tmpPrf.isSense,
                tmpPrf.isLight, profile.isBulb, tmpPrf.isDuo, tmpPrf.isRGBW2, tmpPrf.inColor, tmpPrf.hasLed,
                tmpPrf.supportsButtonUrls, tmpPrf.supportsOutUrls, tmpPrf.supportsPushUrls, tmpPrf.supportsRollerUrls,
                tmpPrf.supportsSensorUrls, tmpPrf.updatePeriod);
        // Set refresh interval for battery-powered devices to
        refreshCount = !tmpPrf.hasBattery
                ? refreshCount = UPDATE_SETTINGS_INTERVAL_SECONDS / UPDATE_STATUS_INTERVAL_SECONDS
                : skipCount;

        // update thing properties
        ShellySettingsStatus status = api.getStatus();
        updateProperties(tmpPrf, status);
        checkVersion(tmpPrf, status);

        if (autoCoIoT) {
            logger.debug("{}: Auto-CoIoT is enabled, disabling http events except roller (use CoIoT instead)",
                    thingName);
            config.eventsCoIoT = true;
            config.eventsButton = false;
            config.eventsSwitch = false;
            config.eventsSensorReport = false;
            config.eventsPush = false;
            if (!coap.isStarted()) {
                coap.start(thingName, config, coiotTriggersRefresh);
            }
        }
        coiotTriggersRefresh = !autoCoIoT && !profile.isSensor;

        // register event urls
        api.setEventURLs();

        // All initialization done, so keep the profile and set Thing to ONLINE
        profile = tmpPrf;
        fillDeviceStatus(status, false);
        postEvent(ALARM_TYPE_NONE, false);

        logger.debug("{}: Thing successfully initialized.", thingName);
        setThingOnline(); // if API call was successful the thing must be online

        return true; // success
    }

    /**
     * Handle Channel Command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (command instanceof RefreshType) {
                return;
            }

            if (!profile.isInitialized()) {
                logger.debug("{}: {}", thingName, messages.get("message.command.init", command.toString()));
                initializeThing();
            } else {
                profile = getProfile(false);
            }

            boolean update = false;
            lockUpdates = true;

            switch (channelUID.getIdWithoutGroup()) {
                case CHANNEL_SENSE_KEY: // Shelly Sense: Send Key
                    logger.debug("{}: Send key {}", thingName, command.toString());
                    api.sendIRKey(command.toString());
                    update = true;
                    break;

                default:
                    update = handleDeviceCommand(channelUID, command);
                    break;
            }

            if (update) {
                requestUpdates(1, false);
            }
            lastUpdateTs = now();
        } catch (ShellyApiException e) {
            ShellyApiResult res = e.getApiResult();
            if (isAuthorizationFailed(res)) {
                return;
            }
            if (res.isNotCalibrtated()) {
                logger.warn("{}:{}", thingName, messages.get("roller.calibrating"));
            } else {
                logger.info("{}: {}", thingName, messages.get("command.failed", channelUID.toString(), e.getMessage(),
                        e.getClass(), e.getStackTrace()));
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.debug("{}: {}", thingName, messages.get("command.failed", channelUID.toString(), e.getMessage(),
                    e.getClass(), e.getStackTrace()));
        } finally {
            lockUpdates = false;
        }
    }

    /**
     * Update device status and channels
     */
    protected void refreshStatus() {
        try {
            boolean updated = false;

            skipUpdate++;
            if (lockUpdates) {
                logger.trace("{}: Update locked, try on next cycle", thingName);
                return;
            }

            if ((skipUpdate % refreshCount == 0) && (profile.isInitialized())
                    && (getThing().getStatus() == ThingStatus.ONLINE)) {
                refreshSettings |= !profile.hasBattery;
            }

            if (refreshSettings || (scheduledUpdates > 0) || (skipUpdate % skipCount == 0)) {
                if ((!profile.isInitialized()) || ((getThing().getStatus() == ThingStatus.OFFLINE)
                        || (getThing().getStatus() == ThingStatus.UNKNOWN) && (getThing().getStatusInfo()
                                .getStatusDetail() != ThingStatusDetail.CONFIGURATION_ERROR))) {
                    logger.debug("{}: Status update triggered thing initialization", thingName);
                    initializeThing(); // may fire an exception if initialization failed
                }

                // Get profile, if refreshSettings == true reload settings from device
                profile = getProfile(refreshSettings);

                logger.trace("{}: Updating status", thingName);
                ShellySettingsStatus status = api.getStatus();
                lastUpdateTs = now();

                // If status update was successful the thing must be online
                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    logger.debug("{}: Thing {} is now online", thingName, getThing().getLabel());
                    updateStatus(ThingStatus.ONLINE); // if API call was successful the thing must be online
                }

                // map status to channels
                updated |= updateDeviceStatus(status);
                updated |= ShellyComponents.updateDeviceStatus(this, status);
                updated |= ShellyComponents.updateMeters(this, status);
                updated |= ShellyComponents.updateSensors(this, status);

                // All channels must be created after the first cycle
                channelsCreated = true;

                if (scheduledUpdates <= 1) {
                    fillDeviceStatus(status, updated);
                }
            }
        } catch (ShellyApiException e) {
            // http call failed: go offline except for battery devices, which might be in
            // sleep mode. Once the next update is successful the device goes back online
            String status = "";
            ShellyApiResult res = e.getApiResult();
            if (e.isTimeout() || res.isHttpTimeout()) {
                status = "offline.status-error-timeout";
            } else if (res.isHttpAccessUnauthorized()) {
                status = "offline.conf-error-access-denied";
            } else if (res.isNotCalibrtated()) {
                status = "offline.status-error-not-calibrated";
            } else {
                status = "offline.status-error-unexpected-api-result";
            }
            if (!e.isTimeout() || !profile.isSensor || (now() > lastUpdateTs + profile.updatePeriod)) {
                // API called failed, Thing goes offline
                logger.debug("{}: API call failed or no update within updatePeriod, go offline", thingName);
                setThingOffline(ThingStatusDetail.COMMUNICATION_ERROR, status);
            }
            scheduledUpdates = 0; // discard remaining requests
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("{}: {}\n{}", thingName,
                    messages.get("statusupdate.failed", getString(e), getString(e.getClass().toString())),
                    e.getStackTrace());
        } finally {
            if (scheduledUpdates > 0) {
                --scheduledUpdates;
                logger.trace("{}: {} more updates requested", thingName, scheduledUpdates);
            } else if ((skipUpdate >= cacheCount) && cache.isEnabled()) {
                logger.debug("{}: Enabling channel cache ({} updates / {}s)", thingName, skipUpdate,
                        cacheCount * UPDATE_STATUS_INTERVAL_SECONDS);
                cache.enable();
            }
        }
    }

    public boolean isThingOnline() {
        return getThing().getStatus() == ThingStatus.ONLINE;
    }

    public boolean isThingOffline() {
        ThingStatus status = getThing().getStatus();
        return status == ThingStatus.OFFLINE || status == ThingStatus.UNKNOWN;
    }

    public void setThingOnline() {
        if (!isThingOnline()) {
            setThingStatus(ThingStatus.ONLINE);
            requestUpdates(!profile.isSensor ? 3 : 1, false); // request 3 updates in a row (during the first 2+3*3 sec)
            lastUpdateTs = now();
        }
    }

    public void setThingOffline(ThingStatusDetail detail, String messageKey) {
        if (getThing().getStatus() != ThingStatus.OFFLINE) {
            setThingStatus(ThingStatus.OFFLINE, detail, messageKey);
            channelsCreated = false; // check for new channels after devices gets re-initialized (e.g. new
        }
    }

    public void setThingStatus(ThingStatus newStatus) {
        if (getThing().getStatus() != newStatus) {
            logger.debug("{}: Changing Thing status from {} to {}", thingName, getThing().getStatus(), newStatus);
            updateStatus(newStatus);
        }
    }

    public void setThingStatus(ThingStatus newStatus, ThingStatusDetail detail, String messageKey) {
        if (getThing().getStatus() != newStatus) {
            logger.debug("{}: Changing Thing status from {} to {}.{} ({})", thingName, getThing().getStatus(),
                    newStatus, detail, messageKey);
            updateStatus(newStatus, detail, "@text/" + messageKey);
        }
    }

    private void fillDeviceStatus(ShellySettingsStatus status, boolean updated) {
        String alarm = "";
        boolean force = false;

        Map<String, String> propertyUpdates = new TreeMap<>();

        // Update uptime and WiFi, internal temp
        ShellyComponents.updateDeviceStatus(this, status);

        if (api.isInitialized() && (lastTimeoutErros != api.getTimeoutErrors())) {
            propertyUpdates.put(PROPERTY_STATS_TIMEOUTS, new Integer(api.getTimeoutErrors()).toString());
            propertyUpdates.put(PROPERTY_STATS_TRECOVERED, new Integer(api.getTimeoutsRecovered()).toString());
            lastTimeoutErros = api.getTimeoutErrors();
        }

        // Check various device indicators like overheating
        if ((status.uptime < lastUptime) && (profile.isInitialized()) && !profile.hasBattery) {
            alarm = ALARM_TYPE_RESTARTED;
            force = true;
        }
        lastUptime = getLong(status.uptime);

        if (getBool(status.overtemperature)) {
            alarm = ALARM_TYPE_OVERTEMP;
        } else if (getBool(status.overload)) {
            alarm = ALARM_TYPE_OVERLOAD;
        } else if (getBool(status.loaderror)) {
            alarm = ALARM_TYPE_LOADERR;
        }

        if (!alarm.isEmpty()) {
            postEvent(alarm, force);
        }

        if (!propertyUpdates.isEmpty()) {
            flushProperties(propertyUpdates);
        }
    }

    /**
     * Save alarm to the lastAlarm channel
     *
     * @param alarm Alarm Message
     */
    public void postEvent(String alarm, boolean force) {
        String channelId = mkChannelId(CHANNEL_GROUP_DEV_STATUS, CHANNEL_DEVST_ALARM);
        Object value = cache.getValue(channelId);
        String lastAlarm = value != null ? value.toString() : "";

        if (force || !lastAlarm.equals(alarm) || (now() > (lastAlarmTs + HEALTH_CHECK_INTERVAL_SEC))) {
            if (alarm.equals(ALARM_TYPE_NONE)) {
                cache.updateChannel(channelId, getStringType(alarm));
            } else {
                logger.warn("{}: {}", thingName, messages.get("event.triggered", alarm));
                triggerChannel(channelId, alarm);
                cache.updateChannel(channelId, getStringType(alarm));
                lastAlarmTs = now();
            }
        }
    }

    /**
     * Callback for device events
     *
     * @param deviceName device receiving the event
     * @param parameters parameters from the event URL
     * @param data the HTML input data
     * @return true if event was processed
     */
    @Override
    public boolean onEvent(String ipAddress, String deviceName, String deviceIndex, String type,
            Map<String, String> parameters) {
        if (thingName.equalsIgnoreCase(deviceName) || config.deviceIp.equals(ipAddress)) {
            logger.debug("{}: Event received: class={}, index={}, parameters={}", deviceName, type, deviceIndex,
                    parameters.toString());
            Integer rindex = !deviceIndex.isEmpty() ? Integer.parseInt(deviceIndex) + 1 : -1;
            if (!profile.isInitialized()) {
                logger.debug("{}: Device is not yet initialized, event triggers initialization", deviceName);
                requestUpdates(1, true);
            } else {
                String group = "";
                boolean isButton = false;
                if (type.equals(ShellyBindingConstants.EVENT_TYPE_RELAY)) {
                    group = profile.numRelays <= 1 ? CHANNEL_GROUP_RELAY_CONTROL
                            : CHANNEL_GROUP_RELAY_CONTROL + rindex.toString();
                    int i = Integer.parseInt(deviceIndex);
                    if ((i >= 0) && (i <= profile.settings.relays.size())) {
                        ShellySettingsRelay relay = profile.settings.relays.get(i);
                        if ((relay != null) && (relay.btnType.equalsIgnoreCase(SHELLY_BTNT_MOMENTARY)
                                || relay.btnType.equalsIgnoreCase(SHELLY_BTNT_DETACHED))) {
                            isButton = true;
                        }
                    }
                }
                if (type.equals(ShellyBindingConstants.EVENT_TYPE_ROLLER)) {
                    group = profile.numRollers <= 1 ? CHANNEL_GROUP_ROL_CONTROL
                            : CHANNEL_GROUP_ROL_CONTROL + rindex.toString();
                }
                if (type.equals(ShellyBindingConstants.EVENT_TYPE_LIGHT)) {
                    group = profile.numRelays <= 1 ? CHANNEL_GROUP_LIGHT_CONTROL
                            : CHANNEL_GROUP_LIGHT_CONTROL + rindex.toString();
                }
                if (type.equals(ShellyBindingConstants.EVENT_TYPE_SENSORDATA)) {
                    group = CHANNEL_GROUP_SENSOR;
                }
                if (group.isEmpty()) {
                    logger.debug("Unsupported event class: {}", type);
                    return false;
                }

                // map some of the events to system defined button triggers
                String channel = "";
                String onoff = "";
                String payload = "";
                String event = type.contentEquals(ShellyBindingConstants.EVENT_TYPE_SENSORDATA)
                        ? SHELLY_EVENT_SENSORDATA
                        : parameters.get("type");
                switch (event) {
                    case SHELLY_EVENT_SHORTPUSH:
                    case SHELLY_EVENT_LONGPUSH:
                        if (isButton) {
                            channel = CHANNEL_BUTTON_TRIGGER;
                            payload = event.equals(SHELLY_EVENT_SHORTPUSH) ? CommonTriggerEvents.SHORT_PRESSED
                                    : CommonTriggerEvents.LONG_PRESSED;
                        } else {
                            logger.debug("{}: Relay button is not in memontary or detached mode, ignore SHORT/LONGPUSH",
                                    thingName);
                        }
                        break;
                    case SHELLY_EVENT_ROLLER_OPEN:
                    case SHELLY_EVENT_ROLLER_CLOSE:
                    case SHELLY_EVENT_ROLLER_STOP:
                        channel = CHANNEL_EVENT_TRIGGER;
                        payload = event;
                        break;
                    case SHELLY_EVENT_BTN_ON:
                    case SHELLY_EVENT_BTN_OFF:
                        onoff = CHANNEL_INPUT;
                        break;
                    case SHELLY_EVENT_BTN1_ON:
                    case SHELLY_EVENT_BTN1_OFF:
                        onoff = CHANNEL_INPUT1;
                        break;
                    case SHELLY_EVENT_BTN2_ON:
                    case SHELLY_EVENT_BTN2_OFF:
                        onoff = CHANNEL_INPUT2;
                        break;
                    case SHELLY_EVENT_OUT_ON:
                    case SHELLY_EVENT_OUT_OFF:
                        onoff = CHANNEL_OUTPUT;
                        break;
                    case SHELLY_EVENT_SENSORDATA:
                        // process sensor with next refresh
                        break;
                    default:
                        // trigger will be provided by input/output channel or sensor channels
                }

                if (!onoff.isEmpty()) {
                    updateChannel(group, onoff, event.toLowerCase().contains("_on") ? OnOffType.ON : OnOffType.OFF);

                }
                if (!payload.isEmpty()) {
                    // Pass event to trigger channel
                    payload = payload.toUpperCase();
                    logger.debug("{}: Post event {}", thingName, payload);
                    triggerChannel(mkChannelId(group, channel), payload);
                }
            }

            // request update on next interval (2x for non-battery devices)
            requestUpdates(scheduledUpdates >= 2 ? 0 : !profile.hasBattery ? 2 : 1, true);
            return true;
        }
        return false;
    }

    /**
     * Initialize the binding's thing configuration, calc update counts
     */
    protected void initializeThingConfig() {
        config = getConfigAs(ShellyThingConfiguration.class);
        config.localIp = localIP;
        config.httpPort = httpPort;
        if (config.userId.isEmpty() && !bindingConfig.defaultUserId.isEmpty()) {
            config.userId = bindingConfig.defaultUserId;
            config.password = bindingConfig.defaultPassword;
            logger.debug("{}: Using userId {} from bindingConfig", thingName, config.userId);
        }
        if (config.updateInterval == 0) {
            config.updateInterval = UPDATE_STATUS_INTERVAL_SECONDS * UPDATE_SKIP_COUNT;
        }
        if (config.updateInterval < UPDATE_MIN_DELAY) {
            config.updateInterval = UPDATE_MIN_DELAY;
        }
        skipCount = config.updateInterval / UPDATE_STATUS_INTERVAL_SECONDS;
    }

    private void checkVersion(ShellyDeviceProfile prf, ShellySettingsStatus status) {
        try {
            ShellyVersionDTO version = new ShellyVersionDTO();
            if (version.checkBeta(getString(prf.fwVersion))) {
                logger.info("{}: {}", prf.hostname, messages.get("versioncheck.beta", prf.fwVersion, prf.fwDate,
                        prf.fwId, SHELLY_API_MIN_FWVERSION));
            } else {
                if (version.compare(prf.fwVersion, SHELLY_API_MIN_FWVERSION) < 0) {
                    logger.warn("{}: {}", prf.hostname, messages.get("versioncheck.tooold", prf.fwVersion, prf.fwDate,
                            prf.fwId, SHELLY_API_MIN_FWVERSION));
                }
            }
            if (bindingConfig.autoCoIoT && (version.compare(prf.fwVersion, SHELLY_API_MIN_FWCOIOT_STD) >= 0)
                    || (version.compare(prf.fwVersion, SHELLY_API_MIN_FWCOIOT_SENSOR) >= 0)) {
                logger.info("{}: {}", thingName, messages.get("versioncheck.autocoiot"));
                autoCoIoT = true;
            }
        } catch (IllegalArgumentException | NullPointerException e) { // could be inconsistant format of beta version
                                                                      // strings
            logger.debug("{}: {}", thingName, messages.get("versioncheck.failed", prf.fwVersion));
        }
        if (status.update.hasUpdate) {
            logger.info("{}: {}", thingName,
                    messages.get("versioncheck.update", status.update.oldVersion, status.update.newVersion));
        }
    }

    /**
     * Checks the http response for authorization error.
     * If the authorization failed the binding can't access the device settings and determine the thing type. In this
     * case the thing type shelly-unknown is set.
     *
     * @param response exception details including the http respone
     * @return true if the authorization failed
     */
    private boolean isAuthorizationFailed(ShellyApiResult result) {
        if (result.isHttpAccessUnauthorized()) {
            // If the device is password protected the API doesn't provide settings to the device settings
            logger.warn("{}: {}", getThing().getLabel(), messages.get("init.protected"));
            setThingOffline(ThingStatusDetail.CONFIGURATION_ERROR, "offline.conf-error-access-denied");
            changeThingType(THING_TYPE_SHELLYPROTECTED_STR, "");
            return true;
        }
        return false;
    }

    /**
     * Change type of this thing.
     *
     * @param thingType thing type acc. to the xml definition
     * @param mode Device mode (e.g. relay, roller)
     */
    private void changeThingType(String thingType, String mode) {
        ThingTypeUID thingTypeUID = ShellyDeviceProfile.getThingTypeUID(thingType, mode);
        if (!thingTypeUID.equals(THING_TYPE_SHELLYUNKNOWN)) {
            logger.debug("{}: Changing thing type to {}", getThing().getLabel(), thingTypeUID.toString());
            Map<String, String> properties = editProperties();
            properties.replace(PROPERTY_DEV_TYPE, thingType);
            properties.replace(PROPERTY_DEV_MODE, mode);
            updateProperties(properties);
            changeThingType(thingTypeUID, getConfig());
        }
    }

    @Override
    public void thingUpdated(Thing thing) {
        logger.debug("{}: Channel definitions updated.", thingName);
        super.thingUpdated(thing);
    }

    /**
     * Start the background updates
     */
    @SuppressWarnings("null")
    protected void startUpdateJob() {
        if ((statusJob == null) || statusJob.isCancelled()) {
            statusJob = scheduler.scheduleWithFixedDelay(this::refreshStatus, 2, UPDATE_STATUS_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            logger.debug("{}: Update status job started, interval={}*{}={}sec.", thingName, skipCount,
                    UPDATE_STATUS_INTERVAL_SECONDS, skipCount * UPDATE_STATUS_INTERVAL_SECONDS);
        }
    }

    /**
     * Flag the status job to do an exceptional update (something happened) rather
     * than waiting until the next regular poll
     *
     * @param requestCount number of polls to execute
     * @param refreshSettings true=force a /settings query
     * @return true=Update schedule, false=skipped (too many updates already
     *         scheduled)
     */
    public boolean requestUpdates(int requestCount, boolean refreshSettings) {
        this.refreshSettings |= refreshSettings;
        if (refreshSettings) {
            logger.debug("{}: Request settings refresh", thingName);
            scheduledUpdates = 1;
            return true;
        }
        if (scheduledUpdates < 10) { // < 30s
            scheduledUpdates += requestCount;
            return true;
        }
        return false;
    }

    /**
     * Map input states to channels
     *
     * @param groupName Channel Group (relay / relay1...)
     *
     * @param status Shelly device status
     * @return true: one or more inputs were updated
     */
    public boolean updateInputs(String groupName, ShellySettingsStatus status, int index) {
        boolean updated = false;
        if ((status.input != null) && (index == 0)) {
            // RGBW2: a single int rather than an array
            logger.trace("{}: Updating input with {}", thingName, getInteger(status.input));
            updated |= updateChannel(groupName, CHANNEL_INPUT,
                    getInteger(status.input) == 0 ? OnOffType.OFF : OnOffType.ON);
        } else if (status.inputs != null) {
            if (profile.isInitialized() && (profile.isDimmer || profile.isRoller)) {
                ShellyInputState state1 = status.inputs.get(0);
                ShellyInputState state2 = status.inputs.get(1);
                logger.trace("{}: Updating {}#input1 with {}, input2 with {}", thingName, groupName,
                        getOnOff(state1.input), getOnOff(state2.input));
                updated |= updateChannel(groupName, CHANNEL_INPUT + "1", getOnOff(state1.input));
                updated |= updateChannel(groupName, CHANNEL_INPUT + "2", getOnOff(state2.input));
            } else {
                ShellyInputState state = status.inputs.get(index);
                logger.trace("{}: Updating input[{}] with {}", thingName, index, getOnOff(state.input));
                updated |= updateChannel(groupName, CHANNEL_INPUT, getOnOff(state.input));
            }
        }
        return updated;
    }

    public void publishState(String channelId, State value) {
        updateState(channelId.contains("$") ? StringUtils.substringBefore(channelId, "$") : channelId, value);
    }

    public boolean updateChannel(String group, String channel, State value) {
        return updateChannel(mkChannelId(group, channel), value, false);
    }

    public boolean updateChannel(String channelId, State value, boolean force) {
        lastUpdateTs = now();
        return (channelId.contains("$") || isLinked(channelId)) && cache.updateChannel(channelId, value, force);
    }

    @Nullable
    public Object getChannelValue(String group, String channel) {
        return cache.getValue(group, channel);
    }

    /**
     * Update Thing's channels according to available status information from the API
     *
     * @param thingHandler
     */
    protected void updateChannelDefinitions(Map<String, Channel> dynChannels) {
        if (channelsCreated) {
            return; // already done
        }

        try {
            // Get subset of those channels that currently do not exist
            List<Channel> existingChannels = getThing().getChannels();
            for (Channel channel : existingChannels) {
                String id = channel.getUID().getId();
                if (dynChannels.containsKey(id)) {
                    dynChannels.remove(id);
                }
            }

            if (!dynChannels.isEmpty()) {
                logger.debug("{}: Updating channel definitions, {} channels", thingName, dynChannels.size());
                ThingBuilder thingBuilder = editThing();
                for (Map.Entry<String, Channel> channel : dynChannels.entrySet()) {
                    Channel c = channel.getValue();
                    logger.debug("{}: Adding channel {}", thingName, c.getUID().getId());
                    thingBuilder.withChannel(c);
                }
                updateThing(thingBuilder.build());
                logger.debug("{}: Channel definitions updated", thingName);
            }
        } catch (IllegalArgumentException e) {
            logger.debug("{}: Unable to update channel definitions: {}", thingName, getString(e));
        }
    }

    public boolean areChannelsCreated() {
        return channelsCreated;
    }

    /**
     * Update thing properties with dynamic values
     *
     * @param profile The device profile
     * @param status the /status result
     */
    @SuppressWarnings("null")
    protected void updateProperties(ShellyDeviceProfile profile, ShellySettingsStatus status) {
        Map<String, Object> properties = fillDeviceProperties(profile);
        String serviceName = getThing().getProperties().get(PROPERTY_SERVICE_NAME);
        String hostname = getString(profile.settings.device.hostname).toLowerCase();
        if ((serviceName == null) || serviceName.isEmpty()) {
            properties.put(PROPERTY_SERVICE_NAME, hostname);
            logger.trace("{}: Updated serrviceName to {}", thingName, hostname);
        }

        // add status properties
        if (status != null) {
            Validate.notNull(status, "updateProperties(): status must not be null!");
            if (status.wifiSta != null) {
                properties.put(PROPERTY_WIFI_NETW, getString(status.wifiSta.ssid));
                properties.put(PROPERTY_WIFI_IP, getString(status.wifiSta.ip));
            }
            if (status.update != null) {
                properties.put(PROPERTY_UPDATE_STATUS, getString(status.update.status));
                properties.put(PROPERTY_UPDATE_AVAILABLE, getBool(status.update.hasUpdate) ? "yes" : "no");
                properties.put(PROPERTY_UPDATE_CURR_VERS, getString(status.update.oldVersion));
                properties.put(PROPERTY_UPDATE_NEW_VERS, getString(status.update.newVersion));
            }
        }
        properties.put(PROPERTY_COIOTAUTO, autoCoIoT.toString());
        properties.put(PROPERTY_COIOTREFRESH, autoCoIoT.toString());

        Map<String, String> thingProperties = new TreeMap<>();
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            thingProperties.put(property.getKey(), (String) property.getValue());
        }
        flushProperties(thingProperties);
    }

    /**
     * Add one property to the Thing Properties
     *
     * @param key Name of the property
     * @param value Value of the property
     */
    public void updateProperties(String key, String value) {
        Map<String, String> thingProperties = editProperties();
        if (thingProperties.containsKey(key)) {
            thingProperties.replace(key, value);
        } else {
            thingProperties.put(key, value);
        }
        updateProperties(thingProperties);
        logger.trace("{}: Properties updated", thingName);
    }

    public void flushProperties(Map<String, String> propertyUpdates) {
        Map<String, String> thingProperties = editProperties();
        for (Map.Entry<String, String> property : propertyUpdates.entrySet()) {
            if (thingProperties.containsKey(property.getKey())) {
                thingProperties.replace(property.getKey(), property.getValue());
            } else {
                thingProperties.put(property.getKey(), property.getValue());
            }
        }
        updateProperties(thingProperties);
        logger.trace("{}: Properties updated", thingName);
    }

    /**
     * Get one property from the Thing Properties
     *
     * @param key property name
     * @return property value or "" if property is not set
     */
    @SuppressWarnings("null")
    public String getProperty(String key) {
        Map<String, String> thingProperties = getThing().getProperties();
        String value = thingProperties.get(key);
        return value != null ? value : "";
    }

    /**
     * Fill Thing Properties with device attributes
     *
     * @param profile Property Map to full
     * @return a full property map
     */
    public static Map<String, Object> fillDeviceProperties(ShellyDeviceProfile profile) {
        Map<String, Object> properties = new TreeMap<>();

        properties.put(PROPERTY_VENDOR, VENDOR);
        if (profile.isInitialized()) {
            properties.put(PROPERTY_MODEL_ID, getString(profile.settings.device.type));
            properties.put(PROPERTY_MAC_ADDRESS, profile.mac);
            properties.put(PROPERTY_FIRMWARE_VERSION,
                    profile.fwVersion + "/" + profile.fwDate + "(" + profile.fwId + ")");
            properties.put(PROPERTY_DEV_MODE, profile.mode);
            properties.put(PROPERTY_NUM_RELAYS, profile.numRelays.toString());
            properties.put(PROPERTY_NUM_ROLLERS, profile.numRollers.toString());
            properties.put(PROPERTY_NUM_METER, profile.numMeters.toString());
            if (!profile.hwRev.isEmpty()) {
                properties.put(PROPERTY_HWREV, profile.hwRev);
                properties.put(PROPERTY_HWBATCH, profile.hwBatchId);
            }

            if (profile.updatePeriod >= 0) {
                properties.put(PROPERTY_UPDATE_PERIOD, profile.updatePeriod.toString());
            }
        }
        return properties;
    }

    /**
     * Return device profile.
     *
     * @param ForceRefresh true=force refresh before returning, false=return without
     *            refresh
     * @return ShellyDeviceProfile instance
     * @throws ShellyApiException
     */
    public ShellyDeviceProfile getProfile(boolean forceRefresh) throws ShellyApiException {
        try {
            refreshSettings |= forceRefresh;
            if (refreshSettings) {
                profile = api.getDeviceProfile(getThing().getThingTypeUID().getId());
            }
        } finally {
            refreshSettings = false;
        }
        return profile;
    }

    public ShellyDeviceProfile getProfile() {
        return profile;
    }

    protected ShellyHttpApi getShellyApi() {
        return api;
    }

    protected ShellyDeviceProfile getDeviceProfile() {
        return profile;
    }

    public void triggerChannel(String group, String channel, String payload) {
        triggerChannel(mkChannelId(group, channel), payload);
    }

    public void stop() {
        lockUpdates = true;
        if (statusJob != null) {
            statusJob.cancel(true);
            statusJob = null;
        }
        profile.initialized = false;
        coap.stop();
    }

    /**
     * Shutdown thing, make sure background jobs are canceled
     */
    @Override
    public void dispose() {
        logger.debug("{}: Shutdown thing", thingName);
        stop();
        logger.debug("{}: Shelly statusJob stopped", thingName);
        super.dispose();
    }

    /**
     * Device specific command handlers are overriding this method to do additional stuff
     *
     * @throws ShellyApiException Communication problem on the API call
     */
    public boolean handleDeviceCommand(ChannelUID channelUID, Command command) throws ShellyApiException {
        return false;
    }

    /**
     * Device specific handlers are overriding this method to do additional stuff
     *
     * @throws ShellyApiException Communication problem on the API call
     */
    public boolean updateDeviceStatus(ShellySettingsStatus status) throws ShellyApiException {
        return false;
    }
}
