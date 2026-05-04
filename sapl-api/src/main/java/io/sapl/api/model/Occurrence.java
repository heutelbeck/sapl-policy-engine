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

import lombok.NonNull;

/**
 * Per-call-site annotation of an attribute read. Lives in the value
 * position of the {@code dependencies} map carried by
 * {@link ExpressionResult}; the map's key is the
 * {@link SubscriptionKey} that identifies the actual subscription.
 * One subscription key may have many occurrences when the same
 * attribute is read at multiple call sites in a policy. The
 * {@code head} flag has moved to {@link SubscriptionKey} (where it
 * affects subscription identity); occurrences carry only the source
 * position for trace and coverage reporting.
 *
 * @param location source position of this read site
 *
 * @since 4.2.0
 */
public record Occurrence(@NonNull SourceLocation location) {}
