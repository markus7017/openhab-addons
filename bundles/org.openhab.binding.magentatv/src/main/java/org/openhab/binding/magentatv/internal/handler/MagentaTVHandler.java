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
package org.openhab.binding.magentatv.internal.handler;

import static org.openhab.binding.magentatv.internal.MagentaTVBindingConstants.*;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.RewindFastforwardType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.magentatv.internal.MagentaTVConfiguration;
import org.openhab.binding.magentatv.internal.MagentaTVException;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRPayEvent;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRPayEventInstanceCreator;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRProgramInfoEvent;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRProgramInfoEventInstanceCreator;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRProgramStatus;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRProgramStatusInstanceCreator;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRShortProgramInfo;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.MRShortProgramInfoInstanceCreator;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.OAuthAutenhicateResponse;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.OAuthTokenResponse;
import org.openhab.binding.magentatv.internal.MagentaTVGsonDTO.OauthCredentials;
import org.openhab.binding.magentatv.internal.MagentaTVHandlerFactory;
import org.openhab.binding.magentatv.internal.network.MagentaTVNetwork;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link MagentaTVHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class MagentaTVHandler extends BaseThingHandler implements MagentaTVListener {
    private final Logger logger = LoggerFactory.getLogger(MagentaTVHandler.class);
    protected final MagentaTVConfiguration thingConfig = new MagentaTVConfiguration();
    private final Gson gson;
    protected MagentaTVNetwork network;
    protected @Nullable MagentaTVControl control;
    protected @Nullable MagentaTVHandlerFactory handlerFactory;

    private volatile int idRefresh = 0;
    private @Nullable ScheduledFuture<?> pairingWatchdogJob;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Constructor, save bindingConfig (services as default for thingConfig)
     *
     * @param thing
     * @param bindingConfig
     */
    public MagentaTVHandler(MagentaTVHandlerFactory handlerFactory, Thing thing, MagentaTVNetwork network) {
        super(thing);
        this.handlerFactory = handlerFactory;
        this.network = network;
        gson = new GsonBuilder().registerTypeAdapter(OauthCredentials.class, new MRProgramInfoEventInstanceCreator())
                .registerTypeAdapter(OAuthTokenResponse.class, new MRProgramStatusInstanceCreator())
                .registerTypeAdapter(OAuthAutenhicateResponse.class, new MRShortProgramInfoInstanceCreator())
                .registerTypeAdapter(OAuthAutenhicateResponse.class, new MRPayEventInstanceCreator()).create();

        // setup background device check
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                renewEventSubscription();
            }
        }, 2, 5, TimeUnit.MINUTES);
    }

    /**
     * Thing initialization:
     * - initialize thing status from UPnP discovery, thing config, local network settings
     * - perform Oath if userID is not configured and credentials are available
     * - wait for NotifyServlet to initialize (solves timing issues on fast startup)
     */
    @SuppressWarnings("null")
    @Override
    public void initialize() {
        // The framework requires you to return from this method quickly. For that the initialization itself is executed
        // asynchronously
        logger.debug("Initialize Thing...");
        // updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            String errorMessage = "";
            try {
                // Example for background initialization:
                // All relevant parameters will be derived from the thing config
                // the final result will be saved to the thing properties and can be viewed in
                // PaperUI
                thingConfig.initializeConfig(getConfig().getProperties());
                Map<String, Object> discoveredProperties = handlerFactory.getDiscoveredProperties(thingConfig.getUDN());
                if (discoveredProperties != null) {
                    thingConfig.updateConfig(discoveredProperties); // get network parameters from control
                }
                if (thingConfig.getUDN().isEmpty()) {
                    // get UDN from device name
                    String uid = this.getThing().getUID().getAsString();
                    thingConfig.setUDN(StringUtils.substringAfterLast(uid, ":"));
                }
                if (thingConfig.getMacAddress().isEmpty()) {
                    // get MAC address from UDN (last 12 digits)
                    String macAddress = StringUtils.substringAfterLast(thingConfig.getUDN(), "_");
                    if (macAddress.isEmpty()) {
                        macAddress = StringUtils.substringAfterLast(thingConfig.getUDN(), "-");
                    }
                    thingConfig.setMacAddress(macAddress);
                }

                control = new MagentaTVControl(thingConfig, network);
                thingConfig.updateConfig(control.getConfig().getProperties()); // get network parameters from control
                authenticateUser();

                // wait for NotifyServlet to initialze
                if (!handlerFactory.getNotifyServletStatus()) {
                    logger.debug("Waiting on NotifyServlet to start...");
                    int iRetries = 30;
                    while ((iRetries-- > 0) && !handlerFactory.getNotifyServletStatus()) {
                        logger.trace("Waiting for init, {} sec remaining", iRetries);
                        Thread.sleep(1000);
                    }
                    if ((iRetries <= 0) && !handlerFactory.getNotifyServletStatus()) {
                        throw new MagentaTVException("Can't initialize, NotifyServlet not started!");
                    }
                }

                connectReceiver(); // throws MagentaTVException on error

                // change to ThingStatus.ONLINE will be done when the pairing result is received
                // (see onPairingResult())
            } catch (MagentaTVException e) {
                errorMessage = e.toString();
            } catch (InterruptedException e) {
                errorMessage = e.toString();
            } finally {
                if (!errorMessage.isEmpty()) {
                    logger.debug("{}", errorMessage);
                    setOnlineState(ThingStatus.OFFLINE, errorMessage);
                }
            }
        });
    }

    /**
     * Handle channel commands
     *
     * @param channelUID - the channel, which received the command
     * @param command - the actual command (could be instance of StringType,
     *            DecimalType or OnOffType)
     */
    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            // currently no channels to be refreshed
            return;
        }

        try {
            if (!isOnline() || command.toString().equalsIgnoreCase("PAIR")) {
                logger.debug("Device {} is offline, try to (re-)connect", deviceName());
                connectReceiver(); // reconnect to MR, throws an exception if this fails
            }

            logger.debug("Channel command for device {}: {} for channel {}", thingConfig.getFriendlyName(),
                    command.toString(), channelUID.getId().toString());
            switch (channelUID.getId()) {
                case CHANNEL_POWER: // toggle power
                    logger.debug("Toggle power, new state={}", command.toString());
                    control.sendKey("POWER");
                    break;
                case CHANNEL_PLAYER:
                    logger.debug("Player command: {}", command);
                    if (command instanceof OnOffType) {
                        control.sendKey("POWER");
                    } else if (command instanceof PlayPauseType) {
                        if (command == PlayPauseType.PLAY) {
                            control.sendKey("PLAY");
                        } else if (command == PlayPauseType.PAUSE) {
                            control.sendKey("PAUSE");
                        }
                    } else if (command instanceof NextPreviousType) {
                        if (command == NextPreviousType.NEXT) {
                            control.sendKey("NEXTCH");
                        } else if (command == NextPreviousType.PREVIOUS) {
                            control.sendKey("PREVCH");
                        }
                    } else if (command instanceof RewindFastforwardType) {
                        if (command == RewindFastforwardType.FASTFORWARD) {
                            control.sendKey("FORWARD");
                        } else if (command == RewindFastforwardType.REWIND) {
                            control.sendKey("REWIND");
                        }
                    } else {
                        logger.debug("Unknown media command: {}", command.toString());
                    }
                    break;
                case CHANNEL_STOP:
                    if ((OnOffType) command == OnOffType.ON) {
                        control.sendKey("STOP");
                        updateState(CHANNEL_PLAYER, PlayPauseType.PAUSE);
                    }
                    updateState(CHANNEL_STOP, OnOffType.OFF);
                    break;
                case CHANNEL_VOLUME_UP:
                    if ((OnOffType) command == OnOffType.ON) {
                        control.sendKey("VOLUP");
                        updateState(CHANNEL_MUTE, OnOffType.OFF);
                    }
                    updateState(CHANNEL_VOLUME_UP, OnOffType.OFF);
                    break;
                case CHANNEL_VOLUME_DOWN:
                    if ((OnOffType) command == OnOffType.ON) {
                        control.sendKey("VOLDOWN");
                        updateState(CHANNEL_MUTE, OnOffType.OFF);
                    }
                    updateState(CHANNEL_VOLUME_DOWN, OnOffType.OFF);
                    break;
                case CHANNEL_MUTE:
                    if (command == OnOffType.ON) {
                        control.sendKey("MUTE");
                    } else {
                        control.sendKey("VOLUP");
                    }
                    break;
                case CHANNEL_CHANNEL:
                    control.selectChannel(command.toString());
                    break;
                case CHANNEL_CHUP:
                    if ((OnOffType) command == OnOffType.ON) {
                        control.sendKey("CHUP");
                    }
                    updateState(CHANNEL_CHUP, OnOffType.OFF);
                    break;
                case CHANNEL_CHDOWN:
                    if ((OnOffType) command == OnOffType.ON) {
                        control.sendKey("CHDOWN");
                    }
                    updateState(CHANNEL_CHDOWN, OnOffType.OFF);
                    break;
                case CHANNEL_KEY:
                    if (command.toString().equalsIgnoreCase("PAIR")) { // special key to re-pair receiver
                        logger.debug("PAIRing key received, reconnect device {}", deviceName());
                    } else {
                        control.sendKey(command.toString());
                        mapKeyToMediateState(command.toString());
                    }
                    break;
                default:
                    logger.debug("Command for unknown channel {}", channelUID.getAsString());
            }
        } catch (MagentaTVException e) {
            String errorMessage = MessageFormat.format("Channel operation failed (command={0}, value={1}): {2}",
                    command.toString(), channelUID.getId().toString(), e.toString());
            logger.debug("{}", errorMessage);
            setOnlineState(ThingStatus.OFFLINE, errorMessage);
        }
    }

    private void mapKeyToMediateState(String key) {
        State state = null;
        switch (key.toUpperCase()) {
            case "PLAY":
                state = PlayPauseType.PLAY;
                break;
            case "PAUSE":
                state = PlayPauseType.PAUSE;
                break;
            case "FORWARD":
                state = RewindFastforwardType.FASTFORWARD;
                break;
            case "REWIND":
                updateState(CHANNEL_PLAYER, RewindFastforwardType.REWIND);
                break;
        }
        if (state != null) {
            logger.debug("Setting Player state to {}", state);
            updateState(CHANNEL_PLAYER, state);
        }
    }

    /**
     * Connect to the receiver
     *
     * @throws MagentaTVException something failed
     */
    @SuppressWarnings("null")
    protected void connectReceiver() throws MagentaTVException {
        if ((control != null) && control.checkDev()) {
            thingConfig.updateConfig(control.getConfig().getProperties()); // get description data
            updateThingProperties(thingConfig.getProperties());
            handlerFactory.registerDevice(thingConfig.getUDN(), thingConfig.getTerminalID(), thingConfig.getIpAddress(),
                    this);
            control.subscribeEventChannel();
            control.sendPairingRequest();
            updateThingProperties(thingConfig.getProperties());

            // check for pairing timeout
            final int iRefresh = ++idRefresh;
            pairingWatchdogJob = scheduler.schedule(() -> {
                if (iRefresh == idRefresh) { // Make a best effort to not run multiple deferred refresh
                    if (thingConfig.getVerificationCode().isEmpty()) {
                        setOnlineState(ThingStatus.OFFLINE, "Timeout on pairing request!");
                    }
                }
            }, 15, TimeUnit.SECONDS);
        }
    }

    /**
     * If userID is empty and credentials are given the Telekom OAuth service is
     * used to query the userID
     *
     * @throws MagentaTVException
     */
    @SuppressWarnings("null")
    private void authenticateUser() throws MagentaTVException {
        if (thingConfig.getUserID().isEmpty()) {
            // run OAuth authentication, this finally provides the userID
            String userID = control.authenticateUser(thingConfig.getAccountName(), thingConfig.getAccountPassword());
            thingConfig.setUserID(userID);

            // Update thing configuration (persistent) - remove credentials, add userID
            Configuration configuration = this.getConfig();
            configuration.remove(PROPERTY_ACCT_NAME);
            configuration.remove(PROPERTY_ACCT_PWD);
            configuration.remove(PROPERTY_USERID);
            configuration.put(PROPERTY_ACCT_NAME, "***");
            configuration.put(PROPERTY_ACCT_PWD, "***");
            configuration.put(PROPERTY_USERID, userID);
            this.updateConfiguration(configuration);
            thingConfig.setAccountName("");
            thingConfig.setAccountPassword("");
        } else {
            logger.debug("Skip OAuth, use existing userID");
        }
    }

    /**
     * Update thing status
     *
     * @param mode new thing status
     * @return ON = power on, OFF=power off
     */
    public void setOnlineState(ThingStatus newStatus, String errorMessage) {
        ThingStatus status = this.getThing().getStatus();
        if (status != newStatus) {
            if (newStatus == ThingStatus.INITIALIZING) {
                logger.debug("Invalid new thing state: {}", newStatus.toString());
            }
            if (newStatus == ThingStatus.ONLINE) {
                updateStatus(newStatus);
                updateState(CHANNEL_POWER, OnOffType.ON);
            } else {
                if (!errorMessage.isEmpty()) {
                    logger.debug("Communication Error - {}, switch Thing offline", errorMessage);
                    updateStatus(newStatus, ThingStatusDetail.COMMUNICATION_ERROR, errorMessage);
                } else {
                    updateStatus(newStatus);
                }
                updateState(CHANNEL_POWER, OnOffType.OFF);
            }
        }
    }

    /**
     * A wakeup of the MR was detected (e.g. UPnP received)
     *
     * @throws MagentaTVException
     */
    @Override
    public void onWakeup(Map<String, Object> discoveredProperties) throws MagentaTVException {
        if (control == null) { // should never happen
            logger.debug("Unable to process wakeup (control == null)");
            return;
        }
        if ((this.getThing().getStatus() == ThingStatus.OFFLINE) || thingConfig.getVerificationCode().isEmpty()) {
            // Device sent a UPnP discovery information, trigger to reconnect
            connectReceiver();
        } else {
            logger.debug("Refesh device status for {} (UDN={}", deviceName(), thingConfig.getUDN());
            setOnlineState(ThingStatus.ONLINE, "");
        }
    }

    /**
     * The pairing result has been received. The pairing code will be used to generate the verification code and
     * complete pairing with the MR. Finally if pairing was completed successful the thing status will change to ONLINE
     *
     * @param pairingCode pairing code received from MR (NOTIFY event data)
     * @throws MagentaTVException
     */
    @SuppressWarnings("null")
    @Override
    public void onPairingResult(String pairingCode) throws MagentaTVException {
        if (control != null) {
            thingConfig.updateConfig(control.getConfig().getProperties()); // get description data

            if (control.generateVerificationCode(pairingCode)) {
                thingConfig.setPairingCode(pairingCode);
                logger.debug(
                        "{}: Pairing code received (UDN {}, terminalID {}, pairingCode={}, verificationCode={}, userID={})",
                        thingConfig.getFriendlyName(), thingConfig.getUDN(), thingConfig.getTerminalID(),
                        thingConfig.getPairingCode(), thingConfig.getVerificationCode(), thingConfig.getUserID());

                // verify pairing completes the pairing process
                if (control.verifyPairing()) {
                    logger.debug("Pairing completed for device {} ({}), Thing now ONLINE",
                            thingConfig.getFriendlyName(), thingConfig.getTerminalID());
                    setOnlineState(ThingStatus.ONLINE, "");
                    cancelPairingCheck(); // stop timeout check
                }
            }
        } else {
            logger.debug("Pairing: control is null!!");
        }
    }

    @SuppressWarnings({ "null", "unused" })
    @Override
    public void onMREvent(String jsonInput) {
        logger.trace("Process MR event for device {}, json={}", deviceName(), jsonInput);
        boolean flUpdatePower = false;
        String jsonEvent = fixEventJson(jsonInput);
        if (jsonEvent.contains(MR_EVENT_EIT_CHANGE)) {
            logger.debug("EVENT_EIT_CHANGE event received.");

            MRProgramInfoEvent pinfo = gson.fromJson(jsonEvent, MRProgramInfoEvent.class);
            if (!pinfo.channelNum.isEmpty()) {
                logger.debug("EVENT_EIT_CHANGE for channel {}/{}", pinfo.channelNum, pinfo.channelCode);
                updateState(CHANNEL_CHANNEL, new StringType(pinfo.channelNum));
                updateState(CHANNEL_CHANNEL_CODE, new StringType(pinfo.channelCode));
            }
            if (pinfo.programInfo != null) {
                int i = 0;
                for (MRProgramStatus ps : pinfo.programInfo) {
                    if ((ps.startTime == null) || ps.startTime.isEmpty()) {
                        logger.debug("EVENT_EIT_CHANGE: empty event data = {}", jsonEvent);
                        continue; // empty program_info
                    }
                    updateState(CHANNEL_RUN_STATUS, new StringType(control.getRunStatus(ps.running_status)));

                    if (ps.shortEvent != null) {
                        for (MRShortProgramInfo se : ps.shortEvent) {
                            if ((ps.startTime == null) || ps.startTime.isEmpty()) {
                                logger.debug("EVENT_EIT_CHANGE: empty program info");
                                continue;
                            }
                            // Convert UTC to local time
                            // 2018/11/04 21:45:00 -> "2018-11-04T10:15:30.00Z"
                            String tsLocal = ps.startTime.replace('/', '-').replace(" ", "T") + "Z";
                            Instant timestamp = Instant.parse(tsLocal);
                            ZonedDateTime localTime = timestamp.atZone(ZoneId.of("Europe/Berlin"));
                            tsLocal = StringUtils.substringBeforeLast(localTime.toString(), "[");
                            tsLocal = StringUtils.substringBefore(tsLocal.replace('-', '/').replace('T', ' '), "+");

                            logger.debug("Info for channel {} / {} - {} {}.{}, start time={}, duration={}",
                                    pinfo.channelNum, pinfo.channelCode, control.getRunStatus(ps.running_status),
                                    se.eventName, se.textChar, tsLocal, ps.duration);
                            if (ps.running_status != EV_EITCHG_RUNNING_NOT_RUNNING) {
                                updateState(CHANNEL_PROG_TITLE, new StringType(se.eventName));
                                updateState(CHANNEL_PROG_TEXT, new StringType(se.textChar));
                                updateState(CHANNEL_PROG_START, new StringType(tsLocal));
                                updateState(CHANNEL_PROG_DURATION, new StringType(ps.duration));
                                if (i++ == 0) {
                                    flUpdatePower = true;
                                }
                            }
                        }
                    }
                }
            }
        } else if (jsonEvent.contains("new_play_mode")) {
            MRPayEvent event = gson.fromJson(jsonEvent, MRPayEvent.class);
            if (event.duration == null) {
                event.duration = -1;
            }
            if (event.playPostion == null) {
                event.playPostion = -1;
            }
            logger.debug("STB event playContent: playMode={}, duration={}, playPosition={}",
                    control.getPlayStatus(event.newPlayMode), event.duration.toString(), event.playPostion.toString());

            // If we get a playConfig event there MR must be online. However it also sends a
            // plyMode stop before powering off the device, so we filter this.
            if ((event.newPlayMode != EV_PLAYCHG_STOP) && this.isInitialized()) {
                flUpdatePower = true;
            }
            if (event.newPlayMode != -1) {
                String playMode = control.getPlayStatus(event.newPlayMode);
                updateState(CHANNEL_PLAY_MODE, new StringType(playMode));
                mapPlayModeToMediaControl(playMode);
            }
            if (event.duration > 0) {
                updateState(CHANNEL_PROG_DURATION, new StringType(event.duration.toString()));
            }
            if (event.playPostion != -1) {
                updateState(CHANNEL_PROG_POS, new StringType(Integer.toString(event.playPostion)));
            }
        } else {
            logger.debug("Unknown MR event, JSON={}", jsonEvent);
        }
        if (flUpdatePower) {
            // We received a non-stopped event -> MR must be on
            updateState(CHANNEL_POWER, OnOffType.ON);
        }
    }

    private void mapPlayModeToMediaControl(String playMode) {
        switch (playMode) {
            case "playing":
            case "playing (MC)":
            case "playing (UC)":
            case "buffering":
                logger.debug("Setting Player state to PLAY");
                updateState(CHANNEL_PLAYER, PlayPauseType.PLAY);
                updateState(CHANNEL_STOP, OnOffType.OFF);
                break;
            case "paused":
            case "stopped":
                logger.debug("Setting Player state to PAUSE");
                updateState(CHANNEL_PLAYER, PlayPauseType.PAUSE);
                break;
        }
    }

    /**
     * When the MR powers off it send a UPnP message, which is catched by the binding.
     */
    @Override
    public void onPowerOff() throws MagentaTVException {
        logger.debug("Power-Off received for device {}", deviceName());
        // MR was powered off -> update pwoer status, reset items
        updateState(CHANNEL_POWER, OnOffType.OFF);
        updateState(CHANNEL_PROG_DURATION, StringType.EMPTY);
        updateState(CHANNEL_PROG_POS, StringType.EMPTY);
        updateState(CHANNEL_PROG_TITLE, StringType.EMPTY);
        updateState(CHANNEL_PROG_TEXT, StringType.EMPTY);
        updateState(CHANNEL_PROG_START, StringType.EMPTY);
        updateState(CHANNEL_PROG_DURATION, StringType.EMPTY);

        updateState(CHANNEL_CHANNEL, new DecimalType(0));
    }

    private String fixEventJson(String jsonEvent) {
        // MR401: channel_num is a string -> ok
        // MR201: channel_num is an int -> fix JSON formatting to String
        if (jsonEvent.contains(MR_EVENT_CHAN_TAG) && !jsonEvent.contains(MR_EVENT_CHAN_TAG + "\"")) {
            // hack: reformat the JSON string to make it compatible with the GSON parsing
            logger.trace("malformed JSON->fix channel_num");
            String start = StringUtils.substringBefore(jsonEvent, MR_EVENT_CHAN_TAG); // up to "channel_num":
            String end = StringUtils.substringAfter(jsonEvent, MR_EVENT_CHAN_TAG); // behind "channel_num":
            String chan = StringUtils.substringBetween(jsonEvent, MR_EVENT_CHAN_TAG, ",");
            chan = StringUtils.trim(chan);
            return start + "\"channel_num\":" + "\"" + chan + "\"" + end;
        }
        return jsonEvent;
    }

    private boolean isOnline() {
        return this.getThing().getStatus() == ThingStatus.ONLINE;
    }

    /**
     * Check device status, if pairing
     * events (if callbackUrl is configured)
     */
    @SuppressWarnings("null")
    private void renewEventSubscription() {
        if (control == null) {
            return;
        }
        logger.debug("Check receiver status, current state  {}/{}",
                this.getThing().getStatusInfo().getStatus().toString(),
                this.getThing().getStatusInfo().getStatusDetail());

        try {
            // when pairing is completed re-new event channel subscription
            if ((this.getThing().getStatus() != ThingStatus.OFFLINE) && !thingConfig.getVerificationCode().isEmpty()) {
                logger.debug("{}: Renew MR event subscription", deviceName());
                control.subscribeEventChannel();
            }
        } catch (MagentaTVException e) {
            logger.warn("{}: Re-new event subscription failed: {}", deviceName(), e.toString());
        }

        // another try: if the above SUBSCRIBE fails, try a re-connect immediatly
        try {
            if ((this.getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR)
                    && !thingConfig.getUserID().isEmpty()) {
                // if we have no userID the OAuth is not completed or pairing process got stuck
                logger.debug("{}: Reconnect media receiver", deviceName());
                connectReceiver(); // throws MagentaTVException on error
            }
        } catch (MagentaTVException | RuntimeException e) {
            logger.debug("{}: Re-connect to receiver failed: {}", deviceName(), e.toString());
        }
    }

    @SuppressWarnings("null")
    public void updateThingProperties(Map<String, Object> properties) {
        // copy all attributes except thos, which begin with $
        Map<String, String> map = new HashMap<String, String>();
        for (String key : properties.keySet()) {
            if ((key.charAt(0) != '$') && !key.contains("component.") && properties.get(key) != null) {
                map.put(key, properties.get(key).toString());
            }
        }
        this.updateProperties(map);
    }

    private String deviceName() {
        return thingConfig.getFriendlyName() + "(" + thingConfig.getTerminalID() + ")";
    }

    @SuppressWarnings("null")
    protected void cancelPairingCheck() {
        if (pairingWatchdogJob != null) {
            pairingWatchdogJob.cancel(true);
        }
    }

    @Override
    @SuppressWarnings("null")
    public void dispose() {
        cancelPairingCheck();
        if (handlerFactory != null) {
            handlerFactory.removeDevice(thingConfig.getTerminalID());
        }
        scheduler.shutdownNow();
        super.dispose();
    }

    @Reference
    public void setMagentaTVHandlerFactory(MagentaTVHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public void unsetMagentaVHandlerFactory(MagentaTVHandlerFactory handlerFactory) {
        this.handlerFactory = null;
    }
}
