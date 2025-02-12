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

import static com.spotify.hamcrest.jackson.IsJsonBoolean.jsonBoolean;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.test.StepVerifier;

class DefaultSAPLInterpreterPolicySetTests {

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private AuthorizationSubscription authorizationSubscription;

    private AnnotationAttributeContext attributeCtx;

    private AnnotationFunctionContext functionCtx;

    @BeforeEach
    void setUp() throws InitializationException {
        authorizationSubscription = new AuthorizationSubscription(null, null, null, null);
        attributeCtx              = new AnnotationAttributeContext();
        functionCtx               = new AnnotationFunctionContext();
        functionCtx.loadLibrary(FilterFunctionLibrary.class);
    }

    @Test
    void setPermit() {
        final var policySet = "set \"tests\" deny-overrides " + "policy \"testp\" permit";
        final var expected  = AuthorizationDecision.PERMIT;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void setDeny() {
        final var policySet = "set \"tests\" deny-overrides " + "policy \"testp\" deny";
        final var expected  = AuthorizationDecision.DENY;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void setNotApplicable() {
        final var policySet = "set \"tests\" deny-overrides " + "for true == false " + "policy \"testp\" deny";
        final var expected  = AuthorizationDecision.NOT_APPLICABLE;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void noApplicablePolicies() {
        final var policySet = "set \"tests\" deny-overrides " + "for true " + "policy \"testp\" deny true == false";
        final var expected  = AuthorizationDecision.NOT_APPLICABLE;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void setIndeterminate() {
        final var policySet = "set \"tests\" deny-overrides" + "for \"a\" > 4 " + "policy \"testp\" permit";
        final var expected  = AuthorizationDecision.INDETERMINATE;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void denyOverridesPermitAndDeny() {
        final var policySet = "set \"tests\" deny-overrides " + "policy \"testp1\" permit " + "policy \"testp2\" deny";
        final var expected  = AuthorizationDecision.DENY;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void denyOverridesPermitAndNotApplicableAndDeny() {
        final var policySet = "set \"tests\" deny-overrides " + "policy \"testp1\" permit "
                + "policy \"testp2\" permit true == false " + "policy \"testp3\" deny";
        final var expected  = AuthorizationDecision.DENY;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void denyOverridesPermitAndIndeterminateAndDeny() {
        final var policySet = "set \"tests\" deny-overrides " +   //
                "policy \"testp1\" permit " +               //
                "policy \"testp2\" permit \"a\" < 5 " +     //
                "policy \"testp3\" deny";
        final var expected  = AuthorizationDecision.DENY;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void importsInSetAvailableInPolicy() {
        final var policySet = "import filter.replace " + //
                "set \"tests\" deny-overrides " + //
                "policy \"testp1\" permit transform true |- replace(false)";

        StepVerifier
                .create(INTERPRETER.evaluate(authorizationSubscription, policySet, attributeCtx, functionCtx,
                        new HashMap<>()))
                .assertNext(decision -> assertThat(decision.getResource(), is(optionalWithValue(jsonBoolean(false)))))
                .verifyComplete();
    }

    @Test
    void importsDuplicatesByPolicySet() {
        final var policySet = "import filter.replace " +          //
                "import filter.replace " +                  //
                "set \"tests\" deny-overrides " +           //
                "policy \"testp1\" permit where true;";
        final var expected  = AuthorizationDecision.INDETERMINATE;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void variablesOnSetLevel() {
        final var policySet = "set \"tests\" deny-overrides " +   //
                "var var1 = true; " +                       //
                "policy \"testp1\" permit var1 == true";
        final var expected  = AuthorizationDecision.PERMIT;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void variablesOnSetLevelError() {
        final var policySet = "set \"tests\" deny-overrides " +   //
                "var var1 = true / null; " +                //
                "policy \"testp1\" permit";
        final var expected  = AuthorizationDecision.INDETERMINATE;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void variablesOverwriteInPolicy() {
        final var policySet = "set \"tests\" deny-overrides " +                        //
                "var var1 = true; " +                                            //
                "policy \"testp1\" permit where var var1 = 10; var1 == 10; " +   //
                "policy \"testp2\" deny where !(var1 == true);";
        final var expected  = AuthorizationDecision.PERMIT;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void subjectAsVariable() {
        final var policySet = "set \"test\" deny-overrides " +    //
                "var subject = null;  " +                   //
                "policy \"test\" permit";
        final var expected  = AuthorizationDecision.INDETERMINATE;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    @Test
    void variablesInPolicyMustNotLeakIntoNextPolicy() {
        final var policySet = "set \"test\" deny-overrides " + "var ps1 = true; " +   //
                "policy \"pol1\" permit where var p1 = 10; p1 == 10; " +        //
                "policy \"pol2\" deny where p1 == undefined;";
        final var expected  = AuthorizationDecision.DENY;
        assertThatDocumentEvaluationReturnsExpected(policySet, expected);
    }

    private void assertThatDocumentEvaluationReturnsExpected(String document, AuthorizationDecision expected) {
        StepVerifier.create(
                INTERPRETER.evaluate(authorizationSubscription, document, attributeCtx, functionCtx, new HashMap<>()))
                .expectNext(expected).verifyComplete();
    }

}
