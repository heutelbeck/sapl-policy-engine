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
package io.sapl.test.lang;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.Array;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.FalseLiteral;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.NullLiteral;
import io.sapl.test.grammar.sapltest.NumberLiteral;
import io.sapl.test.grammar.sapltest.Pair;
import io.sapl.test.grammar.sapltest.Requirement;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.grammar.sapltest.Scenario;
import io.sapl.test.grammar.sapltest.SingleDocument;
import io.sapl.test.grammar.sapltest.SingleExpect;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.sapltest.TrueLiteral;
import io.sapl.test.grammar.sapltest.UndefinedLiteral;
import io.sapl.test.grammar.sapltest.WhenStep;
import io.sapl.test.utils.ClasspathHelper;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes SAPL test definitions.
 * <p>
 * Each scenario is executed with a fresh {@link SaplTestFixture} instance for
 * proper test isolation.
 */
@UtilityClass
public class SaplTestRunner {

    /**
     * Runs all scenarios in a SAPL test definition.
     *
     * @param test the parsed test AST
     * @return list of results, one per scenario
     */
    public static List<TestResult> run(SAPLTest test) {
        var results = new ArrayList<TestResult>();
        for (var requirement : test.getRequirements()) {
            results.addAll(runRequirement(requirement));
        }
        return results;
    }

    private static List<TestResult> runRequirement(Requirement requirement) {
        var requirementName  = requirement.getName();
        var requirementGiven = requirement.getGiven();
        var results          = new ArrayList<TestResult>();

        for (var scenario : requirement.getScenarios()) {
            results.add(runScenario(requirementName, requirementGiven, scenario));
        }
        return results;
    }

    private static TestResult runScenario(String requirementName, Given requirementGiven, Scenario scenario) {
        var scenarioName = scenario.getName();
        try {
            var fixture = buildFixture(requirementGiven, scenario.getGiven());
            executeScenario(fixture, scenario);
            return TestResult.passed(requirementName, scenarioName);
        } catch (AssertionError e) {
            return TestResult.failed(requirementName, scenarioName, e.getMessage());
        } catch (Exception e) {
            return TestResult.error(requirementName, scenarioName, e);
        }
    }

    private static SaplTestFixture buildFixture(Given requirementGiven, Given scenarioGiven) {
        var fixture = SaplTestFixture.createSingleTest();

        // Apply requirement-level given
        if (requirementGiven != null) {
            applyGiven(fixture, requirementGiven);
        }

        // Apply scenario-level given (overrides requirement)
        if (scenarioGiven != null) {
            applyGiven(fixture, scenarioGiven);
        }

        return fixture;
    }

    private static void applyGiven(SaplTestFixture fixture, Given given) {
        var document = given.getDocument();
        if (document != null) {
            applyDocument(fixture, document);
        }

        // TODO: Apply pdpVariables, pdpCombiningAlgorithm, environment, givenSteps
    }

    private static void applyDocument(SaplTestFixture fixture, Document document) {
        if (document instanceof SingleDocument singleDoc) {
            var identifier    = singleDoc.getIdentifier();
            var policyContent = ClasspathHelper.readPolicyFromClasspath(identifier);
            fixture.withPolicy(policyContent);
        }
        // TODO: Handle DocumentSetWithSingleIdentifier, DocumentSet
    }

    private static void executeScenario(SaplTestFixture fixture, Scenario scenario) {
        var whenStep     = scenario.getWhenStep();
        var subscription = buildSubscription(whenStep);

        var expectation = scenario.getExpectation();
        if (expectation instanceof SingleExpect singleExpect) {
            var expectedDecision = singleExpect.getDecision();
            var result           = fixture.whenDecide(subscription);

            switch (expectedDecision.getDecision()) {
            case PERMIT         -> result.expectPermit().verify();
            case DENY           -> result.expectDeny().verify();
            case INDETERMINATE  -> result.expectIndeterminate().verify();
            case NOT_APPLICABLE -> result.expectNotApplicable().verify();
            }
        }
        // TODO: Handle SingleExpectWithMatcher, RepeatedExpect
    }

    private static AuthorizationSubscription buildSubscription(WhenStep whenStep) {
        var astSubscription = whenStep.getAuthorizationSubscription();

        var subject  = toValue(astSubscription.getSubject());
        var action   = toValue(astSubscription.getAction());
        var resource = toValue(astSubscription.getResource());

        var environment = astSubscription.getEnvironment();
        if (environment != null) {
            return AuthorizationSubscription.of(subject, action, resource, toValue(environment));
        }
        return AuthorizationSubscription.of(subject, action, resource);
    }

    /**
     * Converts an AST Value node to a runtime Value.
     */
    static Value toValue(io.sapl.test.grammar.sapltest.Value astValue) {
        return switch (astValue) {
        case StringLiteral s                        -> Value.of(s.getString());
        case NumberLiteral n                        -> Value.of(n.getNumber());
        case TrueLiteral ignored                    -> Value.TRUE;
        case FalseLiteral ignored                   -> Value.FALSE;
        case NullLiteral ignored                    -> Value.NULL;
        case UndefinedLiteral ignored               -> Value.UNDEFINED;
        case Array a                                -> toArrayValue(a);
        case io.sapl.test.grammar.sapltest.Object o -> toObjectValue(o);
        default                                     ->
            throw new IllegalArgumentException("Unsupported AST value type: " + astValue.getClass());
        };
    }

    private static Value toArrayValue(Array array) {
        var items = array.getItems();
        if (items == null || items.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }
        var values = items.stream().map(SaplTestRunner::toValue).toArray(Value[]::new);
        return Value.ofArray(values);
    }

    private static Value toObjectValue(io.sapl.test.grammar.sapltest.Object object) {
        var members = object.getMembers();
        if (members == null || members.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        var properties = new java.util.HashMap<String, Value>();
        for (Pair pair : members) {
            properties.put(pair.getKey(), toValue(pair.getValue()));
        }
        return Value.ofObject(properties);
    }
}
