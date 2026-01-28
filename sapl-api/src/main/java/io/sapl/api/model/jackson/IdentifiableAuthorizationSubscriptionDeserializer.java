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

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import lombok.val;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer for IdentifiableAuthorizationSubscription.
 */
public class IdentifiableAuthorizationSubscriptionDeserializer
        extends StdDeserializer<IdentifiableAuthorizationSubscription> {

    /**
     * Default constructor required by Jackson 3.
     */
    public IdentifiableAuthorizationSubscriptionDeserializer() {
        super(IdentifiableAuthorizationSubscription.class);
    }

    private static final String ERROR_EXPECTED_START_OBJECT   = "Expected START_OBJECT for IdentifiableAuthorizationSubscription.";
    private static final String ERROR_MISSING_REQUIRED_FIELDS = "IdentifiableAuthorizationSubscription requires subscriptionId and subscription fields.";

    private final AuthorizationSubscriptionDeserializer subscriptionDeserializer = new AuthorizationSubscriptionDeserializer();

    @Override
    public IdentifiableAuthorizationSubscription deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            context.reportInputMismatch(IdentifiableAuthorizationSubscription.class, ERROR_EXPECTED_START_OBJECT);
        }

        String                    subscriptionId = null;
        AuthorizationSubscription subscription   = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "subscriptionId" -> subscriptionId = parser.getString();
            case "subscription"   -> subscription = subscriptionDeserializer.deserialize(parser, context);
            default               -> parser.skipChildren();
            }
        }

        if (subscriptionId == null || subscription == null) {
            context.reportInputMismatch(IdentifiableAuthorizationSubscription.class, ERROR_MISSING_REQUIRED_FIELDS);
        }

        return new IdentifiableAuthorizationSubscription(subscriptionId, subscription);
    }
}
