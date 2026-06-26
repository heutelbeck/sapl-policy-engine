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
import io.sapl.api.model.Value;

import java.time.Instant;
import java.util.List;

/**
 * One attribute read that contributed to a decision: the subscribed
 * key, the value held for it, when that value was published, and
 * the source locations in policy code where the key was referenced.
 *
 * @param key the subscribed attribute key
 * @param value the value held for {@code key}
 * @param valueTimestamp when {@code value} was published
 * @param occurrences source locations referencing {@code key}
 *
 * @since 4.1.0
 */
public record AttributeContribution(
        SubscriptionKey key,
        Value value,
        Instant valueTimestamp,
        List<Occurrence> occurrences) {}
