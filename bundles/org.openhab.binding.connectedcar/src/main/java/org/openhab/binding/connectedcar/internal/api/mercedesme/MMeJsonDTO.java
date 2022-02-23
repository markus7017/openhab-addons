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

import java.util.ArrayList;

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

    public class MMeErrorResponse {
        /*
         * {"error_description":"{errorCode:1003,reason:Too many failed login attempts. Please try again in 0 minutes.}"
         * ,"error":"invalid_grant"}
         */
        public Integer errorCode;
        public String reason;
        public String error;
    }

    public class MMeRequestPinResponse {
        public Boolean isEmail;
        public String username;
    }

    public class MMeVehicle {
        public String id;
        public String licenseplate;
        public String salesdesignation;
        public String finorvin;
        public String modelyear;
        public String colorname;
        public String fueltype;
        public String powerhp;
        public String powerkw;
        public String numberofdoors;
        public String numberofseats;
    }

    public static class MMeVehicleLMasteristData {
        public static class MMeVehicleMasterDataEntry {
            public String doorsCount;
            public String fin;
            public String fuelType;
            public String handDrive;
            public String roofType; // e.g. "NoSunroof"
            public String tirePressureMonitoringType; // e.g. "TirePressureMonitoring"
            public String windowsLiftCount; // e.g. "FourLift"
        }

        public ArrayList<MMeVehicleMasterDataEntry> vehicles;
    }

    public class MMeVehicleList {
        public class MMeVehicleListEntry {
            public String id;
            public String licenseplate;
            public String finorvin;
        }

        public ArrayList<MMeVehicleListEntry> vehicles;
    }

    public class MMeVehicleStatusData {
        public class MMeValue {
            public String unit;
            public String value;
            public String retrievalstatus;
            public String timestamp;
        }

        public class MMeTireStatus {

            public MMeValue tirepressurefrontleft;
            public MMeValue tirepressurefrontright;
            public MMeValue tirepressurerearleft;
            public MMeValue tirepressurerearright;
        }

        public class MMeDoorStatus {

            public MMeValue doorstatusfrontleft;
            public MMeValue doorstatusfrontright;
            public MMeValue doorstatusrearleft;
            public MMeValue doorstatusrearright;
            public MMeValue doorlockstatusfrontleft;
            public MMeValue doorlockstatusfrontright;
            public MMeValue doorlockstatusrearleft;
            public MMeValue doorlockstatusrearright;
            public MMeValue doorlockstatusdecklid;
            public MMeValue doorlockstatusgas;
            public MMeValue doorlockstatusvehicle;
        }

        public class MMeLocation {
            public MMeValue latitude;
            public MMeValue longitude;
            public MMeValue heading;
        }

        public class MMeOdometer {
            public MMeValue odometer;
            public MMeValue distancesincereset;
            public MMeValue distancesincestart;
        }

        public class MMeFuelStatus extends MMeValue {

        }

        public class MMeSocStatus extends MMeValue {

        }

        public class MMeVehicleStatus {
        }
    }
}
