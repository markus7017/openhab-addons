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

import static org.openhab.binding.connectedcar.internal.api.ApiDataTypesDTO.API_BRAND_MERCEDES;
import static org.openhab.binding.connectedcar.internal.util.Helpers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.HttpHeaders;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.connectedcar.internal.api.ApiBase;
import org.openhab.binding.connectedcar.internal.api.ApiDataTypesDTO.VehicleDetails;
import org.openhab.binding.connectedcar.internal.api.ApiDataTypesDTO.VehicleStatus;
import org.openhab.binding.connectedcar.internal.api.ApiEventListener;
import org.openhab.binding.connectedcar.internal.api.ApiException;
import org.openhab.binding.connectedcar.internal.api.ApiHttpClient;
import org.openhab.binding.connectedcar.internal.api.ApiHttpMap;
import org.openhab.binding.connectedcar.internal.api.BrandAuthenticator;
import org.openhab.binding.connectedcar.internal.api.IdentityManager;
import org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.MMeVehicleLMasteristData;
import org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.MMeVehicleLMasteristData.MMeVehicleMasterDataEntry;
import org.openhab.binding.connectedcar.internal.api.mercedesme.MMeJsonDTO.MMeVehicleStatusData;
import org.openhab.binding.connectedcar.internal.handler.ThingHandlerInterface;

