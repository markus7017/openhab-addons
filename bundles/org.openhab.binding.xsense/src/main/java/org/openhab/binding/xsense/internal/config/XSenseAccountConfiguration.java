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
package org.openhab.binding.xsense.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link XSenseAccountConfiguration} contains the configuration of the account bridge thing.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseAccountConfiguration {
    public String email = "";
    public String password = "";
    // 0 = not configured, the binding configuration default applies
    public int refreshInterval;
}
