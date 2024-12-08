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
import static io.sapl.api.pdp.AuthorizationDecision.PERMIT;
import static io.sapl.testutil.TestUtil.hasDecision;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.testutil.MockUtil;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SAPLImplCustomTests {

    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static Stream<Arguments> provideImportTestCases() {
        // @formatter:off
		return Stream.of(
				// importsWorkCorrectlyBasicFunction
			    Arguments.of("import filter.blacken policy \"policy\" permit true",
			    		Map.of("blacken", "filter.blacken")),

				// importsWorkCorrectlyWildcardFunction
			    Arguments.of("import filter.* policy \"policy\" permit true",
			    		Map.of("blacken", "filter.blacken", "replace", "filter.replace", "remove", "filter.remove")),

				// importsWorkCorrectlyLibraryFunction
			    Arguments.of("import filter as fil policy \"policy\" permit true",
			    		Map.of("fil.blacken", "filter.blacken", "fil.replace", "filter.replace", "fil.remove", "filter.remove")),

				// importsWorkCorrectlyBasicAttribute
			    Arguments.of("import test.numbers policy \"policy\" permit true",
			    		Map.of("numbers", "test.numbers")),

				// importsWorkCorrectlyWildcardAttribute
			    Arguments.of("import test.* policy \"policy\" permit true",
			    		Map.of("numbers", "test.numbers", "numbersWithError", "test.numbersWithError", "nilflux", "test.nilflux")),

				// importsWorkCorrectlyLibraryAttribute
			    Arguments.of("import test as t policy \"policy\" permit true",
			    		Map.of("t.numbers", "test.numbers", "t.numbersWithError", "test.numbersWithError", "t.nilflux", "test.nilflux"))
		);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideImportTestCases")
    void importsWorkAsExpected(String policySource, Map<String, String> expectedImports) {
        final var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.evaluate()
                .flatMap(val -> Mono.deferContextual(
                        ctx -> Mono.just(AuthorizationContext.getImports(ctx).equals(expectedImports))))
                .contextWrite(MockUtil::setUpAuthorizationContext)).expectNext(Boolean.FALSE).verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // detectErrorInTargetMatches
            "policy \"policy\" permit (10/0)",
            // detectErrorInImportsDuringMatches
            "import filter.blacken import filter.blacken policy \"policy\" permit true" })
    void policyElementEvaluatesToError(String policySource) {
        final var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.matches().contextWrite(MockUtil::setUpAuthorizationContext))
                .expectNextMatches(Val::isError).verifyComplete();
    }

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
				// detectErrorInImportsDuringEvaluate
			    Arguments.of("import filter.blacken import filter.blacken policy \"policy\" permit true", INDETERMINATE),

				// importNonExistingFails
			    Arguments.of("import test.nonExisting policy \"policy\" permit true", INDETERMINATE),

				// doubleImportWildcardFails
			    Arguments.of("import test.* import test.* policy \"policy\" permit true", INDETERMINATE),

				// doubleImportLibraryFails
			    Arguments.of("import test as t import test as t policy \"policy\" permit true",INDETERMINATE),

				// policyBodyEvaluationDoesNotCheckTargetAgain
			    Arguments.of("policy \"policy\" permit (10/0)", PERMIT)
		);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void documentEvaluatesToExpectedValue(String policySource, AuthorizationDecision expected) {
        final var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.evaluate().contextWrite(MockUtil::setUpAuthorizationContext))
                .expectNextMatches(hasDecision(expected)).verifyComplete();
    }
}
