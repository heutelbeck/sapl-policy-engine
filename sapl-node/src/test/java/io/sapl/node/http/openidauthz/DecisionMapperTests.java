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
package io.sapl.node.http.openidauthz;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DecisionMapper translates SAPL AuthorizationDecision to OpenID response shape")
class DecisionMapperTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static void assertSaplDecisionVerb(OpenIdEvaluationResponse response, Decision expected) {
        assertThat(response.context()).isNotNull().containsKey(DecisionMapper.SAPL_KEY);
        assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry(DecisionMapper.SAPL_DECISION_KEY, expected.name());
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

    private static void assertSaplExtensionPresent(OpenIdEvaluationResponse response, String extensionKey) {
        assertThat(response.context()).isNotNull().containsKey(DecisionMapper.SAPL_KEY);
        assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class)).containsKey(extensionKey);
    }

    @Nested
    @DisplayName("verb -> boolean mapping")
    class VerbMapping {

        @Test
        void plainPermitMapsToTrue() {
            assertThat(DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER).decision()).isTrue();
        }

        @Test
        void permitWithObligationsMapsToFalse() {
            val sapl = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            assertThat(DecisionMapper.map(sapl, MAPPER).decision()).isFalse();
        }

        @Test
        void permitWithTransformedResourceMapsToFalse() {
            val sapl = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            assertThat(DecisionMapper.map(sapl, MAPPER).decision()).isFalse();
        }

        @Test
        void permitWithAdviceOnlyMapsToTrue() {
            val sapl = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
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

        @ParameterizedTest(name = "{0} verb surfaces under context.sapl.decision")
        @EnumSource(Decision.class)
        void everyVerbSurfaces(Decision verb) {
            assertSaplDecisionVerb(DecisionMapper.map(authorizationDecisionFor(verb), MAPPER), verb);
        }

        private static AuthorizationDecision authorizationDecisionFor(Decision verb) {
            return switch (verb) {
            case PERMIT         -> AuthorizationDecision.PERMIT;
            case DENY           -> AuthorizationDecision.DENY;
            case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
            case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
            case SUSPEND        -> AuthorizationDecision.SUSPEND;
            };
        }
    }

    @Nested
    @DisplayName("contract")
    class Contract {

        @Test
        void mapRejectsNullSaplDecision() {
            assertThatNullPointerException().isThrownBy(() -> DecisionMapper.map(null, MAPPER));
        }

        @Test
        void plainPermitStillCarriesSaplVerbInContext() {
            assertSaplDecisionVerb(DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER), Decision.PERMIT);
        }

        @Test
        void multipleObligationsPreserveOrderInContext() {
            val sapl     = new AuthorizationDecision(Decision.DENY,
                    Value.ofArray(Value.of("first"), Value.of("second"), Value.of("third")), Value.EMPTY_ARRAY,
                    Value.UNDEFINED);
            val response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .extractingByKey(DecisionMapper.OBLIGATIONS_KEY).asString().contains("first", "second", "third");
        }
    }

    @Nested
    @DisplayName("reason field present whenever the boolean is false")
    class ReasonFields {

        @Test
        void plainPermitHasNoReason() {
            val ctx = DecisionMapper.map(AuthorizationDecision.PERMIT, MAPPER).context();
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
            val sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            val response = DecisionMapper.map(sapl, MAPPER);
            assertReasonAdmin(response, DecisionMapper.REASON_PERMIT_NEEDS_ENFORCEMENT_EN);
        }

        @Test
        void permitWithTransformedResourceCarriesAdminReason() {
            val sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            val response = DecisionMapper.map(sapl, MAPPER);
            assertReasonAdmin(response, DecisionMapper.REASON_PERMIT_NEEDS_ENFORCEMENT_EN);
        }

    }

    @Nested
    @DisplayName("obligations / advice / resource carry into context.sapl")
    class SaplExtensions {

        @Test
        void denyWithObligationsCarriesObligations() {
            val sapl     = new AuthorizationDecision(Decision.DENY, Value.ofArray(Value.of("audit-log")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            val response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.OBLIGATIONS_KEY);
        }

        @Test
        void permitWithObligationsCarriesObligations() {
            val sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            val response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.OBLIGATIONS_KEY);
        }

        @Test
        void permitWithAdviceCarriesAdvice() {
            val sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                    Value.ofArray(Value.of("send-email")), Value.UNDEFINED);
            val response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.ADVICE_KEY);
        }

        @Test
        void permitWithResourceCarriesResource() {
            val sapl     = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted"));
            val response = DecisionMapper.map(sapl, MAPPER);
            assertSaplExtensionPresent(response, DecisionMapper.RESOURCE_KEY);
        }

        @Test
        void permitWithObligationsAdviceAndResourceCarriesAllThree() {
            val sapl     = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("ob")),
                    Value.ofArray(Value.of("ad")), Value.of("res"));
            val response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.decision()).isFalse();
            assertThat(response.context()).containsKey(DecisionMapper.SAPL_KEY);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class)).containsKeys(
                            DecisionMapper.OBLIGATIONS_KEY, DecisionMapper.ADVICE_KEY, DecisionMapper.RESOURCE_KEY);
        }

        @Test
        void suspendWithObligationsCarriesBothVerbAndObligations() {
            val sapl     = new AuthorizationDecision(Decision.SUSPEND, Value.ofArray(Value.of("step-up")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            val response = DecisionMapper.map(sapl, MAPPER);
            assertThat(response.context().get(DecisionMapper.SAPL_KEY))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsKeys(DecisionMapper.SAPL_DECISION_KEY, DecisionMapper.OBLIGATIONS_KEY)
                    .containsEntry(DecisionMapper.SAPL_DECISION_KEY, Decision.SUSPEND.name());
        }

    }
}
