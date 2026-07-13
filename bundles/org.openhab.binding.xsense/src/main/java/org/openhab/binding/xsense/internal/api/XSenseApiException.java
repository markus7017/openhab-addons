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
package org.openhab.binding.xsense.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link XSenseApiException} is thrown on X-Sense cloud API failures.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class XSenseApiException extends Exception {
    private static final long serialVersionUID = 1L;

    private final boolean authenticationFailure;

    public XSenseApiException(String message) {
        this(message, false, null);
    }

    public XSenseApiException(String message, Throwable cause) {
        this(message, false, cause);
    }

    public XSenseApiException(String message, boolean authenticationFailure) {
        this(message, authenticationFailure, null);
    }

    private XSenseApiException(String message, boolean authenticationFailure, @Nullable Throwable cause) {
        super(message, cause);
        this.authenticationFailure = authenticationFailure;
    }

    /**
     * Returns true when the failure was caused by invalid credentials or an expired session,
     * i.e. reconnecting without user interaction will not help unless a new login succeeds.
     */
    public boolean isAuthenticationFailure() {
        return authenticationFailure;
    }
}
