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

import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.sapl.api.attributes.AttributeFinderInvocation;

/**
 * Outcome of one snapshot-driven evaluation pass.
 * <p>
 * Field meanings:
 * <ul>
 * <li>{@code result} — the computed {@link Value} if all needed
 * attribute invocations were resolvable from the snapshot at
 * evaluation time; {@code null} if at least one read could not
 * complete and the trigger loop must subscribe and retry.</li>
 * <li>{@code subscriptions} — the <strong>complete</strong> set of
 * attribute invocations this evaluation pass needed or touched.
 * Not a delta; the full current picture. The trigger loop diffs
 * this against the previously-held subscription set to decide
 * what to subscribe (additions) and what to release (removals).
 * The "or less" reconciliation case (a parameter value change
 * making prior subscriptions unreachable) is naturally expressed
 * by the new pass returning a smaller set.</li>
 * </ul>
 * <p>
 * Why this shape rather than {@code Value | NeedsMore}: a sum-type
 * result can only signal "I need MORE attributes." It cannot
 * signal "I need FEWER" — the subscription set can only grow
 * monotonically across passes, which prevents correct cleanup
 * when parameterised attribute references resolve to different
 * branches. The full-set-per-pass shape supports both directions
 * symmetrically.
 *
 * @param result the computed value, or {@code null} if evaluation
 * could not complete with the current snapshot
 * @param subscriptions the complete set of attribute invocations
 * needed or touched in this evaluation pass
 *
 * @since 4.2.0
 */
public record ExpressionResult(@Nullable Value result, Set<AttributeFinderInvocation> subscriptions) {}
