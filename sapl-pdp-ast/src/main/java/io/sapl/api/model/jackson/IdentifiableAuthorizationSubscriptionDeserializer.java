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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;

import java.io.IOException;

/**
 * Jackson deserializer for IdentifiableAuthorizationSubscription.
 */
public class IdentifiableAuthorizationSubscriptionDeserializer
        extends JsonDeserializer<IdentifiableAuthorizationSubscription> {

    private final AuthorizationSubscriptionDeserializer subscriptionDeserializer = new AuthorizationSubscriptionDeserializer();

    @Override
    public IdentifiableAuthorizationSubscription deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT for IdentifiableAuthorizationSubscription.");
        }

        String                    subscriptionId = null;
        AuthorizationSubscription subscription   = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "subscriptionId" -> subscriptionId = parser.getText();
            case "subscription"   -> subscription = subscriptionDeserializer.deserialize(parser, context);
            default               -> parser.skipChildren();
            }
        }

        if (subscriptionId == null || subscription == null) {
            throw new IOException(
                    "IdentifiableAuthorizationSubscription requires subscriptionId and subscription fields.");
        }

        return new IdentifiableAuthorizationSubscription(subscriptionId, subscription);
    }
}
