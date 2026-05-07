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
package io.sapl.compiler.document;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SubscriptionKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A {@link Vote} together with its emit timestamp and the raw
 * inputs of the round that produced it: the dependency map (keys
 * read, with their source occurrences) and the snapshot entries
 * those keys held at evaluation time. Empty trace inputs mean the
 * round was non-streaming or the trace was not captured.
 *
 * @param vote the underlying vote
 * @param timestamp the emit timestamp of this vote
 * @param dependencies the dependency map of the producing round
 * @param readSnapshot the snapshot entries for the keys in
 * {@code dependencies}, value and publish timestamp at evaluation
 * time
 *
 * @since 4.1.0
 */
public record TracedVote(
        Vote vote,
        Instant timestamp,
        Map<SubscriptionKey, List<Occurrence>> dependencies,
        Map<SubscriptionKey, AttributeSnapshot> readSnapshot) {

    /**
     * Wraps a vote with an emit timestamp and empty trace inputs.
     */
    public static TracedVote of(Vote vote, Instant timestamp) {
        return new TracedVote(vote, timestamp, Map.of(), Map.of());
    }
}
