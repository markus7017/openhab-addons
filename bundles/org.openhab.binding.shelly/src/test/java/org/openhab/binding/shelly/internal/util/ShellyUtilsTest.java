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
package org.openhab.binding.shelly.internal.util;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * Tests for {@link ShellyUtils}.
 *
 * @author Jacob Laursen - Initial contribution
 */
@NonNullByDefault
public class ShellyUtilsTest {
    @ParameterizedTest
    @MethodSource("provideTestCasesForGetTimestampFromLocalEpoch")
    void getTimestampFromLocalEpoch(String zone, long timestamp, Instant expectedInstant) {
        State actual = ShellyUtils.getTimestampFromLocalEpoch(zone, timestamp);
        DateTimeType expected = new DateTimeType(expectedInstant);
        assertThat(actual, is(equalTo(expected)));
    }

    private static Stream<Arguments> provideTestCasesForGetTimestampFromLocalEpoch() {
        return Stream.of( //
                Arguments.of("UTC", 1772900449, Instant.parse("2026-03-07T16:20:49Z")), //
                Arguments.of("Europe/Copenhagen", 1772900449, Instant.parse("2026-03-07T15:20:49Z")), //
                Arguments.of("Europe/Copenhagen", 1783441249, Instant.parse("2026-07-07T14:20:49Z")), //
                Arguments.of("Europe/Berlin", 1774747800, Instant.parse("2026-03-29T00:30:00Z")), //
                Arguments.of("America/New_York", 1772900449, Instant.parse("2026-03-07T21:20:49Z")), //
                Arguments.of("", 1772900449,
                        LocalDateTime.parse("2026-03-07T16:20:49").atZone(ZoneId.systemDefault()).toInstant()));
    }

    @Test
    void getTimestampFromLocalEpochInvalidZoneReturnsUndef() {
        assertThat(ShellyUtils.getTimestampFromLocalEpoch("_invalid", 123), is(equalTo(UnDefType.UNDEF)));
    }

    @Test
    void getTimestampFromEpochIgnoresTimezone() {
        assertThat(ShellyUtils.getTimestampFromEpoch(1648205140),
                is(equalTo(new DateTimeType(Instant.parse("2022-03-25T10:45:40Z")))));
    }

    @Test
    void stripDeprecatedSuffixRemovesSwitchSuffixFromDeprecatedSplitChannel() {
        assertEquals("light1#brightness", ShellyUtils.stripDeprecatedSuffix("light1#brightness$Switch"));
    }

    @Test
    void stripDeprecatedSuffixRemovesValueSuffixFromDeprecatedSplitChannel() {
        assertEquals("light1#brightness", ShellyUtils.stripDeprecatedSuffix("light1#brightness$Value"));
    }

    @Test
    void stripDeprecatedSuffixLeavesRegularChannelIdUnchanged() {
        assertEquals("light1#brightness", ShellyUtils.stripDeprecatedSuffix("light1#brightness"));
    }
}
