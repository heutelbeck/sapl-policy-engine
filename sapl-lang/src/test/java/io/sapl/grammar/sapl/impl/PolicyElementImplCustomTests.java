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
package io.sapl.grammar.sapl.impl;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.testutil.MockUtil;
import reactor.test.StepVerifier;

class PolicyElementImplCustomTests {

    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
	 			// emptyTargetMatches
	 			Arguments.of("policy \"p\" permit", Val.TRUE),
	 			// falseTargetDosNotMatch
	 			Arguments.of("policy \"p\" permit false", Val.FALSE),
	 			// trueTargetDosMatch
	 			Arguments.of("policy \"p\" permit true", Val.TRUE)
			);
		// @formatter:on
    }

    @MethodSource("provideTestCases")
    void policyElementEvaluatesToExpectedValue(String policySource, Val expected) {
        final var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.matches().contextWrite(MockUtil::setUpAuthorizationContext)).expectNext(expected)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // undefinedTargetErrors
            "policy \"p\" permit undefined",
            // errorTargetErrors
            "policy \"p\" permit (10/0)",
            // nonBooleanTargetErrors
            "policy \"p\" permit \"abc\"" })
    void policyElementEvaluatesToError(String policySource) {
        final var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.matches().contextWrite(MockUtil::setUpAuthorizationContext))
                .expectNextMatches(Val::isError).verifyComplete();
    }
}
