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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;

import java.io.IOException;

/**
 * Jackson serializer for AuthorizationDecision.
 * <p>
 * Serialization rules:
 * <ul>
 * <li>decision field is always included</li>
 * <li>obligations field is omitted if empty</li>
 * <li>advice field is omitted if empty</li>
 * <li>resource field is omitted if UndefinedValue</li>
 * </ul>
 * <p>
 * Example output for a simple PERMIT decision:
 *
 * <pre>{@code
 * {"decision":"PERMIT"}
 * }</pre>
 * <p>
 * Example output with obligations and resource:
 *
 * <pre>{@code
 * {
 *   "decision": "PERMIT",
 *   "obligations": [{"type": "log", "message": "Access granted"}],
 *   "resource": {"filtered": true}
 * }
 * }</pre>
 */
public class AuthorizationDecisionSerializer extends JsonSerializer<AuthorizationDecision> {

    private final ValueSerializer valueSerializer = new ValueSerializer();

    @Override
    public void serialize(AuthorizationDecision decision, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeStartObject();

        generator.writeStringField("decision", decision.decision().name());

        if (!decision.obligations().isEmpty()) {
            generator.writeFieldName("obligations");
            serializeValueList(decision.obligations(), generator, serializers);
        }

        if (!decision.advice().isEmpty()) {
            generator.writeFieldName("advice");
            serializeValueList(decision.advice(), generator, serializers);
        }

        if (!(decision.resource() instanceof UndefinedValue)) {
            generator.writeFieldName("resource");
            valueSerializer.serialize(decision.resource(), generator, serializers);
        }

        if (!(decision.error() instanceof UndefinedValue) && !(decision.error() instanceof ErrorValue)) {
            generator.writeFieldName("error");
            valueSerializer.serialize(decision.error(), generator, serializers);
        }

        generator.writeEndObject();
    }

    private void serializeValueList(java.util.List<Value> values, JsonGenerator generator,
            SerializerProvider serializers) throws IOException {
        generator.writeStartArray();
        for (Value value : values) {
            valueSerializer.serialize(value, generator, serializers);
        }
        generator.writeEndArray();
    }
}
