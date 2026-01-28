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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.pdp.AuthorizationSubscription;

/**
 * Jackson serializer for AuthorizationSubscription.
 * <p>
 * Serialization rules:
 * <ul>
 * <li>subject, action, resource fields are always included</li>
 * <li>environment field is omitted if UndefinedValue</li>
 * </ul>
 */
public class AuthorizationSubscriptionSerializer extends StdSerializer<AuthorizationSubscription> {

    private final ValueSerializer valueSerializer = new ValueSerializer();

    public AuthorizationSubscriptionSerializer() {
        super(AuthorizationSubscription.class);
    }

    @Override
    public void serialize(AuthorizationSubscription subscription, JsonGenerator generator,
            SerializationContext serializers) {
        generator.writeStartObject();

        generator.writeName("subject");
        valueSerializer.serialize(subscription.subject(), generator, serializers);

        generator.writeName("action");
        valueSerializer.serialize(subscription.action(), generator, serializers);

        generator.writeName("resource");
        valueSerializer.serialize(subscription.resource(), generator, serializers);

        if (!(subscription.environment() instanceof UndefinedValue)) {
            generator.writeName("environment");
            valueSerializer.serialize(subscription.environment(), generator, serializers);
        }

        if (!subscription.secrets().isEmpty()) {
            generator.writeName("secrets");
            valueSerializer.serialize(subscription.secrets(), generator, serializers);
        }

        generator.writeEndObject();
    }
}
