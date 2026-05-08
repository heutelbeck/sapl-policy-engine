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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Test {@link Clock} whose current instant is mutated explicitly by
 * the test author. Use with {@link TestTimeScheduler} to drive
 * deterministic time-based assertions: set the instant, advance the
 * scheduler, observe emissions.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant      instant;

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneId.of("UTC"));
    }

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.instant = initialInstant;
        this.zone    = zone;
    }

    /**
     * Replaces the current instant.
     */
    public void setInstant(Instant newInstant) {
        this.instant = newInstant;
    }

    /**
     * Advances the current instant by {@code amount}.
     */
    public void advance(Duration amount) {
        this.instant = this.instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
