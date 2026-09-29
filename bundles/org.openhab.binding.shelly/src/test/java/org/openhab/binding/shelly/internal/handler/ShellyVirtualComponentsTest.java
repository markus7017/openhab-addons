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
package org.openhab.binding.shelly.internal.handler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.BINDING_ID;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.VGROUP_TYPE_MARKER;
import static org.openhab.binding.shelly.internal.ShellyDevices.THING_TYPE_SHELLYPLUS1;
import static org.openhab.binding.shelly.internal.handler.ShellyVirtualComponents.resolveVGroupBaseType;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.thing.ThingTypeUID;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyVirtualComponentsTest {

    @Test
    void returnsTheSameUidWhenItDoesNotCarryTheVGroupMarker() {
        assertThat(resolveVGroupBaseType(THING_TYPE_SHELLYPLUS1), is(THING_TYPE_SHELLYPLUS1));
    }

    @Test
    void stripsTheMarkerAndThingIdFromASyntheticVGroupUid() {
        ThingTypeUID synthetic = new ThingTypeUID(BINDING_ID,
                THING_TYPE_SHELLYPLUS1.getId() + VGROUP_TYPE_MARKER + "08f9e0e48f30");

        assertThat(resolveVGroupBaseType(synthetic), is(THING_TYPE_SHELLYPLUS1));
    }
}
