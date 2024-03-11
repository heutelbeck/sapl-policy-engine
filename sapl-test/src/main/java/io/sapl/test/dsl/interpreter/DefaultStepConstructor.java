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

package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.Environment;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.Scenario;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefaultStepConstructor implements StepConstructor {

    private final DefaultTestFixtureConstructor defaultTestFixtureConstructor;
    private final DefaultTestCaseConstructor    defaultTestCaseConstructor;
    private final DefaultWhenStepConstructor    defaultWhenStepConstructor;
    private final DefaultExpectStepConstructor  defaultExpectStepConstructor;
    private final DefaultVerifyStepConstructor  defaultVerifyStepConstructor;

    @Override
    public SaplTestFixture constructTestFixture(final Given given, final List<GivenStep> givenSteps) {
        return defaultTestFixtureConstructor.constructTestFixture(given, givenSteps);
    }

    @Override
    public GivenOrWhenStep constructTestCase(final SaplTestFixture saplTestFixture, final Environment environment,
            final boolean needsMocks) {
        return defaultTestCaseConstructor.constructTestCase(saplTestFixture, environment, needsMocks);
    }

    @Override
    public WhenStep constructWhenStep(final List<GivenStep> givenSteps, final GivenOrWhenStep initialTestCase,
            final Expectation expectation) {
        return defaultWhenStepConstructor.constructWhenStep(givenSteps, initialTestCase, expectation);
    }

    @Override
    public ExpectStep constructExpectStep(final Scenario scenario, final WhenStep whenStep) {
        return defaultExpectStepConstructor.constructExpectStep(scenario, whenStep);
    }

    @Override
    public VerifyStep constructVerifyStep(final Scenario scenario, final ExpectStep expectStep) {
        return defaultVerifyStepConstructor.constructVerifyStep(scenario, expectStep);
    }

    public static StepConstructor of(final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var objectMapper   = new ObjectMapper();
        final var valInterpreter = new ValueInterpreter(objectMapper);

        final var stringMatcherInterpreter   = new StringMatcherInterpreter();
        final var jsonNodeMatcherInterpreter = new JsonNodeMatcherInterpreter(stringMatcherInterpreter);

        final var matcherInterpreter = new ValMatcherInterpreter(valInterpreter, jsonNodeMatcherInterpreter,
                stringMatcherInterpreter);

        final var durationInterpreter                     = new DurationInterpreter();
        final var attributeInterpreter                    = new AttributeInterpreter(valInterpreter, matcherInterpreter,
                durationInterpreter);
        final var multipleAmountInterpreter               = new MultipleInterpreter();
        final var functionInterpreter                     = new FunctionInterpreter(valInterpreter, matcherInterpreter,
                multipleAmountInterpreter);
        final var authorizationDecisionInterpreter        = new AuthorizationDecisionInterpreter(valInterpreter,
                objectMapper);
        final var authorizationSubscriptionInterpreter    = new AuthorizationSubscriptionInterpreter(valInterpreter);
        final var authorizationDecisionMatcherInterpreter = new AuthorizationDecisionMatcherInterpreter(valInterpreter,
                jsonNodeMatcherInterpreter);
        final var expectInterpreter                       = new ExpectationInterpreter(valInterpreter,
                authorizationDecisionInterpreter, authorizationDecisionMatcherInterpreter, durationInterpreter,
                multipleAmountInterpreter);

        final var defaultTestFixtureConstructor = getTestFixtureConstructor(valInterpreter,
                customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);

        final var defaultTestCaseConstructor = new DefaultTestCaseConstructor(valInterpreter);

        final var defaultWhenStepConstructor   = new DefaultWhenStepConstructor(functionInterpreter,
                attributeInterpreter);
        final var defaultExpectStepConstructor = new DefaultExpectStepConstructor(authorizationSubscriptionInterpreter);
        final var defaultVerifyStepConstructor = new DefaultVerifyStepConstructor(expectInterpreter);

        return new DefaultStepConstructor(defaultTestFixtureConstructor, defaultTestCaseConstructor,
                defaultWhenStepConstructor, defaultExpectStepConstructor, defaultVerifyStepConstructor);
    }

    private static DefaultTestFixtureConstructor getTestFixtureConstructor(final ValueInterpreter valueInterpreter,
            final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var combiningAlgorithmInterpreter = new CombiningAlgorithmInterpreter();

        final var documentInterpreter     = new DocumentInterpreter(customUnitTestPolicyResolver,
                customIntegrationTestPolicyResolver);
        final var pdpConfigurationHandler = new PdpConfigurationHandler(valueInterpreter,
                combiningAlgorithmInterpreter);

        final var functionLibraryInterpreter = new FunctionLibraryInterpreter();
        final var reflectionHelper           = new ReflectionHelper();

        return new DefaultTestFixtureConstructor(documentInterpreter, pdpConfigurationHandler,
                functionLibraryInterpreter, reflectionHelper);
    }
}
