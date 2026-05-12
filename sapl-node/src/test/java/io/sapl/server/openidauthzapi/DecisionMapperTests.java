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
        void plainPermitMapsToTrue() {
            assertThat(DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER).decision()).isTrue();
        }

        @Test
        void permitWithObligationsMapsToFalse() {
            final var sapl = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            assertThat(DecisionMapper.map(sapl, MAPPER).decision()).isFalse();
        }

        @Test
        void permitWithTransformedResourceMapsToFalse() {
            final var sapl = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            assertThat(DecisionMapper.map(sapl, MAPPER).decision()).isFalse();
        }

        @Test
        void permitWithAdviceOnlyMapsToTrue() {
            final var sapl = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                    Value.ofArray(Value.of("send-email")), Value.UNDEFINED);
            assertThat(DecisionMapper.map(sapl, MAPPER).decision()).isTrue();
        }

        @Test
        void denyMapsToFalse() {
            assertThat(DecisionMapper.map(AuthorizationDecision.DENY, MAPPER).decision()).isFalse();
        }

        @Test
        void notApplicableMapsToFalse() {
            assertThat(DecisionMapper.map(AuthorizationDecision.NOT_APPLICABLE, MAPPER).decision()).isFalse();
        }

        @Test
        void indeterminateMapsToFalse() {
            assertThat(DecisionMapper.map(AuthorizationDecision.INDETERMINATE, MAPPER).decision()).isFalse();
        }

        @Test
        void suspendMapsToFalse() {
            assertThat(DecisionMapper.map(AuthorizationDecision.SUSPEND, MAPPER).decision()).isFalse();
        }
    }

    @Nested
    @DisplayName("context.sapl.decision carries the SAPL verb for every decision")
    class VerbInContext {

        @Test
        void permitVerbSurfaces() {
            assertSaplDecision(DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER), Decision.PERMIT);
        }

        @Test
        void denyVerbSurfaces() {
            assertSaplDecision(DecisionMapper.map(AuthorizationDecision.DENY, MAPPER), Decision.DENY);
        }

        @Test
        void notApplicableVerbSurfaces() {
            assertSaplDecision(DecisionMapper.map(AuthorizationDecision.NOT_APPLICABLE, MAPPER),
                    Decision.NOT_APPLICABLE);
        }

        @Test
        void indeterminateVerbSurfaces() {
            assertSaplDecision(DecisionMapper.map(AuthorizationDecision.INDETERMINATE, MAPPER), Decision.INDETERMINATE);
        }

        @Test
        void suspendVerbSurfaces() {
            assertSaplDecision(DecisionMapper.map(AuthorizationDecision.SUSPEND, MAPPER), Decision.SUSPEND);
        }

        private static void assertSaplDecision(OpenIdEvaluationResponse response, Decision expected) {
            assertThat(response.context()).isNotNull().containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsEntry(DecisionMapper.SAPL_DECISION_KEY, expected.name());
        }
    }

    @Nested
    @DisplayName("reason field present whenever the boolean is false")
    class ReasonFields {

        @Test
        void plainPermitHasNoReason() {
            final var ctx = DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER).context();
            assertThat(ctx).doesNotContainKey(DecisionMapper.REASON_ADMIN_KEY)
                    .doesNotContainKey(DecisionMapper.REASON_USER_KEY);
        }

        @Test
        void denyCarriesUserReason() {
            assertReasonUser(DecisionMapper.map(AuthorizationDecision.DENY, MAPPER), DecisionMapper.REASON_DENY_EN);
        }

        @Test
        void notApplicableCarriesUserReason() {
            assertReasonUser(DecisionMapper.map(AuthorizationDecision.NOT_APPLICABLE, MAPPER),
                    DecisionMapper.REASON_NOT_APPLICABLE_EN);
        }

        @Test
        void indeterminateCarriesAdminReason() {
            assertReasonAdmin(DecisionMapper.map(AuthorizationDecision.INDETERMINATE, MAPPER),
                    DecisionMapper.REASON_INDETERMINATE_EN);
        }

        @Test
        void suspendCarriesUserReason() {
            assertReasonUser(DecisionMapper.map(AuthorizationDecision.SUSPEND, MAPPER),
                    DecisionMapper.REASON_SUSPEND_EN_403);
        }

        @Test
        void permitWithObligationsCarriesAdminReason() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertReasonAdmin(response, DecisionMapper.REASON_PERMIT_NEEDS_ENFORCEMENT_EN);
        }

        @Test
        void permitWithTransformedResourceCarriesAdminReason() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertReasonAdmin(response, DecisionMapper.REASON_PERMIT_NEEDS_ENFORCEMENT_EN);
        }

        private static void assertReasonAdmin(OpenIdEvaluationResponse response, String expected) {
            assertThat(response.context()).containsKey(DecisionMapper.REASON_ADMIN_KEY);
            assertThat(response.context().get(DecisionMapper.REASON_ADMIN_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsEntry(DecisionMapper.LANG_EN, expected);
        }

        private static void assertReasonUser(OpenIdEvaluationResponse response, String expected) {
            assertThat(response.context()).containsKey(DecisionMapper.REASON_USER_KEY);
            assertThat(response.context().get(DecisionMapper.REASON_USER_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsEntry(DecisionMapper.LANG_EN_403, expected);
        }
    }

    @Nested
    @DisplayName("obligations / advice / resource carry into context.sapl")
    class SaplExtensions {

        @Test
        void denyWithObligationsCarriesObligations() {
            final var sapl     = new AuthorizationDecision(Decision.DENY, Value.ofArray(Value.of("audit-log")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.OBLIGATIONS_KEY);
        }

        @Test
        void permitWithObligationsCarriesObligations() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
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
        void permitWithResourceCarriesResource() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.RESOURCE_KEY);
        }

        @Test
        void permitWithObligationsAdviceAndResourceCarriesAllThree() {
            final var sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("ob")),
                    Value.ofArray(Value.of("ad")), Value.of("res"));
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context()).containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class)).containsKeys(
                            DecisionMapper.OBLIGATIONS_KEY, DecisionMapper.ADVICE_KEY, DecisionMapper.RESOURCE_KEY);
        }

        @Test
        void suspendWithObligationsCarriesBothVerbAndObligations() {
            final var sapl     = new AuthorizationDecision(Decision.SUSPEND, Value.ofArray(Value.of("step-up")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            final var response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsKeys(DecisionMapper.SAPL_DECISION_KEY, DecisionMapper.OBLIGATIONS_KEY)
                    .containsEntry(DecisionMapper.SAPL_DECISION_KEY, Decision.SUSPEND.name());
        }

        private static void assertSaplExtensionPresent(OpenIdEvaluationResponse response, String extensionKey) {
            assertThat(response.context()).isNotNull().containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class)).containsKey(extensionKey);
        }
    }
}
