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

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;

import lombok.val;

/**
 * Jackson deserializer for AuthorizationSubscription.
 */
public class AuthorizationSubscriptionDeserializer extends StdDeserializer<AuthorizationSubscription> {

    /**
     * Default constructor required by Jackson 3.
     */
    public AuthorizationSubscriptionDeserializer() {
        super(AuthorizationSubscription.class);
    }

    private static final String ERROR_EXPECTED_START_OBJECT  = "Expected START_OBJECT for AuthorizationSubscription.";
    private static final String ERROR_MISSING_REQUIRED_FIELD = "AuthorizationSubscription requires subject, action, and resource fields.";

    private final ValueDeserializer valueDeserializer = new ValueDeserializer();

    @Override
    public AuthorizationSubscription deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            context.reportInputMismatch(AuthorizationSubscription.class, ERROR_EXPECTED_START_OBJECT);
        }

        Value       subject     = null;
        Value       action      = null;
        Value       resource    = null;
        Value       environment = Value.UNDEFINED;
        ObjectValue secrets     = Value.EMPTY_OBJECT;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "subject"     -> subject = valueDeserializer.deserialize(parser, context);
            case "action"      -> action = valueDeserializer.deserialize(parser, context);
            case "resource"    -> resource = valueDeserializer.deserialize(parser, context);
            case "environment" -> environment = valueDeserializer.deserialize(parser, context);
            case "secrets"     -> secrets = toObjectValue(valueDeserializer.deserialize(parser, context));
            default            -> parser.skipChildren();
            }
        }

        if (subject == null || action == null || resource == null) {
            context.reportInputMismatch(AuthorizationSubscription.class, ERROR_MISSING_REQUIRED_FIELD);
        }

        return new AuthorizationSubscription(subject, action, resource, environment, secrets);
    }

    private static ObjectValue toObjectValue(Value value) {
        if (value instanceof ObjectValue ov) {
            return ov;
        }
        return Value.EMPTY_OBJECT;
    }
}
