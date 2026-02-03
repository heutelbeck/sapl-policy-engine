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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;

import java.util.List;

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
public class AuthorizationDecisionSerializer extends StdSerializer<AuthorizationDecision> {

    private final ValueSerializer valueSerializer = new ValueSerializer();

    public AuthorizationDecisionSerializer() {
        super(AuthorizationDecision.class);
    }

    @Override
    public void serialize(AuthorizationDecision decision, JsonGenerator generator, SerializationContext serializers) {
        generator.writeStartObject();

        generator.writeStringProperty("decision", decision.decision().name());

        if (!decision.obligations().isEmpty()) {
            generator.writeName("obligations");
            serializeValueList(decision.obligations(), generator, serializers);
        }

        if (!decision.advice().isEmpty()) {
            generator.writeName("advice");
            serializeValueList(decision.advice(), generator, serializers);
        }

        if (!(decision.resource() instanceof UndefinedValue)) {
            generator.writeName("resource");
            valueSerializer.serialize(decision.resource(), generator, serializers);
        }

        generator.writeEndObject();
    }

    private void serializeValueList(List<Value> values, JsonGenerator generator, SerializationContext serializers) {
        generator.writeStartArray();
        for (Value value : values) {
            valueSerializer.serialize(value, generator, serializers);
        }
        generator.writeEndArray();
    }
}
