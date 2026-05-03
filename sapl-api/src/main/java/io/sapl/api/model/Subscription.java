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

import io.sapl.api.attributes.AttributeFinderInvocation;
import lombok.NonNull;

/**
 * Per-use-site record produced by a snapshot-driven evaluation pass.
 * <p>
 * Carries everything the trigger loop and observability layer need
 * about one attribute read in one expression evaluation:
 * <ul>
 * <li>{@code invocation} — the canonical attribute store key. The
 * store de-duplicates real subscriptions by this. Whether the
 * subscription is shared across
 * {@link io.sapl.api.pdp.AuthorizationSubscription}
 * sessions or anchored per-session is read from
 * {@link AttributeFinderInvocation#fresh()}: shared when
 * {@code false}, per-session when {@code true}.</li>
 * <li>{@code location} — source position of the read. Observational
 * only; never enters the store key. Multiple read sites of the
 * same invocation collapse on the store side but stay distinct in
 * the trace.</li>
 * <li>{@code head} — read kind. {@code true} means take the first
 * emission and ignore subsequent updates; {@code false} means
 * follow updates. Orthogonal to freshness.</li>
 * </ul>
 *
 * @param invocation the canonical attribute store key
 * @param location the source position of this read site
 * @param head {@code true} for first-emission-only reads,
 * {@code false} for latest-value reads
 *
 * @since 4.2.0
 */
public record Subscription(
        @NonNull AttributeFinderInvocation invocation,
        @NonNull SourceLocation location,
        boolean head) {}
