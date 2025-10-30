/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryAttributeRepository;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.util.HashMap;
import java.util.stream.Stream;

import static com.spotify.hamcrest.jackson.IsJsonBoolean.jsonBoolean;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultSAPLInterpreterPolicySetTests {

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private AuthorizationSubscription authorizationSubscription;
    private AttributeStreamBroker     attributeStreamBroker;
    private AnnotationFunctionContext functionCtx;

    @BeforeEach
    void setUp() throws InitializationException {
        authorizationSubscription = new AuthorizationSubscription(null, null, null, null);
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        attributeStreamBroker = new CachingAttributeStreamBroker(attributeRepository);
        functionCtx           = new AnnotationFunctionContext();
        functionCtx.loadLibrary(FilterFunctionLibrary.class);
    }

    static Stream<Arguments> provideTestCasesForValidateSet() {
        return Stream.of(Arguments.of("setPermit", """
                set "tests"
                deny-overrides
                policy "testp" permit
                """, AuthorizationDecision.PERMIT), Arguments.of("setDeny", """
                set "tests"
                deny-overrides
                policy "testp" deny
                """, AuthorizationDecision.DENY), Arguments.of("setNotApplicable", """
                set "tests"
                deny-overrides
                for true == false
                policy "testp" deny
                """, AuthorizationDecision.NOT_APPLICABLE), Arguments.of("noApplicablePolicies", """
                set "tests"
                deny-overrides
                for true
                policy "testp" deny true == false
                """, AuthorizationDecision.NOT_APPLICABLE), Arguments.of("setIndeterminate", """
                set "tests"
                deny-overrides
                for "a" > 4
                policy "testp" permit
                """, AuthorizationDecision.INDETERMINATE), Arguments.of("denyOverridesPermitAndDeny", """
                set "tests"
                deny-overrides
                policy "testp1" permit
                policy "testp2" deny
                """, AuthorizationDecision.DENY), Arguments.of("denyOverridesPermitAndNotApplicableAndDeny", """
                set "tests"
                deny-overrides
                policy "testp1"
                permit policy "testp2"
                permit true == false
                policy "testp3" deny
                """, AuthorizationDecision.DENY), Arguments.of("denyOverridesPermitAndIndeterminateAndDeny", """
                set "tests"
                deny-overrides
                policy "testp1" permit
                policy "testp2" permit "a" < 5
                policy "testp3" deny
                """, AuthorizationDecision.DENY), Arguments.of("importsDuplicatesByPolicySetIgnored", """
                import filter.replace
                import filter.replace
                set "tests"
                deny-overrides
                policy "testp1" permit
                where true;
                """, AuthorizationDecision.PERMIT), Arguments.of("variablesOnSetLevel", """
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit var1 == true
                """, AuthorizationDecision.PERMIT), Arguments.of("variablesOnSetLevelError", """
                set "tests" deny-overrides
                var var1 = true / null;
                policy "testp1" permit
                """, AuthorizationDecision.INDETERMINATE), Arguments.of("variablesOverwriteInPolicy", """
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit where var var1 = 10; var1 == 10;
                policy "testp2" deny where !(var1 == true);
                """, AuthorizationDecision.PERMIT), Arguments.of("subjectAsVariable", """
                set "test" deny-overrides
                var subject = null;
                policy "test" permit
                """, AuthorizationDecision.INDETERMINATE),
                Arguments.of("variablesInPolicyMustNotLeakIntoNextPolicy", """
                        set "test" deny-overrides
                        var ps1 = true;

                        policy "pol1" permit
                        where
                          var p1 = 10;
                          p1 == 10;

                        policy "pol2" deny
                        where p1 == undefined;
                        """, AuthorizationDecision.DENY));
    }

    @ParameterizedTest
    @MethodSource("provideTestCasesForValidateSet")
    void validateSet_ShouldReturnGivenDecision(String caseName, String policySet,
            AuthorizationDecision expectedDecision) {
        assertThatDocumentEvaluationReturnsExpected(policySet, expectedDecision);
    }

    @Test
    void importsInSetAvailableInPolicy() {
        final var policySet = """
                import filter.replace
                set "tests"
                deny-overrides
                policy "testp1" permit
                transform true |- replace(false)
                """;
        StepVerifier
                .create(INTERPRETER.evaluate(authorizationSubscription, policySet, attributeStreamBroker, functionCtx,
                        new HashMap<>()))
                .assertNext(decision -> assertThat(decision.getResource(), is(optionalWithValue(jsonBoolean(false)))))
                .verifyComplete();
    }

    private void assertThatDocumentEvaluationReturnsExpected(String document, AuthorizationDecision expected) {
        StepVerifier.create(INTERPRETER.evaluate(authorizationSubscription, document, attributeStreamBroker,
                functionCtx, new HashMap<>())).expectNext(expected).verifyComplete();
    }

}
