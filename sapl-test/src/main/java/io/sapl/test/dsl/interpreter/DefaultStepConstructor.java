/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefaultStepConstructor implements StepConstructor {

    private final DefaultTestFixtureConstructor defaultTestFixtureConstructor;
    private final DefaultWhenStepConstructor    whenStepBuilder;
    private final DefaultExpectStepConstructor  expectStepBuilder;
    private final DefaultVerifyStepConstructor  verifyStepBuilder;

    @Override
    public GivenOrWhenStep buildTestFixture(final List<FixtureRegistration> fixtureRegistrations,
            final TestSuite testSuite, final io.sapl.test.grammar.sAPLTest.Object environment,
            final boolean needsMocks) {
        return defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuite, environment, needsMocks);
    }

    @Override
    public WhenStep constructWhenStep(final List<GivenStep> givenSteps, GivenOrWhenStep givenOrWhenStep) {
        return whenStepBuilder.constructWhenStep(givenSteps, givenOrWhenStep);
    }

    @Override
    public ExpectStep constructExpectStep(final TestCase testCase, final WhenStep whenStep) {
        return expectStepBuilder.constructExpectStep(testCase, whenStep);
    }

    @Override
    public VerifyStep constructVerifyStep(final TestCase testCase, final ExpectOrVerifyStep expectOrVerifyStep) {
        return verifyStepBuilder.constructVerifyStep(testCase, expectOrVerifyStep);
    }

    public static StepConstructor of(final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var objectMapper   = new ObjectMapper();
        final var valInterpreter = new ValInterpreter(objectMapper);

        final var stringMatcherInterpreter   = new StringMatcherInterpreter();
        final var jsonNodeMatcherInterpreter = new JsonNodeMatcherInterpreter(stringMatcherInterpreter);

        final var matcherInterpreter = new ValMatcherInterpreter(valInterpreter, jsonNodeMatcherInterpreter,
                stringMatcherInterpreter);

        final var durationInterpreter                     = new DurationInterpreter();
        final var attributeInterpreter                    = new AttributeInterpreter(valInterpreter, matcherInterpreter,
                durationInterpreter);
        final var multipleAmountInterpreter               = new MultipleAmountInterpreter();
        final var functionInterpreter                     = new FunctionInterpreter(valInterpreter, matcherInterpreter,
                multipleAmountInterpreter);
        final var authorizationDecisionInterpreter        = new AuthorizationDecisionInterpreter(valInterpreter,
                objectMapper);
        final var authorizationSubscriptionInterpreter    = new AuthorizationSubscriptionInterpreter(valInterpreter);
        final var authorizationDecisionMatcherInterpreter = new AuthorizationDecisionMatcherInterpreter(valInterpreter,
                jsonNodeMatcherInterpreter);
        final var expectInterpreter                       = new ExpectInterpreter(valInterpreter,
                authorizationDecisionInterpreter, authorizationDecisionMatcherInterpreter, durationInterpreter,
                multipleAmountInterpreter);

        final var defaultTestFixtureConstructor = getTestFixtureConstructor(valInterpreter,
                customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
        final var whenStepBuilder               = new DefaultWhenStepConstructor(functionInterpreter,
                attributeInterpreter);
        final var expectStepBuilder             = new DefaultExpectStepConstructor(
                authorizationSubscriptionInterpreter);
        final var verifyStepBuilder             = new DefaultVerifyStepConstructor(expectInterpreter);

        return new DefaultStepConstructor(defaultTestFixtureConstructor, whenStepBuilder, expectStepBuilder,
                verifyStepBuilder);
    }

    private static DefaultTestFixtureConstructor getTestFixtureConstructor(final ValInterpreter valInterpreter,
            final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();

        final var testSuiteInterpreter = new TestSuiteInterpreter(valInterpreter, pdpCombiningAlgorithmInterpreter,
                customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);

        final var functionLibraryInterpreter = new FunctionLibraryInterpreter();
        final var reflectionHelper           = new ReflectionHelper();

        return new DefaultTestFixtureConstructor(testSuiteInterpreter, functionLibraryInterpreter, reflectionHelper);
    }
}
