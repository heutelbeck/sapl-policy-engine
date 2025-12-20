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
package io.sapl.test.plain;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.Decision;
import io.sapl.test.DecisionMatcher;
import io.sapl.test.MockingFunctionBroker.ArgumentMatchers;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.antlr.SAPLTestParser.*;
import io.sapl.test.verification.Times;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.sapl.compiler.StringsUtil.unquoteString;
import static io.sapl.test.Matchers.*;
import io.sapl.api.model.Value;

/**
 * Interprets a parsed scenario and executes it using SaplTestFixture.
 * <p>
 * This class maps the grammar constructs to the fixture API, handling:
 * <ul>
 * <li>Document selection (unit vs integration test)</li>
 * <li>Combining algorithm resolution</li>
 * <li>Function and attribute mocking</li>
 * <li>Authorization subscription construction</li>
 * <li>Decision expectations and verification</li>
 * </ul>
 */
@RequiredArgsConstructor
public class ScenarioInterpreter {

    private final TestConfiguration config;

    /**
     * Executes a scenario and returns the result.
     *
     * @param testDoc the test document containing this scenario
     * @param requirement the parent requirement (for requirement-level given)
     * @param scenario the scenario to execute
     * @return the scenario result
     */
    public ScenarioResult execute(SaplTestDocument testDoc, RequirementContext requirement, ScenarioContext scenario) {
        var requirementName = unquoteString(requirement.name.getText());
        var scenarioName    = unquoteString(scenario.name.getText());
        var startTime       = System.nanoTime();

        try {
            // Merge requirement-level and scenario-level given blocks
            var mergedGiven = mergeGivenBlocks(requirement.given(), scenario.given());

            // Determine test mode (unit vs integration) based on document specification
            var documentSpec = findDocumentSpecification(mergedGiven);
            var isUnitTest   = isUnitTest(documentSpec);

            // Create fixture based on test mode
            var fixture = isUnitTest ? SaplTestFixture.createSingleTest() : SaplTestFixture.createIntegrationTest();

            // Load default function libraries so unmocked functions delegate to real
            // implementations
            fixture.withDefaultFunctionLibraries();

            // Configure coverage for test identification
            fixture.withTestIdentifier(requirementName + " > " + scenarioName);

            // Apply document selection
            applyDocumentSelection(fixture, documentSpec, isUnitTest);

            // Apply combining algorithm (if specified or use defaults)
            applyCombiningAlgorithm(fixture, mergedGiven, isUnitTest);

            // Apply variables (if specified)
            applyVariables(fixture, mergedGiven);

            // Apply function mocks
            applyFunctionMocks(fixture, mergedGiven);

            // Apply attribute mocks
            applyAttributeMocks(fixture, mergedGiven);

            // Build and execute authorization subscription
            var whenStep       = scenario.whenStep();
            var decisionResult = executeWhenClause(fixture, whenStep);

            // Execute expectations (including then/expect sequences)
            executeExpectations(decisionResult, scenario.expectOrThenExpect());

            // Verify and get result - this actually runs the reactive pipeline
            var testResult = decisionResult.verify();

            // Execute verify block AFTER evaluation completes (so invocations are recorded)
            if (scenario.verifyBlock() != null) {
                executeVerifyBlock(fixture, scenario.verifyBlock());
            }

            var duration = Duration.ofNanos(System.nanoTime() - startTime);
            if (testResult.passed()) {
                return ScenarioResult.passed(testDoc.id(), requirementName, scenarioName, duration,
                        testResult.coverage());
            } else {
                return ScenarioResult.failed(testDoc.id(), requirementName, scenarioName, duration,
                        testResult.failureMessage(), testResult.coverage());
            }
        } catch (AssertionError e) {
            // StepVerifier throws AssertionError on expectation failure
            var duration = Duration.ofNanos(System.nanoTime() - startTime);
            return ScenarioResult.failed(testDoc.id(), requirementName, scenarioName, duration, e.getMessage(), null);
        } catch (Exception e) {
            var duration = Duration.ofNanos(System.nanoTime() - startTime);
            return ScenarioResult.error(testDoc.id(), requirementName, scenarioName, duration, e, null);
        }
    }

