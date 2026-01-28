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

import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for IdentifiableAuthorizationSubscription.
 * <p>
 * Serializes to a JSON object with subscriptionId and the subscription fields
 * flattened.
 */
public class IdentifiableAuthorizationSubscriptionSerializer
        extends StdSerializer<IdentifiableAuthorizationSubscription> {

    private final AuthorizationSubscriptionSerializer subscriptionSerializer = new AuthorizationSubscriptionSerializer();

    public IdentifiableAuthorizationSubscriptionSerializer() {
        super(IdentifiableAuthorizationSubscription.class);
    }

    @Override
    public void serialize(IdentifiableAuthorizationSubscription identifiable, JsonGenerator generator,
            SerializationContext serializers) {
        generator.writeStartObject();
        generator.writeStringProperty("subscriptionId", identifiable.subscriptionId());
        generator.writeName("subscription");
        subscriptionSerializer.serialize(identifiable.subscription(), generator, serializers);
        generator.writeEndObject();
    }
}
