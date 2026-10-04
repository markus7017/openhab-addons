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
package org.openhab.binding.shelly.internal.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.shelly.internal.api2.Shelly2ApiJsonDTO.Shelly2AuthChallenge;

/**
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyHttpClientTest {

    @Test
    public void parseAuthChallengeReadsDigestHeader() {
        Shelly2AuthChallenge auth = ShellyHttpClient.parseAuthChallenge(
                "Digest qop=\"auth\", realm=\"shellycamera-a0b1c2\", nonce=\"60dc59c6\", algorithm=SHA-256");

        assertThat(auth.authType, is("digest"));
        assertThat(auth.realm, is("shellycamera-a0b1c2"));
        assertThat(auth.nonce, is("60dc59c6"));
        assertThat(auth.algorithm, is("SHA-256"));
    }
}