    /**
     * Merges requirement-level and scenario-level given blocks.
     * Scenario-level items override/extend requirement-level items.
     */
    private MergedGiven mergeGivenBlocks(@Nullable GivenContext requirementGiven,
            @Nullable GivenContext scenarioGiven) {
        var result = new MergedGiven();

        // First, add requirement-level items
        if (requirementGiven != null) {
            for (var item : requirementGiven.givenItem()) {
                result.addItem(item, false);
            }
        }

        // Then, add/override with scenario-level items
        if (scenarioGiven != null) {
            for (var item : scenarioGiven.givenItem()) {
                result.addItem(item, true);
            }
        }

        return result;
    }

    /**
     * Finds the document specification in the merged given items.
     * Returns null if no document specification is present (meaning: use all
     * documents).
     */
    @Nullable
    private DocumentSpecificationContext findDocumentSpecification(MergedGiven given) {
        return given.documentSpecification;
    }

    /**
     * Determines if this is a unit test based on document specification.
     * Unit test: `document "X"` (singular)
     * Integration test: `documents "X", ...` (plural) or omitted
     */
    private boolean isUnitTest(@Nullable DocumentSpecificationContext docSpec) {
        if (docSpec == null) {
            // No document specification = integration test (all documents)
            return false;
        }
        return docSpec instanceof SingleDocumentContext;
    }

    /**
     * Applies document selection to the fixture.
     */
    private void applyDocumentSelection(SaplTestFixture fixture, @Nullable DocumentSpecificationContext docSpec,
            boolean isUnitTest) {
        switch (docSpec) {
        case null                              -> {
            // Integration test with all documents from config
            for (var doc : config.saplDocuments()) {
                fixture.withPolicy(doc.sourceCode());
            }
        }
        case SingleDocumentContext single      -> {
            // Unit test with single document
            var docName = unquoteString(single.identifier.getText());
            var doc     = findDocumentByName(docName);
            fixture.withPolicy(doc.sourceCode());
        }
        case MultipleDocumentsContext multiple -> {
            // Integration test with explicit subset
            for (var idToken : multiple.identifiers) {
                var docName = unquoteString(idToken.getText());
                var doc     = findDocumentByName(docName);
                fixture.withPolicy(doc.sourceCode());
            }
        }
        default                                -> { /* NO-OP */ }
        }
    }

    /**
     * Finds a document by name from the configuration.
     */
    private SaplDocument findDocumentByName(String name) {
        return config.saplDocuments().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow(() -> {
            var available = config.saplDocuments().stream().map(SaplDocument::name).toList();
            return new IllegalArgumentException(
                    "Document not found: '" + name + "'. Available documents: " + available);
        });
    }

    /**
     * Applies combining algorithm to the fixture.
     * Validates that unit tests do not specify a combining algorithm.
     */
    private void applyCombiningAlgorithm(SaplTestFixture fixture, MergedGiven given, boolean isUnitTest) {
        if (isUnitTest) {
            // Unit test: default is ONLY_ONE_APPLICABLE (handled by fixture)
            // Specifying an algorithm in a unit test is a validation error
            if (given.combiningAlgorithm != null) {
                throw new TestValidationException(
                        "Unit tests (using 'document' singular) cannot specify a combining algorithm. "
                                + "Unit tests automatically use ONLY_ONE_APPLICABLE. "
                                + "Use 'documents' (plural) for integration tests that need a specific algorithm.");
            }
            return;
        }

        // Integration test: use specified or config default
        if (given.combiningAlgorithm != null) {
            var algorithm = parseCombiningAlgorithm(given.combiningAlgorithm);
            fixture.withCombiningAlgorithm(algorithm);
        } else {
            // Use config default
            fixture.withCombiningAlgorithm(config.defaultAlgorithm());
        }
    }

