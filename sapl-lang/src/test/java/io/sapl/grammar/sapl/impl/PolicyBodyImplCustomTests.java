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
package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.AuthorizationDecision.INDETERMINATE;
import static io.sapl.api.pdp.AuthorizationDecision.NOT_APPLICABLE;
import static io.sapl.api.pdp.AuthorizationDecision.PERMIT;
import static io.sapl.testutil.TestUtil.hasDecision;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.testutil.MockUtil;
import reactor.test.StepVerifier;

class PolicyBodyImplCustomTests {

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
	 			// trueReturnsEntitlement
	 			Arguments.of("policy \"p\" permit true where true; true; true;", PERMIT),

	 			// oneFalseReturnsNotApplicableEntitlement
	 			Arguments.of("policy \"p\" permit true where true; false; true;",NOT_APPLICABLE),

	 			// oneErrorReturnsIndeterminate
	 			Arguments.of("policy \"p\" permit true where true; (10/0); true;",INDETERMINATE),

	 			// valueDefinitionsEvaluateAndScope
	 			Arguments.of("policy \"p\" permit true where variable == undefined; var variable = 1; variable == 1;", PERMIT),

	 			// valueDefinitionsDefineUndefined
	 			Arguments.of("policy \"p\" permit true where variable == undefined; var variable = undefined; variable == undefined;", PERMIT),

	 			// valueDefinitionsDefineError
	 			Arguments.of("policy \"p\" permit where var variable = (10/0);", INDETERMINATE),

	 			// lazyStatementEvaluationVarDef
	 			Arguments.of("policy \"p\" permit true where false; var variable = (10/0);", NOT_APPLICABLE),

	 			// lazyStatementEvaluationVarDefOnError
	 			Arguments.of("policy \"p\" permit true where (10/0); var variable = (10/0);", INDETERMINATE),

	 			// lazyStatementEvaluation
	 			Arguments.of("policy \"p\" permit true where false; (10/0);", NOT_APPLICABLE)
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void documentEvaluatesToExpectedValue(String policySource, AuthorizationDecision expected) {
        var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.evaluate().contextWrite(MockUtil::setUpAuthorizationContext))
                .expectNextMatches(hasDecision(expected)).verifyComplete();
    }
}
