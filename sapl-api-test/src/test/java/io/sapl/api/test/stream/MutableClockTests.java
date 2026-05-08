/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.api.test.stream;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MutableClock")
class MutableClockTests {

    @Test
    @DisplayName("returns the initial instant")
    void whenConstructedThenInstantIsInitial() {
        val initial = Instant.parse("2026-05-08T12:00:00Z");

        val clock = new MutableClock(initial);

        assertThat(clock.instant()).isEqualTo(initial);
    }

    @Test
    @DisplayName("setInstant replaces the current instant")
    void whenSetInstantThenInstantIsReplaced() {
        val clock      = new MutableClock(Instant.parse("2026-05-08T12:00:00Z"));
        val newInstant = Instant.parse("2026-05-08T13:00:00Z");

        clock.setInstant(newInstant);

        assertThat(clock.instant()).isEqualTo(newInstant);
    }

    @Test
    @DisplayName("advance moves the current instant forward by the given duration")
    void whenAdvanceThenInstantMovesForward() {
        val clock = new MutableClock(Instant.parse("2026-05-08T12:00:00Z"));

        clock.advance(Duration.ofMinutes(30));

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-05-08T12:30:00Z"));
    }

    @Test
    @DisplayName("withZone returns an independent clock at the same instant")
    void whenWithZoneThenNewClockHasIndependentZoneAndSameInstant() {
        val clock = new MutableClock(Instant.parse("2026-05-08T12:00:00Z"), ZoneId.of("UTC"));

        val rezoned = clock.withZone(ZoneId.of("Europe/Berlin"));

        assertThat(rezoned.getZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(rezoned.instant()).isEqualTo(clock.instant());
    }
}
