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
package io.sapl.node.http;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

@DisplayName("Published HTTP OpenAPI contract for the PDP")
class PdpHttpOpenApiContractTests {

    @Test
    @DisplayName("AuthorizationDecision marks only the decision field as required, matching omit-when-empty serialization")
    @SuppressWarnings("unchecked")
    void whenInspectingAuthorizationDecisionSchemaThenOnlyDecisionIsRequired() {
        final Map<String, Object> schema = authorizationDecisionSchema();

        final Object required = schema.get("required");

        assertThat(required).asInstanceOf(list(String.class)).as(
                "a plain PERMIT serializes to {\"decision\":\"PERMIT\"}, so only decision is always present on the wire")
                .containsExactly("decision").doesNotContain("obligations", "advice", "resource");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> authorizationDecisionSchema() {
        try (InputStream in = getClass().getResourceAsStream("/static/openapi/pdp-http.yaml")) {
            assertThat(in).as("static OpenAPI document must be on the classpath").isNotNull();
            final Map<String, Object> root         = new Yaml().load(in);
            final Map<String, Object> components   = (Map<String, Object>) root.get("components");
            final Map<String, Object> schemas      = (Map<String, Object>) components.get("schemas");
            val                       authDecision = (Map<String, Object>) schemas.get("AuthorizationDecision");
            assertThat(authDecision).as("AuthorizationDecision schema must be declared").isNotNull();
            return authDecision;
        } catch (Exception e) {
            throw new IllegalStateException("unable to read OpenAPI document", e);
        }
    }
}