/**
 * {@link MercedesMeApi} implements the MercedesMe API
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class MercedesMeApi extends ApiBase implements BrandAuthenticator, MMeWebSocketInterface {
    private final Map<String, MMeVehicleMasterDataEntry> vehicleList = new HashMap<>();
    private final MMeWebSocket webSocket;

    public MercedesMeApi(ThingHandlerInterface handler, ApiHttpClient httpClient, IdentityManager tokenManager,
            @Nullable ApiEventListener eventListener) {
        super(handler, httpClient, tokenManager, eventListener);
        webSocket = new MMeWebSocket(config);
        webSocket.addMessageHandler(this);
    }

    @Override
    public String getApiUrl() {
        return config.api.apiDefaultUrl;
    }

    @Override
    public String getHomeReguionUrl() {
        return getApiUrl();
    }

    @Override
    public ArrayList<String> getVehicles() throws ApiException {
        String json = callApi("", "v1/vehicle/self/masterdata?" + addLocaleUrl(), createApiParameters(),
                "getVehicleList", String.class);
        json = "{ \"vehicles\" : " + json + " }"; // Convert JSON form unnamed array to simplify Gson mapping
        vehicleList.clear();
        ArrayList<String> list = new ArrayList<String>();
        MMeVehicleLMasteristData data = fromJson(gson, json, MMeVehicleLMasteristData.class);
        for (MMeVehicleMasterDataEntry vehicle : data.vehicles) {
            list.add(vehicle.fin);
            vehicleList.put(vehicle.fin, vehicle);
        }
        /*
         * if (vehicleList.isEmpty()) {
         * MMeVehicleMasterDataEntry v = new MMeVehicleMasterDataEntry();
         * v.fin = "4711000000";
         * list.add(v.fin);
         * vehicleList.put(v.fin, v);
         * }
         */
        try {
            getUserInfo();
        } catch (ApiException e) {

        }
        return list;
    }

    @Override
    public VehicleDetails getVehicleDetails(String vin) throws ApiException {
        MMeVehicleMasterDataEntry vehicle = vehicleList.get(vin);
        if (vehicle != null) {
            if (!webSocket.isConnected()) {
                try {
                    webSocket.connect(createAccessToken());
                } catch (ApiException e) {

                }
            }

            VehicleDetails details = new VehicleDetails();
            details.vin = vehicle.fin;
            details.brand = API_BRAND_MERCEDES;
            details.model = details.brand + " " + getString(vehicle.fin);
            try {
                String json = callApi("", "/v1/vehicle/{2}/capabilities/commands", createApiParameters(),
                        "getCommandCapabilities", String.class);
            } catch (ApiException e) {

            }
            try {
                getGeoFences();
            } catch (ApiException e) {

            }
            return details;
        }
        throw new IllegalArgumentException("Unknown VIN " + vin);
    }

    @Override
    public VehicleStatus getVehicleStatus() throws ApiException {
        String json = callApi("", "vehicles/v4/{2}/status", createApiParameters(), "getVehicleStatus", String.class);
        return new VehicleStatus(fromJson(gson, json, MMeVehicleStatusData.class));
    }

    protected void getUserInfo() throws ApiException {
        String json = callApi("", "v1/user/self", createApiParameters(), "getUserInfo", String.class);
    }

    protected void getGeoFences() throws ApiException {
        String json = callApi("", "v1/geofencing/fences/?vin={2}", createApiParameters(), "getGeoFences", String.class);
        json = "{ \"fences\" : " + json + " }"; // Convert JSON form unnamed array to simplify Gson mapping
    }

    @Override
    public void onConnect(boolean connected) {
    }

    @Override
    public void onClose() {
    }

    @Override
    public void onMessage(String decodedmessage) {
    }

    // public void onNotifyStatus(ShellyRpcNotifyStatus message);

    @Override
    public void onError(Throwable cause) {
    }

    /*
     * @Override
     * public String refreshVehicleStatus() throws ApiException {
     * String json = http.put("vehicles/v2/{2}/status", createApiParameters(), "").response;
     * FPActionResponse rsp = fromJson(gson, json, FPActionResponse.class);
     * return "200".equals(rsp.status) ? API_REQUEST_SUCCESSFUL : API_REQUEST_FAILED;
     * }
     *
     * @Override
     * public String controlLock(boolean lock) throws ApiException {
     * return sendAction(FPSERVICE_DOORS, lock ? "lock" : "unlock", "lock", lock);
     * }
     *
     * @Override
     * public String controlEngine(boolean start) throws ApiException {
     * return sendAction(FPSERVICE_ENGINE, start ? "start" : "stop", "start", start);
     * }
     *
     * private String sendAction(String service, String action, String command, boolean start) throws ApiException {
     * logger.debug("{}: Sending action {} ({}, {}))", thingId, action, command, start);
     * HttpMethod method = start ? HttpMethod.PUT : HttpMethod.DELETE;
     * String uri = "/vehicles/v2/{2}/" + service + "/" + command;
     * String json = http.request(method, uri, "", createApiParameters(), "", "", "", false).response;
     * FPActionRequest req = new FPActionRequest(service, action, fromJson(gson, json, FPActionResponse.class));
     * req.checkUrl = uri + "/" + req.requestId;
     * return queuePendingAction(new ApiActionRequest(req));
     * }
     *
     * @Override
     * public String getApiRequestStatus(ApiActionRequest req) throws ApiException {
     * String json = http.get(req.checkUrl, createApiParameters()).response;
     * FPActionResponse rsp = fromJson(gson, json, FPActionResponse.class);
     * if (rsp.isError()) {
     * req.error = "API returned error: " + rsp.status;
     * logger.debug("{}: Unexpected API status code: {}", thingId, req.error);
     * }
     * return rsp.mapStatusCode();
     * }
     */
    protected ApiHttpMap createDefaultParameters() throws ApiException {
        return new ApiHttpMap().headers(config.api.stdHeaders)//
                .header("X-Request-Id", UUID.randomUUID().toString());
    }

    protected Map<String, String> createApiParameters(String token) throws ApiException {
        return createDefaultParameters().header("Application-Id", config.api.xClientId)
                // .header(HttpHeaders.ACCEPT, CONTENT_TYPE_FORM_URLENC)//
                .header(HttpHeaders.AUTHORIZATION, token.isEmpty() ? createAccessToken() : token) //
                .getHeaders();
    }

    protected Map<String, String> createApiParameters() throws ApiException {
        return createApiParameters(createAccessToken());
    }

    protected String addLocaleUrl() {
        return "country=" + substringAfter(config.api.xcountry, "-") + "&locale=" + config.api.xcountry;
    }

    public void dispose() {
        webSocket.close();
    }
}