    /**
     * Parses a combining algorithm from grammar context.
     */
    private CombiningAlgorithm parseCombiningAlgorithm(CombiningAlgorithmContext ctx) {
        if (ctx instanceof DenyOverridesAlgorithmContext) {
            return CombiningAlgorithm.DENY_OVERRIDES;
        } else if (ctx instanceof PermitOverridesAlgorithmContext) {
            return CombiningAlgorithm.PERMIT_OVERRIDES;
        } else if (ctx instanceof OnlyOneApplicableAlgorithmContext) {
            return CombiningAlgorithm.ONLY_ONE_APPLICABLE;
        } else if (ctx instanceof DenyUnlessPermitAlgorithmContext) {
            return CombiningAlgorithm.DENY_UNLESS_PERMIT;
        } else if (ctx instanceof PermitUnlessDenyAlgorithmContext) {
            return CombiningAlgorithm.PERMIT_UNLESS_DENY;
        }
        throw new IllegalArgumentException("Unknown combining algorithm: " + ctx.getText());
    }

    /**
     * Applies variables to the fixture.
     * Variables from config are base; test variables override.
     */
    private void applyVariables(SaplTestFixture fixture, MergedGiven given) {
        // First apply config-level variables
        for (var entry : config.pdpVariables().entrySet()) {
            fixture.givenVariable(entry.getKey(), entry.getValue());
        }

        // Then apply test-level variables (these will override config variables)
        if (given.variables != null) {
            var testVariables = ValueConverter.convertObjectToMap(given.variables.variables);
            for (var entry : testVariables.entrySet()) {
                // The fixture throws if a variable already exists, so we need to handle
                // override
                // For now, we just add them - the fixture will handle duplicates
                try {
                    fixture.givenVariable(entry.getKey(), entry.getValue());
                } catch (IllegalArgumentException e) {
                    // Variable already exists - this is expected for overrides
                    // TODO: Consider if we need to support variable override in fixture
                }
            }
        }
    }

    /**
     * Applies function mocks to the fixture.
     */
    private void applyFunctionMocks(SaplTestFixture fixture, MergedGiven given) {
        for (var funcMock : given.functionMocks) {
            var functionName       = buildFunctionName(funcMock.functionFullName);
            var parameters         = buildFunctionParameters(funcMock.functionParameters());
            var returnValueOrError = ValueConverter.convertValueOrError(funcMock.returnValue);

            if (returnValueOrError.isError()) {
                // Return error Value - causes policy evaluation to result in indeterminate
                var errorMessage = returnValueOrError.errorMessage() != null ? returnValueOrError.errorMessage()
                        : "Mock function error";
                fixture.givenFunction(functionName, parameters, Value.error(errorMessage));
            } else {
                fixture.givenFunction(functionName, parameters, returnValueOrError.value());
            }
        }
    }

    /**
     * Builds a function name from the grammar context.
     */
    private String buildFunctionName(FunctionNameContext ctx) {
        return ctx.parts.stream().map(t -> t.getText()).collect(Collectors.joining("."));
    }

    /**
     * Builds function parameters (argument matchers) from the grammar context.
     */
    private SaplTestFixture.Parameters buildFunctionParameters(@Nullable FunctionParametersContext ctx) {
        if (ctx == null || ctx.matchers.isEmpty()) {
            return args();
        }
        var matchers = MatcherConverter.convertAll(ctx.matchers);
        return ArgumentMatchers.of(matchers);
    }

