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
package io.sapl.api.model.jackson;

import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for MultiAuthorizationSubscription.
 * <p>
 * Serializes to a JSON object mapping subscription IDs to their subscriptions.
 *
 * <pre>{@code
 * {
 *   "read-file": {"subject": "alice", "action": "read", "resource": "document.pdf"},
 *   "write-file": {"subject": "alice", "action": "write", "resource": "document.pdf"}
 * }
 * }</pre>
 */
public class MultiAuthorizationSubscriptionSerializer extends StdSerializer<MultiAuthorizationSubscription> {

    private final AuthorizationSubscriptionSerializer subscriptionSerializer = new AuthorizationSubscriptionSerializer();

    public MultiAuthorizationSubscriptionSerializer() {
        super(MultiAuthorizationSubscription.class);
    }

    @Override
    public void serialize(MultiAuthorizationSubscription multiSubscription, JsonGenerator generator,
            SerializationContext serializers) {
        generator.writeStartObject();
        for (val identifiable : multiSubscription) {
            generator.writeName(identifiable.subscriptionId());
            subscriptionSerializer.serialize(identifiable.subscription(), generator, serializers);
        }
        generator.writeEndObject();
    }
}
