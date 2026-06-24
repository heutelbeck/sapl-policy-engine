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
 * Consumer-facing subscription identity for the snapshot-driven attribute
 * broker. Two reads of the same {@link AttributeFinderInvocation} with
 * different {@code head} flags are genuinely different consumer views:
 * the {@code head=true} view is frozen at the first emitted value,
 * the {@code head=false} view follows updates. The PIP-facing
 * subscription is keyed on {@code invocation} alone (the PIP is unaware
 * of {@code head}); the consumer-facing layer dedups per
 * {@code (invocation, head)} and tracks two cached views per invocation.
 *
 * @param invocation the canonical attribute-finder invocation. The PIP
 * subscription identity
 * @param head {@code true} for first-emission-only reads (legacy
 * {@code take(1)} semantic); {@code false} for latest-value reads
 *
 * @since 4.1.0
 */
public record SubscriptionKey(@NonNull AttributeFinderInvocation invocation, boolean head) {}
