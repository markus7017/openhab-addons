/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

package org.openhab.binding.connectedcar.internal.api.mercedesme;

import static org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.*;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.HttpHeaders;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.connectedcar.internal.api.ApiException;
import org.openhab.binding.connectedcar.internal.config.CombinedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@NonNullByDefault
@WebSocket
public class MMeWebSocket {
    private static final String WEBSOCKET_API_BASE = "wss://websocket-prod.risingstars.daimler.com/ws";
    private static final String WEBSOCKET_API_BASE_NA = "wss://websocket-prod.risingstars-amap.daimler.com/ws";
    private static final String WEBSOCKET_API_BASE_PA = "wss://websocket-prod.risingstars-amap.daimler.com/ws";
    private static final String WEBSOCKET_USER_AGENT = "okhttp/3.12.2";
    private static final int SOCKET_MIN_RETRY = 15;

    public static enum SocketStatus {
        UNINITIALIZED_STATE,
        AUTHENTICATION_PROCESS,
        AUTHENTICATION_FAILED,
        AUTHENTICATION_COMPLETE,
        SEND_PING,
        CHECK_PONG,
        CONNECTION_FAILED,
        CONNECTION_ESTABLISHED,
        COMMUNICATION_ERROR,
        RECONNECTION_PROCESS;
    }

    private SocketStatus actualStatus = SocketStatus.UNINITIALIZED_STATE;
    private @Nullable Future<?> webSocketPollingJob;
    private @Nullable Future<?> webSocketReconnectionPollingJob;

    private final Logger logger = LoggerFactory.getLogger(MMeWebSocket.class);
    private final Gson gson = new Gson();
    private final CombinedConfig config;

    private String thingId = "";
    private String deviceIp = "";

    private @Nullable Session session;
    private @Nullable MMeWebSocketInterface websocketHandler;
    private @Nullable URI websocketAddress;
    private final WebSocketClient webSocketClient = new WebSocketClient();
    private ClientUpgradeRequest webSocketClientRequest = new ClientUpgradeRequest();
    private String apiResponse = "";

    public MMeWebSocket(String thingId, CombinedConfig config) {
        this.thingId = thingId;
        this.config = config;
    }

    public void addMessageHandler(MMeWebSocketInterface interfacehandler) {
        this.websocketHandler = interfacehandler;
    }

