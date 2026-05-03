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

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.attributes.AttributeFinderInvocation;

/**
 * Outcome of one snapshot-driven evaluation pass.
 * <p>
 * Field meanings:
 * <ul>
 * <li>{@code result} — the computed {@link Value} if all needed
 * attribute reads were resolvable from the snapshot at evaluation
 * time; {@code null} if at least one read could not complete and
 * the trigger loop must subscribe and retry.</li>
 * <li>{@code dependencies} — the <strong>complete</strong> map of
 * attribute subscriptions this evaluation pass needed or touched,
 * keyed by {@link AttributeFinderInvocation} (the natural
 * deduplication key on the attribute store side). The list of
 * {@link Occurrence}s per key captures every call site that depends
 * on this subscription, with its source location and head flag, for
 * trace and coverage purposes. Not a delta; the full current
 * picture. The trigger loop diffs this against the previously-held
 * dependency map to decide what to subscribe (additions) and what
 * to release (removals). The "or less" reconciliation case (a
 * parameter value change making prior subscriptions unreachable) is
 * naturally expressed by the new pass returning a smaller map.</li>
 * </ul>
 * <p>
 * Why this shape rather than {@code Value | NeedsMore}: a sum-type
 * result can only signal "I need MORE attributes." It cannot signal
 * "I need FEWER" — the dependency set could only grow monotonically
 * across passes, which prevents correct cleanup when parameterised
 * attribute references resolve to different branches. The
 * full-map-per-pass shape supports both directions symmetrically.
 *
 * @param result the computed value, or {@code null} if evaluation
 * could not complete with the current snapshot
 * @param dependencies the complete map of attribute subscriptions
 * needed or touched in this evaluation pass, with per-call-site
 * occurrences in the value position
 *
 * @since 4.2.0
 */
public record ExpressionResult(@Nullable Value result, Map<AttributeFinderInvocation, List<Occurrence>> dependencies) {}
