/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.unit;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Mono;

class StepBuilderTests {

    private static final AuthorizationSubscription AUTHZ_SUB = AuthorizationSubscription.of("willi", "not_matching",
            "something");
    private static final DefaultSAPLInterpreter    PARSER    = new DefaultSAPLInterpreter();

    @Test
    void test_NotApplicableDecisionWhenNotMatchingPolicyInUnitTest() {
        var document = PARSER.parse("policy \"test\" permit action == \"read\"");
        StepBuilder.newBuilderAtWhenStep(document, new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).when(AUTHZ_SUB).expectNotApplicable().verify();

    }

    @Test
    void test_matchResultNotBoolean() {
        var document = Mockito.mock(SAPL.class);
        Mockito.when(document.matches()).thenReturn(Val.errorMono("test"));
        StepBuilder.newBuilderAtWhenStep(document, new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).when(AUTHZ_SUB).expectNotApplicable().verify();

    }

    @Test
    void test_matchEmpty() {
        var document = Mockito.mock(SAPL.class);
        Mockito.when(document.matches()).thenReturn(Mono.empty());
        StepBuilder.newBuilderAtWhenStep(document, new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).when(AUTHZ_SUB).expectNotApplicable().verify();
    }

    @Test
    void test_matchVirtualTime() {
        var document = PARSER.parse("policy \"test\" permit");
        StepBuilder.newBuilderAtGivenStep(document, new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).withVirtualTime().when(AUTHZ_SUB).expectPermit().verify();
    }

    @Test
    void test_match_with_attribute() {
        var document = PARSER.parse("policy \"test\" permit where |<foo.bar> == \"fizz\";");
        StepBuilder
                .newBuilderAtGivenStep(document, new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                        new HashMap<>())
                .givenAttribute("foo.bar", Val.of("fizz"), Val.of("buzz")).when(AUTHZ_SUB).expectPermit().verify();
    }

    @Test
    void test_match() {
        var document = PARSER.parse("policy \"test\" permit");
        StepBuilder.newBuilderAtWhenStep(document, new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).when(AUTHZ_SUB).expectPermit().verify();
    }

}
