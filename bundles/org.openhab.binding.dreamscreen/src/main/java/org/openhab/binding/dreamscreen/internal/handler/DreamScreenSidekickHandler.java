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
package org.openhab.binding.dreamscreen.internal.handler;

import static org.openhab.binding.dreamscreen.internal.DreamScreenBindingConstants.PRODUCT_ID_SIDEKICK;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.dreamscreen.internal.DreamScreenServer;
import org.openhab.binding.dreamscreen.internal.message.RefreshMessage;
import org.openhab.core.thing.Thing;

/**
 * The {@link DreamScreenSidekickHandler} is the Thing Handler for the DreamScreen Sidekick device.
 *
 * @author Markus Michels - Initial contribution
 */

@NonNullByDefault
public class DreamScreenSidekickHandler extends DreamScreenBaseHandler {
    public DreamScreenSidekickHandler(DreamScreenServer server, Thing thing) {
        super(server, thing);
    }

    @Override
    protected boolean refreshMsg(final RefreshMessage msg) {
        if (msg.getProductId() == PRODUCT_ID_SIDEKICK) {
            return super.refreshMsg(msg);
        }
        return false;
    }
}
