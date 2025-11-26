/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;

import java.io.IOException;

/**
 * Jackson deserializer for AuthorizationSubscription.
 */
public class AuthorizationSubscriptionDeserializer extends JsonDeserializer<AuthorizationSubscription> {

    private final ValueDeserializer valueDeserializer = new ValueDeserializer();

    @Override
    public AuthorizationSubscription deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT for AuthorizationSubscription.");
        }

        Value subject     = null;
        Value action      = null;
        Value resource    = null;
        Value environment = Value.UNDEFINED;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "subject"     -> subject = valueDeserializer.deserialize(parser, context);
            case "action"      -> action = valueDeserializer.deserialize(parser, context);
            case "resource"    -> resource = valueDeserializer.deserialize(parser, context);
            case "environment" -> environment = valueDeserializer.deserialize(parser, context);
            default            -> parser.skipChildren();
            }
        }

        if (subject == null || action == null || resource == null) {
            throw new IOException("AuthorizationSubscription requires subject, action, and resource fields.");
        }

        return new AuthorizationSubscription(subject, action, resource, environment);
    }
}
