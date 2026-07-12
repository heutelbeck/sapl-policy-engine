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
package io.sapl.api.model;

import java.time.Instant;

import lombok.NonNull;

/**
 * One entry in the snapshot map carried by an
 * {@link EvaluationContext}. Pairs the attribute value the trigger
 * loop most recently observed with the {@link Instant} the broker
 * emitted it. The timestamp is the data's freshness, not a round
 * counter. Operators can reason about staleness without a separate
 * clock query.
 *
 * @param value the latest value the trigger loop holds for the
 * keyed {@link io.sapl.api.attributes.AttributeFinderInvocation};
 * may be an {@link ErrorValue} when the broker emitted an error
 * @param timestamp when the broker emitted this value
 *
 * @since 4.1.0
 */
public record AttributeSnapshot(@NonNull Value value, @NonNull Instant timestamp) {}