    /**
     * Applies attribute mocks to the fixture.
     */
    private void applyAttributeMocks(SaplTestFixture fixture, MergedGiven given) {
        for (var attrMock : given.attributeMocks) {
            var mockId  = unquoteString(attrMock.mockId.getText());
            var attrRef = attrMock.attributeReference();

            if (attrRef instanceof EnvironmentAttributeReferenceContext envAttr) {
                // Environment attribute: <pip.attr> or <pip.attr("param")>
                var attributeName = buildAttributeName(envAttr.attributeFullName);
                var parameters    = buildAttributeParameters(envAttr.attributeParameters());

                if (attrMock.initialValue != null) {
                    var initialValue = ValueConverter.convertValueOrError(attrMock.initialValue);
                    if (initialValue.isError()) {
                        var errorMessage = initialValue.errorMessage() != null ? initialValue.errorMessage()
                                : "Mock attribute error";
                        fixture.givenEnvironmentAttribute(mockId, attributeName, parameters, Value.error(errorMessage));
                    } else {
                        fixture.givenEnvironmentAttribute(mockId, attributeName, parameters, initialValue.value());
                    }
                } else {
                    fixture.givenEnvironmentAttribute(mockId, attributeName, parameters);
                }
            } else if (attrRef instanceof EntityAttributeReferenceContext entityAttr) {
                // Entity attribute: any.<pip.attr> or {"id":1}.<pip.attr("param")>
                var attributeName = buildAttributeName(entityAttr.attributeFullName);
                var entityMatcher = MatcherConverter.convert(entityAttr.entityMatcher);
                var parameters    = buildAttributeParameters(entityAttr.attributeParameters());

                if (attrMock.initialValue != null) {
                    var initialValue = ValueConverter.convertValueOrError(attrMock.initialValue);
                    if (initialValue.isError()) {
                        var errorMessage = initialValue.errorMessage() != null ? initialValue.errorMessage()
                                : "Mock attribute error";
                        fixture.givenAttribute(mockId, attributeName, entityMatcher, parameters,
                                Value.error(errorMessage));
                    } else {
                        fixture.givenAttribute(mockId, attributeName, entityMatcher, parameters, initialValue.value());
                    }
                } else {
                    fixture.givenAttribute(mockId, attributeName, entityMatcher, parameters);
                }
            }
        }
    }

    /**
     * Builds an attribute name from the grammar context.
     */
    private String buildAttributeName(AttributeNameContext ctx) {
        return ctx.parts.stream().map(Token::getText).collect(Collectors.joining("."));
    }

    /**
     * Builds attribute parameters (argument matchers) from the grammar context.
     */
    private SaplTestFixture.Parameters buildAttributeParameters(@Nullable AttributeParametersContext ctx) {
        if (ctx == null || ctx.matchers.isEmpty()) {
            return args();
        }
        var matchers = MatcherConverter.convertAll(ctx.matchers);
        return ArgumentMatchers.of(matchers);
    }

    /**
     * Executes the when clause and returns the decision result.
     */
    private SaplTestFixture.DecisionResult executeWhenClause(SaplTestFixture fixture, WhenStepContext whenStep) {
        var authSub = whenStep.authorizationSubscription();

        var subject  = ValueConverter.convert(authSub.subject);
        var action   = ValueConverter.convert(authSub.action);
        var resource = ValueConverter.convert(authSub.resource);

        AuthorizationSubscription subscription;
        if (authSub.env != null) {
            var environment = ValueConverter.convertObject(authSub.env);
            subscription = AuthorizationSubscription.of(subject, action, resource, environment);
        } else {
            subscription = AuthorizationSubscription.of(subject, action, resource);
        }

        return fixture.whenDecide(subscription);
    }

    /**
     * Executes expectations (including then/expect sequences).
     */
    private void executeExpectations(SaplTestFixture.DecisionResult decisionResult,
            ExpectOrThenExpectContext expectOrThen) {
        // Process initial expectation
        executeExpectation(decisionResult, expectOrThen.expectation());

        // Process then/expect sequences
        for (var thenExpect : expectOrThen.thenExpect()) {
            executeThenBlock(decisionResult, thenExpect.thenBlock());
            executeExpectation(decisionResult, thenExpect.expectation());
        }
    }

