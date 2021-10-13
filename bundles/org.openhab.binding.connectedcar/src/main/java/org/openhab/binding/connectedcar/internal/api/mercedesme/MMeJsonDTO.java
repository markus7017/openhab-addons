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

/**
 * {@link MMeJsonDTO} defines data formats for MercedesMe API
 *
 * @author Markus Michels - Initial contribution
 */
public class MMeJsonDTO {
    public static final String MERCEDES_AUTH_URL = "https://id.mercedes-benz.com/as/authorization.oauth2";
    public static final String MERCEDES_TOKEN_URL = "https://id.mercedes-benz.com/as/token.oauth2";
    public static final String MERCEDES_API_URL = "https://api.mercedes-benz.com/vehicledata/v2/vehicles/";

    // Authorization related Servlet and resources aliases.
    public static final String MERCEDES_ALIAS = "/connectmercedes";

    // Mercedes scopes needed by this binding to work.
    public static final String MERCEDES_SCOPE_FUELSTATUS = "mb:vehicle:mbdata:fuelstatus ";
    public static final String MERCEDES_SCOPE_EVSTATUS = "mb:vehicle:mbdata:evstatus ";
    public static final String MERCEDES_SCOPE_VEHICLELOCK = "mb:vehicle:mbdata:vehiclelock ";
    public static final String MERCEDES_SCOPE_VEHICLESTATUS = "mb:vehicle:mbdata:vehiclestatus ";
    public static final String MERCEDES_SCOPE_PAYASYOUDRIVE = "mb:vehicle:mbdata:payasyoudrive ";

    public static final String MERCEDES_SCOPE_REFRESHTOKEN = "offline_access";

    public class MmeErrorResponse {
    }

    public static class MMeVehicleListData {
        public class MMeVehicle {
        }
    }

    public class MMeVehicleStatusData {
        public class MMeVehicleStatus {
        }
    }

}
