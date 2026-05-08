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
package io.sapl.server.openidauthzapi;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DecisionMapper translates SAPL AuthorizationDecision to OpenID response shape")
class DecisionMapperTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Nested
    @DisplayName("verb -> boolean mapping")
    class VerbMapping {

        @Test
        void permitMapsToTrueWithNoContext() {
            final var response = DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER);
            assertThat(response.decision()).isTrue();
            assertThat(response.context()).isNull();
        }

        @Test
        void denyMapsToFalseWithNoContext() {
            final var response = DecisionMapper.map(AuthorizationDecision.DENY, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context()).isNull();
        }

        @Test
        void notApplicableMapsToFalseWithNoContext() {
            final var response = DecisionMapper.map(AuthorizationDecision.NOT_APPLICABLE, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context()).isNull();
        }

        @Test
        void indeterminateMapsToFalseWithReasonAdmin() {
            final var response = DecisionMapper.map(AuthorizationDecision.INDETERMINATE, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context()).isNotNull().containsKey(DecisionMapper.REASON_ADMIN_KEY);
            assertThat(response.context().get(DecisionMapper.REASON_ADMIN_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsEntry(DecisionMapper.LANG_EN, DecisionMapper.REASON_INDETERMINATE_EN);
        }

        @Test
        void suspendMapsToFalseWithReasonUserAndSaplDecisionMarker() {
            final var response = DecisionMapper.map(AuthorizationDecision.SUSPEND, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context()).isNotNull().containsKey(DecisionMapper.REASON_USER_KEY)
                    .containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.REASON_USER_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsEntry(DecisionMapper.LANG_EN_403, DecisionMapper.REASON_SUSPEND_EN_403);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsEntry(DecisionMapper.SAPL_DECISION_KEY, DecisionMapper.SUSPEND_MARKER);
        }
    }

    @Nested
    @DisplayName("obligations / advice / resource carry into context.sapl")
    class SaplExtensions {

        @Test
        void permitWithObligationsCarriesObligations() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.decision()).isTrue();
            assertSaplExtensionPresent(response, DecisionMapper.OBLIGATIONS_KEY);
        }

        @Test
        void denyWithObligationsCarriesObligations() {
            final var sapl     = new AuthorizationDecision(Decision.DENY, Value.ofArray(Value.of("audit-log")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.decision()).isFalse();
            assertSaplExtensionPresent(response, DecisionMapper.OBLIGATIONS_KEY);
        }

        @Test
        void permitWithAdviceCarriesAdvice() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                    Value.ofArray(Value.of("send-email")), Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.ADVICE_KEY);
        }

        @Test
        void permitWithResourceTransformationCarriesResource() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.RESOURCE_KEY);
        }

        @Test
        void permitWithObligationsAndAdviceAndResourceCarriesAllThree() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("ob")),
                    Value.ofArray(Value.of("ad")), Value.of("res"));
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.context()).isNotNull().containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class)).containsKeys(
                            DecisionMapper.OBLIGATIONS_KEY, DecisionMapper.ADVICE_KEY, DecisionMapper.RESOURCE_KEY);
        }

        @Test
        void suspendWithObligationsCarriesBothMarkerAndObligations() {
            final var sapl     = new AuthorizationDecision(Decision.SUSPEND, Value.ofArray(Value.of("step-up")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsKeys(DecisionMapper.SAPL_DECISION_KEY, DecisionMapper.OBLIGATIONS_KEY)
                    .containsEntry(DecisionMapper.SAPL_DECISION_KEY, DecisionMapper.SUSPEND_MARKER);
        }

        private static void assertSaplExtensionPresent(OpenIdEvaluationResponse response, String extensionKey) {
            assertThat(response.context()).isNotNull().containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class)).containsKey(extensionKey);
        }
    }
}
