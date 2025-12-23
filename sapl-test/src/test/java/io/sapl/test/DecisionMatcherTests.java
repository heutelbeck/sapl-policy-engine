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
package io.sapl.test;

import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.test.Matchers.isDeny;
import static io.sapl.test.Matchers.isIndeterminate;
import static io.sapl.test.Matchers.isNotApplicable;
import static io.sapl.test.Matchers.isPermit;
import static io.sapl.test.Matchers.isDecision;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DecisionMatcherTests {

    @ParameterizedTest
    @EnumSource(Decision.class)
    void whenDecisionMatches_thenReturnsTrue(Decision decision) {
        var matcher       = isDecision(decision);
        var authzDecision = new AuthorizationDecision(decision, List.of(), List.of(), Value.UNDEFINED);

        assertThat(matcher.test(authzDecision)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("decisionMismatchCases")
    void whenDecisionDoesNotMatch_thenReturnsFalse(Decision expected, Decision actual) {
        var matcher       = isDecision(expected);
        var authzDecision = new AuthorizationDecision(actual, List.of(), List.of(), Value.UNDEFINED);

        assertThat(matcher.test(authzDecision)).isFalse();
    }

    static Stream<Arguments> decisionMismatchCases() {
        return Stream.of(arguments(Decision.PERMIT, Decision.DENY), arguments(Decision.DENY, Decision.PERMIT),
                arguments(Decision.INDETERMINATE, Decision.NOT_APPLICABLE),
                arguments(Decision.NOT_APPLICABLE, Decision.INDETERMINATE));
    }

    @Test
    void whenNullDecision_thenReturnsFalse() {
        assertThat(isPermit().test(null)).isFalse();
    }

    @Test
    void whenContainsObligation_thenMatchesIfPresent() {
        var cultistObligation = Value.of("summon_shoggoth");
        var authzDecision     = new AuthorizationDecision(Decision.PERMIT, List.of(cultistObligation), List.of(),
                Value.UNDEFINED);

        assertThat(isPermit().containsObligation(cultistObligation).test(authzDecision)).isTrue();
    }

    @Test
    void whenContainsObligation_thenFailsIfMissing() {
        var elderSign     = Value.of("ward_against_great_old_ones");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), Value.UNDEFINED);

        assertThat(isPermit().containsObligation(elderSign).test(authzDecision)).isFalse();
    }

    @Test
    void whenContainsMultipleObligations_thenMatchesIfAllPresent() {
        var chantRitual    = Value.of("chant_ritual");
        var offerSacrifice = Value.of("offer_sacrifice");
        var authzDecision  = new AuthorizationDecision(Decision.DENY,
                List.of(chantRitual, offerSacrifice, Value.of("extra")), List.of(), Value.UNDEFINED);

        assertThat(isDeny().containsObligations(chantRitual, offerSacrifice).test(authzDecision)).isTrue();
    }

    @Test
    void whenContainsMultipleObligations_thenFailsIfAnyMissing() {
        var openPortal    = Value.of("open_portal");
        var authzDecision = new AuthorizationDecision(Decision.DENY, List.of(openPortal), List.of(), Value.UNDEFINED);

        assertThat(isDeny().containsObligations(openPortal, Value.of("close_portal")).test(authzDecision)).isFalse();
    }

    @Test
    void whenContainsObligationMatching_thenMatchesWithPredicate() {
        var necronomicon  = Value.of("necronomicon_page_42");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(necronomicon), List.of(),
                Value.UNDEFINED);

        assertThat(isPermit()
                .containsObligationMatching(v -> v instanceof TextValue tv && tv.value().contains("necronomicon"))
                .test(authzDecision)).isTrue();
    }

    @Test
    void whenContainsAdvice_thenMatchesIfPresent() {
        var warningAdvice = Value.of("beware_the_thing_on_the_doorstep");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(warningAdvice),
                Value.UNDEFINED);

        assertThat(isPermit().containsAdvice(warningAdvice).test(authzDecision)).isTrue();
    }

    @Test
    void whenContainsAdvice_thenFailsIfMissing() {
        var advice        = Value.of("consult_miskatonic_archives");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), Value.UNDEFINED);

        assertThat(isPermit().containsAdvice(advice).test(authzDecision)).isFalse();
    }

    @Test
    void whenContainsMultipleAdvices_thenMatchesIfAllPresent() {
        var advice1       = Value.of("avoid_innsmouth");
        var advice2       = Value.of("never_read_aloud");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(advice1, advice2),
                Value.UNDEFINED);

        assertThat(isPermit().containsAdvices(advice1, advice2).test(authzDecision)).isTrue();
    }

    @Test
    void whenContainsAdviceMatching_thenMatchesWithPredicate() {
        var advice        = Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(advice), Value.UNDEFINED);

        assertThat(isPermit().containsAdviceMatching(v -> v instanceof TextValue tv && tv.value().contains("Cthulhu"))
                .test(authzDecision)).isTrue();
    }

    @Test
    void whenWithResource_thenMatchesExactly() {
        var forbiddenTome = Value.of("de_vermis_mysteriis");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), forbiddenTome);

        assertThat(isPermit().withResource(forbiddenTome).test(authzDecision)).isTrue();
    }

    @Test
    void whenWithResource_thenFailsOnMismatch() {
        var expected      = Value.of("pnakotic_manuscripts");
        var actual        = Value.of("book_of_eibon");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), actual);

        assertThat(isPermit().withResource(expected).test(authzDecision)).isFalse();
    }

    @Test
    void whenChainingAllConstraints_thenMatchesIfAllSatisfied() {
        var resource      = Value.of("arkham_sanitarium_records");
        var obligation    = Value.of("log_access");
        var advice        = Value.of("consult_dr_armitage");
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(advice), resource);

        assertThat(isPermit().withResource(resource).containsObligation(obligation).containsAdvice(advice)
                .test(authzDecision)).isTrue();
    }

    @Test
    void whenDescribe_thenReturnsReadableDescription() {
        var matcher = isPermit().withResource(Value.of("resource")).containsObligation(Value.of("obligation"))
                .containsAdvice(Value.of("advice"));

        assertThat(matcher.describe()).contains("PERMIT").contains("resource").contains("obligation")
                .contains("advice");
    }

    @Test
    void whenDescribeMismatch_thenExplainsFailure() {
        var matcher       = isDeny().containsObligation(Value.of("required"));
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), Value.UNDEFINED);

        assertThat(matcher.describeMismatch(authzDecision)).contains("expected DENY").contains("PERMIT");
    }

    @Test
    void whenDescribeMismatchOnNull_thenExplainsNull() {
        assertThat(isPermit().describeMismatch(null)).contains("null");
    }

    @Test
    void whenDescribeMismatchMissingObligation_thenExplainsWhichMissing() {
        var required      = Value.of("elder_sign_ward");
        var matcher       = isDeny().containsObligation(required);
        var authzDecision = new AuthorizationDecision(Decision.DENY, List.of(), List.of(), Value.UNDEFINED);

        assertThat(matcher.describeMismatch(authzDecision)).contains("missing obligation");
    }

    @Test
    void whenDescribeMismatchMissingAdvice_thenExplainsWhichMissing() {
        var required      = Value.of("flee_immediately");
        var matcher       = isIndeterminate().containsAdvice(required);
        var authzDecision = new AuthorizationDecision(Decision.INDETERMINATE, List.of(), List.of(), Value.UNDEFINED);

        assertThat(matcher.describeMismatch(authzDecision)).contains("missing advice");
    }

    @Test
    void whenIsNotApplicable_thenMatchesNotApplicableDecision() {
        var authzDecision = new AuthorizationDecision(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);

        assertThat(isNotApplicable().test(authzDecision)).isTrue();
    }

    @Test
    void whenPredicatesDoNotMatch_thenReturnsFalse() {
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(Value.of("normal")), List.of(),
                Value.UNDEFINED);

        assertThat(isPermit()
                .containsObligationMatching(v -> v instanceof TextValue tv && tv.value().startsWith("eldritch"))
                .test(authzDecision)).isFalse();
    }

    @Test
    void whenAdvicePredicateDoesNotMatch_thenReturnsFalse() {
        var authzDecision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(Value.of("mundane_advice")),
                Value.UNDEFINED);

        assertThat(isPermit()
                .containsAdviceMatching(v -> v instanceof TextValue tv && tv.value().contains("cosmic_horror"))
                .test(authzDecision)).isFalse();
    }
}
