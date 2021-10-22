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
    public static final String MME_REGION_EUROPE = "EU";
    public static final String MME_REGION_NORTHAM = "US";
    public static final String MME_REGION_APAC = "AP";

    // Authorization related Servlet and resources aliases.
    public static final String MMME_ALIAS_URI = "/connectmercedes";

    // Mercedes scopes needed by this binding to work.
    public static final String MME_SCOPE_FUELSTATUS = "mb:vehicle:mbdata:fuelstatus ";
    public static final String MME_SCOPE_EVSTATUS = "mb:vehicle:mbdata:evstatus ";
    public static final String MME_SCOPE_VEHICLELOCK = "mb:vehicle:mbdata:vehiclelock ";
    public static final String MME_SCOPE_VEHICLESTATUS = "mb:vehicle:mbdata:vehiclestatus ";
    public static final String MME_SCOPE_PAYASYOUDRIVE = "mb:vehicle:mbdata:payasyoudrive ";
    public static final String MME_SCOPE_REFRESHTOKEN = "offline_access";
    public static final String MME_AUTH_SCOPE = MME_SCOPE_VEHICLESTATUS + " " + MME_SCOPE_FUELSTATUS + " "
            + MME_SCOPE_EVSTATUS + " " + MME_SCOPE_VEHICLELOCK;

    public class MmeErrorResponse {
    }

    public class MmeRequestPinResponse {
        public Boolean isEmail;
        public String username;
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
