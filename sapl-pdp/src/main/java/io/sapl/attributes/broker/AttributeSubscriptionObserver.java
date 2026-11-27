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
package io.sapl.attributes.broker;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import lombok.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Receives serialized rounds and terminal infrastructure failures for one subscription.
 * A failure to establish a truthful snapshot must reach the result owner even when a required
 * attribute has never delivered its first value.
 *
 * @param onUpdate evaluates one captured input and returns the next required keys
 * @param onFailure handles a terminal observation failure
 */
public record AttributeSubscriptionObserver(
        @NonNull Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate,
        @NonNull Consumer<RuntimeException> onFailure)
        implements Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> {
    @Override
    public Set<SubscriptionKey> apply(Map<SubscriptionKey, AttributeSnapshot> snapshot) {
        return onUpdate.apply(snapshot);
    }
}
