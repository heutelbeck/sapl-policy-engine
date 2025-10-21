/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.broker.api;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeRepository.TimeOutStrategy;

import java.time.Duration;
import java.time.Instant;

/**
 * Attribute data persisted to storage.
 * <p>
 * Sequence numbers are NOT persisted - they coordinate in-flight subscribers
 * during runtime only. On restart, sequence numbers start fresh from zero.
 */
public record PersistedAttribute(
        Val value,
        Instant timestamp,
        Duration ttl,
        TimeOutStrategy timeoutStrategy,
        Instant timeoutDeadline) {
    /**
     * Checks if this attribute has expired based on the given time.
     *
     * @param now the time to check against
     * @return true if the timeout deadline has passed
     */
    public boolean isExpiredAt(Instant now) {
        return now.isAfter(timeoutDeadline);
    }
}