    public void connect() throws ApiException {
        try {
            String url;
            switch (config.account.region) {
                case MME_REGION_EUROPE:
                default:
                    url = WEBSOCKET_API_BASE;
                    break;
                case MME_REGION_NORTHAM:
                    url = WEBSOCKET_API_BASE_NA;
                    break;
                case MME_REGION_APAC:
                    url = WEBSOCKET_API_BASE_PA;
                    break;
            }
            websocketAddress = new URI(url);

            for (Map.Entry<String, String> header : config.api.stdHeaders.entrySet()) {
                webSocketClientRequest.setHeader(header.getKey(), header.getValue());
            }
            webSocketClientRequest.setHeader(HttpHeaders.USER_AGENT, WEBSOCKET_USER_AGENT);
            webSocketClientRequest.setTimeout(30, TimeUnit.MILLISECONDS);

            webSocketClient.start();
            webSocketClient.connect(this, websocketAddress, webSocketClientRequest);
        } catch (Exception e) {
            throw new ApiException("Unable to initialize WebSocket", e);
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        boolean connected = session.isOpen();
        actualStatus = connected ? SocketStatus.CONNECTION_ESTABLISHED : SocketStatus.CONNECTION_FAILED;
        logger.debug("{}: WebSocket connect {}", thingId, connected ? "was successful" : "failed!");
        if (websocketHandler != null) {
            websocketHandler.onConnect(true);
        }
    }

    public String sendMessage(String str) throws ApiException {
        apiResponse = "";
        if (session != null) {
            try {
                session.getRemote().sendString(str);
                return apiResponse;
            } catch (IOException e) {
                throw new ApiException("Error WebSocketSend failed", e);
            }
        }
        throw new ApiException("Unable to send API request (WebSocket I/O failed");
    }

    @OnWebSocketMessage
    public void onText(Session session, String receivedMessage) {
        logger.debug("{}: Inbound WebSocket message: {}", thingId, receivedMessage);
        apiResponse = receivedMessage;
        MMeWebSocketInterface handler = websocketHandler;
        if (handler != null) {
            /*
             * try {
             * ShellyRpcNotifyStatus message = fromJson(gson, receivedMessage, ShellyRpcNotifyStatus.class);
             * if (SHELLYRPC_METHOD_NOTIFYSTATUS.equalsIgnoreCase(message.method)) {
             * handler.onNotifyStatus(message);
             * return;
             * }
             * } catch (ApiException e) {
             * }
             */
            handler.onMessage(receivedMessage);
        } else {
            logger.debug("{}: No WebSocket onText handler registered!");
        }
    }

    public void closeWebsocketSession() throws ApiException {
        logger.debug("{}: Closing WebSocket");
        if (session != null) {
            session.close();
        }
        try {
            webSocketClient.stop();
        } catch (Exception e) {
            throw new ApiException("Unable to close WebSocket session", e);
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        logger.debug("{}: WebSocket closed", thingId);
        if (statusCode != StatusCode.NORMAL) {
            logger.debug("{}: WebSocket Connection closed: {} - {}", thingId, statusCode, reason);
        }
        if (session != null) {
            if (!session.isOpen()) {
                if (session != null) {
                    session.close();
                }
            }
            session = null;
        }
        if (websocketHandler != null) {
            websocketHandler.onClose();
        }
        disposeWebsocketPollingJob();
        reconnectWebsocket();
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        logger.warn("{}: WebSocket Error {}", cause.getMessage());
        if (websocketHandler != null) {
            websocketHandler.onError(cause);
        }
        disposeWebsocketPollingJob();
        actualStatus = SocketStatus.COMMUNICATION_ERROR;
        reconnectWebsocket();

    }

    public void reconnectWebsocket() {
        if (webSocketReconnectionPollingJob == null) {
            // webSocketReconnectionPollingJob = scheduler.scheduleWithFixedDelay(this::reconnectWebsocketJob, 0, 30,
            // TimeUnit.SECONDS);
        }
    }

    public void reconnectWebsocketJob() throws ApiException {
        switch (actualStatus) {
            case COMMUNICATION_ERROR:
                logger.debug("Reconnecting WebSocket");
                try {
                    disposeWebsocketPollingJob();
                    // rpcClient.closeWebsocketSession();
                    // thing.setThingOffline(ThingStatusDetail.COMMUNICATION_ERROR, "statusupdate.failed");
                    actualStatus = SocketStatus.RECONNECTION_PROCESS;
                } catch (Exception e) {
                    logger.debug("Connection error {}", e.getMessage());
                }
                connect();
                break;
            case AUTHENTICATION_COMPLETE:
                if (webSocketReconnectionPollingJob != null) {
                    if (!webSocketReconnectionPollingJob.isCancelled() && webSocketReconnectionPollingJob != null) {
                        webSocketReconnectionPollingJob.cancel(true);
                    }
                    webSocketReconnectionPollingJob = null;
                }
                break;
            default:
                break;
        }
    }

    public void dispose() {
        close();
    }

    public void close() {
        disposeWebsocketPollingJob();
        if (webSocketReconnectionPollingJob != null) {
            if (!webSocketReconnectionPollingJob.isCancelled() && webSocketReconnectionPollingJob != null) {
                webSocketReconnectionPollingJob.cancel(true);
            }
            webSocketReconnectionPollingJob = null;
        }
    }

    private void disposeWebsocketPollingJob() {
        if (webSocketPollingJob != null) {
            if (!webSocketPollingJob.isCancelled() && webSocketPollingJob != null) {
                webSocketPollingJob.cancel(true);
            }
            webSocketPollingJob = null;
        }
    }

}