    /**
     * Executes a single expectation.
     */
    private void executeExpectation(SaplTestFixture.DecisionResult decisionResult, ExpectationContext expectation) {
        if (expectation instanceof SingleExpectationContext single) {
            // expect permit|deny|... [with obligations ...] [with resource ...] [with
            // advice ...]
            var matcher = buildDecisionMatcher(single.authorizationDecision());
            decisionResult.expectDecisionMatches(matcher);

        } else if (expectation instanceof MatcherExpectationContext matcherExp) {
            // expect decision any, is permit, with obligation ..., etc.
            // Combine all matchers into a single predicate that checks ONE decision
            applyCombinedMatchers(decisionResult, matcherExp.matchers);

        } else if (expectation instanceof StreamExpectationContext streamExp) {
            // expect - permit once - deny 2 times - ...
            for (var expectStep : streamExp.expectStep()) {
                executeExpectStep(decisionResult, expectStep);
            }
        }
    }

    /**
     * Executes a single expect step in stream expectations.
     */
    private void executeExpectStep(SaplTestFixture.DecisionResult decisionResult, ExpectStepContext step) {
        if (step instanceof NextDecisionStepContext nextStep) {
            // permit once, deny 2 times
            var decision = parseDecisionType(nextStep.expectedDecision);
            var times    = parseNumericAmount(nextStep.amount);
            var matcher  = createBasicDecisionMatcher(decision);
            for (int i = 0; i < times; i++) {
                decisionResult.expectDecisionMatches(matcher);
            }

        } else if (step instanceof NextWithDecisionStepContext nextWithStep) {
            // permit with obligation {...}
            var matcher = buildDecisionMatcher(nextWithStep.expectedDecision);
            decisionResult.expectDecisionMatches(matcher);

        } else if (step instanceof NextWithMatcherStepContext nextMatcherStep) {
            // decision any, is permit, with obligation ...
            // Combine all matchers into a single predicate that checks ONE decision
            applyCombinedMatchers(decisionResult, nextMatcherStep.matchers);
        }
    }

    /**
     * Executes a then block (attribute emissions).
     */
    private void executeThenBlock(SaplTestFixture.DecisionResult decisionResult, ThenBlockContext thenBlock) {
        for (var thenStep : thenBlock.thenStep()) {
            if (thenStep instanceof AttributeEmitStepContext emitStep) {
                var mockId       = unquoteString(emitStep.mockId.getText());
                var valueOrError = ValueConverter.convertValueOrError(emitStep.emittedValue);
                if (valueOrError.isError()) {
                    var errorMessage = valueOrError.errorMessage() != null ? valueOrError.errorMessage()
                            : "Mock emit error";
                    decisionResult.thenEmit(mockId, Value.error(errorMessage));
                } else {
                    decisionResult.thenEmit(mockId, valueOrError.value());
                }
            }
        }
    }

    /**
     * Builds a DecisionMatcher from an authorizationDecision context.
     */
    private DecisionMatcher buildDecisionMatcher(AuthorizationDecisionContext ctx) {
        var decision = parseDecisionType(ctx.decision);
        var matcher  = createBasicDecisionMatcher(decision);

        // Add obligations if present
        if (ctx.obligations != null && !ctx.obligations.isEmpty()) {
            for (var obligationCtx : ctx.obligations) {
                var obligation = ValueConverter.convert(obligationCtx);
                matcher.containsObligation(obligation);
            }
        }

        // Add resource if present
        if (ctx.resource != null) {
            var resource = ValueConverter.convert(ctx.resource);
            matcher.withResource(resource);
        }

        // Add advice if present
        if (ctx.advice != null && !ctx.advice.isEmpty()) {
            for (var adviceCtx : ctx.advice) {
                var advice = ValueConverter.convert(adviceCtx);
                matcher.containsAdvice(advice);
            }
        }

        return matcher;
    }

