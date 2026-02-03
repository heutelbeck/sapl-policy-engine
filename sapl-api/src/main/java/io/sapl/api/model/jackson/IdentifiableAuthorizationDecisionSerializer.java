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

import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for IdentifiableAuthorizationDecision.
 * <p>
 * Serializes to a JSON object with subscriptionId and the decision fields.
 */
public class IdentifiableAuthorizationDecisionSerializer extends StdSerializer<IdentifiableAuthorizationDecision> {

    private final AuthorizationDecisionSerializer decisionSerializer = new AuthorizationDecisionSerializer();

    public IdentifiableAuthorizationDecisionSerializer() {
        super(IdentifiableAuthorizationDecision.class);
    }

    @Override
    public void serialize(IdentifiableAuthorizationDecision identifiable, JsonGenerator generator,
            SerializationContext serializers) {
        generator.writeStartObject();
        generator.writeStringProperty("subscriptionId", identifiable.subscriptionId());
        generator.writeName("decision");
        decisionSerializer.serialize(identifiable.decision(), generator, serializers);
        generator.writeEndObject();
    }
}
