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
import io.sapl.api.pdp.MultiAuthorizationDecision;

import java.io.IOException;

import lombok.val;

/**
 * Jackson serializer for MultiAuthorizationDecision.
 * <p>
 * Serializes to a JSON object mapping subscription IDs to their decisions.
 *
 * <pre>{@code
 * {
 *   "read-file": {"decision": "PERMIT"},
 *   "write-file": {"decision": "DENY"}
 * }
 * }</pre>
 */
public class MultiAuthorizationDecisionSerializer extends JsonSerializer<MultiAuthorizationDecision> {

    private final AuthorizationDecisionSerializer decisionSerializer = new AuthorizationDecisionSerializer();

    @Override
    public void serialize(MultiAuthorizationDecision multiDecision, JsonGenerator generator,
            SerializerProvider serializers) throws IOException {
        generator.writeStartObject();
        for (val identifiable : multiDecision) {
            generator.writeFieldName(identifiable.subscriptionId());
            decisionSerializer.serialize(identifiable.decision(), generator, serializers);
        }
        generator.writeEndObject();
    }
}