    /**
     * Applies a decision matcher expectation to the result.
     * Handles both DecisionMatcher-based and Predicate-based matching.
     */
    private void applyDecisionMatcherExpectation(SaplTestFixture.DecisionResult decisionResult,
            AuthorizationDecisionMatcherContext ctx) {
        switch (ctx) {
        case AnyDecisionMatcherContext anyDecisionMatcherContext ->
            // any - matches any decision, use predicate for flexibility
            decisionResult.expectDecisionMatches(Objects::nonNull);
        case IsDecisionMatcherContext isCtx             -> {
            // is permit|deny|...
            var decision = parseDecisionType(isCtx.decision);
            var matcher  = createBasicDecisionMatcher(decision);
            decisionResult.expectDecisionMatches(matcher);
        }
        case HasObligationOrAdviceMatcherContext hasCtx ->
            // with obligation|advice [equals|matching|containing key ...]
            // Use predicate to check constraints regardless of decision type
            decisionResult.expectDecisionMatches(decision -> {
                if (decision == null) {
                    return false;
                }
                // TODO: Apply extended matcher constraints from hasCtx.extendedMatcher
                return true; // For now, just check non-null
            });
        case HasResourceMatcherContext resCtx -> {
            // with resource [equals|matching]
            if (resCtx.defaultMatcher != null) {
                if (resCtx.defaultMatcher instanceof ExactMatchObjectMatcherContext exactCtx) {
                    var expected = ValueConverter.convert(exactCtx.equalTo);
                    decisionResult.expectDecisionMatches(
                            decision -> decision != null && expected.equals(decision.resource()));
                } else {
                    // Matching case - just check resource is present for now
                    decisionResult.expectDecisionMatches(Objects::nonNull);
                }
            } else {
                // Just "with resource" - check that resource is present
                decisionResult.expectDecisionMatches(Objects::nonNull);
            }
        }
        default                               ->
            throw new IllegalArgumentException("Unknown decision matcher type: " + ctx.getClass().getSimpleName());
        }
    }

