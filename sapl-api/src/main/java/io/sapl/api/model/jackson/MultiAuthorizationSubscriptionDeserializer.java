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
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer for MultiAuthorizationSubscription.
 * <p>
 * Deserializes from a JSON object mapping subscription IDs to their
 * subscriptions.
 */
public class MultiAuthorizationSubscriptionDeserializer extends StdDeserializer<MultiAuthorizationSubscription> {

    /**
     * Default constructor required by Jackson 3.
     */
    public MultiAuthorizationSubscriptionDeserializer() {
        super(MultiAuthorizationSubscription.class);
    }

    private static final String ERROR_EXPECTED_START_OBJECT = "Expected START_OBJECT for MultiAuthorizationSubscription.";

    private final AuthorizationSubscriptionDeserializer subscriptionDeserializer = new AuthorizationSubscriptionDeserializer();

    @Override
    public MultiAuthorizationSubscription deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(MultiAuthorizationSubscription.class, ERROR_EXPECTED_START_OBJECT);
        }

        val multiSubscription = new MultiAuthorizationSubscription();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val subscriptionId = parser.currentName();
            parser.nextToken();
            val subscription = subscriptionDeserializer.deserialize(parser, context);
            multiSubscription.addSubscription(subscriptionId, subscription);
        }

        return multiSubscription;
    }
}
