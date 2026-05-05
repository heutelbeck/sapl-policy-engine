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

import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SubscriptionKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Outcome of one snapshot-driven voter evaluation pass.
 * <p>
 * Direct algebraic mirror of
 * {@link io.sapl.api.model.ExpressionResult} at the voter layer.
 * <ul>
 * <li>{@code vote} — the computed {@link Vote} if all needed
 * attribute reads were resolvable from the snapshot at evaluation
 * time; {@code null} if at least one read could not complete and
 * the trigger loop must subscribe and retry.</li>
 * <li>{@code dependencies} — the complete map of attribute
 * subscriptions this evaluation pass needed or touched, keyed by
 * {@link SubscriptionKey}. Same semantics as
 * {@link io.sapl.api.model.ExpressionResult#dependencies()}: not a
 * delta, the full current picture. The trigger loop diffs against
 * the previous round to drive subscribe/release decisions.</li>
 * </ul>
 *
 * @since 4.2.0
 */
public record VoteResult(@Nullable Vote vote, Map<SubscriptionKey, List<Occurrence>> dependencies) {}