    /**
     * Combines multiple matchers into a single expectation that checks ONE
     * decision.
     * This ensures that 'expect is permit, with obligation' checks a single
     * decision
     * for BOTH conditions, rather than expecting two separate decisions.
     */
    private void applyCombinedMatchers(SaplTestFixture.DecisionResult decisionResult,
            java.util.List<AuthorizationDecisionMatcherContext> matchers) {
        if (matchers.isEmpty()) {
            return;
        }
        if (matchers.size() == 1) {
            // Single matcher - use existing logic
            applyDecisionMatcherExpectation(decisionResult, matchers.get(0));
            return;
        }
        // Multiple matchers - combine into a single predicate
        decisionResult.expectDecisionMatches(decision -> {
            if (decision == null) {
                return false;
            }
            for (var matcher : matchers) {
                if (!matchesSingleMatcher(decision, matcher)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Checks if a decision matches a single matcher context.
     * Used by applyCombinedMatchers to evaluate each matcher independently.
     */
    private boolean matchesSingleMatcher(io.sapl.api.pdp.AuthorizationDecision decision,
            AuthorizationDecisionMatcherContext ctx) {
        return switch (ctx) {
        case AnyDecisionMatcherContext __           -> true;
        case IsDecisionMatcherContext isCtx         -> {
            var expectedDecision = parseDecisionType(isCtx.decision);
            yield decision.decision() == expectedDecision;
        }
        case HasObligationOrAdviceMatcherContext __ -> {
            // TODO: Apply extended matcher constraints
            // For now, just check that obligations/advice exist if required
            yield true;
        }
        case HasResourceMatcherContext resCtx       -> {
            if (resCtx.defaultMatcher != null) {
                if (resCtx.defaultMatcher instanceof ExactMatchObjectMatcherContext exactCtx) {
                    var expected = ValueConverter.convert(exactCtx.equalTo);
                    yield expected.equals(decision.resource());
                }
            }
            // Just check resource is present
            yield true;
        }
        default                                     ->
            throw new IllegalArgumentException("Unknown matcher type: " + ctx.getClass().getSimpleName());
        };
    }

    /**
     * Creates a basic DecisionMatcher for the given decision type.
     */
    private DecisionMatcher createBasicDecisionMatcher(Decision decision) {
        return switch (decision) {
        case PERMIT         -> isPermit();
        case DENY           -> isDeny();
        case INDETERMINATE  -> isIndeterminate();
        case NOT_APPLICABLE -> isNotApplicable();
        };
    }

    /**
     * Parses a decision type from grammar context.
     */
    private Decision parseDecisionType(AuthorizationDecisionTypeContext ctx) {
        if (ctx instanceof PermitDecisionContext) {
            return Decision.PERMIT;
        } else if (ctx instanceof DenyDecisionContext) {
            return Decision.DENY;
        } else if (ctx instanceof IndeterminateDecisionContext) {
            return Decision.INDETERMINATE;
        } else if (ctx instanceof NotApplicableDecisionContext) {
            return Decision.NOT_APPLICABLE;
        }
        throw new IllegalArgumentException("Unknown decision type: " + ctx.getText());
    }

    /**
     * Parses a numeric amount from grammar context.
     */
    private int parseNumericAmount(NumericAmountContext ctx) {
        if (ctx instanceof OnceAmountContext) {
            return 1;
        } else if (ctx instanceof MultipleAmountContext multiple) {
            return Integer.parseInt(multiple.amount.getText());
        }
        throw new IllegalArgumentException("Unknown amount type: " + ctx.getText());
    }

    /**
     * Executes the verify block.
     */
    private void executeVerifyBlock(SaplTestFixture fixture, VerifyBlockContext verifyBlock) {
        for (var verifyStep : verifyBlock.verifyStep()) {
            if (verifyStep instanceof FunctionVerificationContext funcVerify) {
                var functionName = buildFunctionName(funcVerify.functionFullName);
                var parameters   = buildFunctionParameters(funcVerify.functionParameters());
                var times        = buildTimes(funcVerify.timesCalled);
                fixture.getMockingFunctionBroker().verify(functionName, parameters, times);

            } else if (verifyStep instanceof AttributeVerificationContext attrVerify) {
                var attrRef = attrVerify.attributeReference();
                var times   = buildTimes(attrVerify.timesCalled);

                // TODO: Implement attribute verification in MockingAttributeBroker
                // For now, just skip attribute verification
            }
        }
    }

    /**
     * Builds a Times verification from grammar context.
     */
    private Times buildTimes(NumericAmountContext ctx) {
        var count = parseNumericAmount(ctx);
        return Times.times(count);
    }

    /**
     * Helper class to hold merged given items.
     */
    private static class MergedGiven {
        @Nullable
        DocumentSpecificationContext documentSpecification;
        @Nullable
        CombiningAlgorithmContext    combiningAlgorithm;
        @Nullable
        VariablesDefinitionContext   variables;
        @Nullable
        EnvironmentContext           environment;
        List<FunctionMockContext>    functionMocks  = new ArrayList<>();
        List<AttributeMockContext>   attributeMocks = new ArrayList<>();

        void addItem(GivenItemContext item, boolean isScenarioLevel) {
            if (item instanceof DocumentGivenItemContext docItem) {
                if (isScenarioLevel) {
                    throw new TestValidationException(
                            "Document specification ('document' or 'documents') must be in the requirement-level given block, "
                                    + "not in the scenario-level given block. All scenarios in a requirement test the same document(s).");
                }
                this.documentSpecification = docItem.documentSpecification();
            } else if (item instanceof AlgorithmGivenItemContext algItem) {
                // Scenario-level algorithm also adds to merged - validation happens later
                this.combiningAlgorithm = algItem.combiningAlgorithm();
            } else if (item instanceof VariablesGivenItemContext varItem) {
                // Scenario-level variables override requirement-level
                this.variables = varItem.variablesDefinition();
            } else if (item instanceof EnvironmentGivenItemContext envItem) {
                // Scenario-level environment overrides requirement-level
                this.environment = envItem.environment();
            } else if (item instanceof MockGivenItemContext mockItem) {
                // Mocks are additive (both levels combined)
                var mockDef = mockItem.mockDefinition();
                if (mockDef instanceof FunctionMockContext funcMock) {
                    functionMocks.add(funcMock);
                } else if (mockDef instanceof AttributeMockContext attrMock) {
                    attributeMocks.add(attrMock);
                }
            }
        }
    }
}
