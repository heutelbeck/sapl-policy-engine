/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.test.DecisionMatcher;
import io.sapl.test.MockingFunctionBroker.ArgumentMatchers;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.antlr.SAPLTestParser.*;
import io.sapl.test.verification.Times;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.sapl.compiler.util.StringsUtil.unquoteString;
import static io.sapl.test.Matchers.*;

import io.sapl.api.model.ObjectValue;
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

    private static final String DEFAULT_MOCK_ATTRIBUTE_ERROR = "Mock attribute error";
    private static final String DEFAULT_MOCK_EMIT_ERROR      = "Mock emit error";
    private static final String DEFAULT_MOCK_FUNCTION_ERROR  = "Mock function error";

    private static final String ERROR_DOCUMENT_NOT_FOUND       = "Document not found: '%s'. Available documents: %s.";
    private static final String ERROR_UNKNOWN_AMOUNT_TYPE      = "Unknown amount type: %s.";
    private static final String ERROR_UNKNOWN_DECISION_MATCHER = "Unknown decision matcher type: %s.";
    private static final String ERROR_UNKNOWN_DECISION_TYPE    = "Unknown decision type: %s.";
    private static final String ERROR_UNKNOWN_DEFAULT_DECISION = "Unknown default decision: %s";
    private static final String ERROR_UNKNOWN_ERROR_HANDLING   = "Unknown error handling: %s";
    private static final String ERROR_UNKNOWN_MATCHER_TYPE     = "Unknown matcher type: %s.";
    private static final String ERROR_UNKNOWN_VOTING_MODE      = "Unknown voting mode: %s";
    private static final String ERROR_UNIT_TEST_COMBINING_ALG  = "Unit tests (using 'document' singular) cannot specify a combining algorithm. "
            + "Unit tests automatically use ONLY_ONE_APPLICABLE. "
            + "Use 'documents' (plural) for integration tests that need a specific algorithm.";
    private static final String ERROR_UNIT_TEST_DOC_SPEC       = "Document specification ('document') for unit tests must be in the requirement-level given block, "
            + "not in the scenario-level given block. All scenarios in a requirement test the same document. "
            + "For integration tests with different document combinations per scenario, use 'documents' instead.";

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

            // Apply secrets (if specified)
            applySecrets(fixture, mergedGiven);

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
            // Integration test with all documents from security
            for (var doc : config.saplDocuments()) {
                addDocumentToFixture(fixture, doc);
            }
        }
        case SingleDocumentContext single      -> {
            // Unit test with single document
            var docName = unquoteString(single.identifier.getText());
            var doc     = findDocumentByName(docName);
            addDocumentToFixture(fixture, doc);
        }
        case MultipleDocumentsContext multiple -> {
            // Integration test with explicit subset
            for (var idToken : multiple.identifiers) {
                var docName = unquoteString(idToken.getText());
                var doc     = findDocumentByName(docName);
                addDocumentToFixture(fixture, doc);
            }
        }
        default                                -> { /* NO-OP */ }
        }
    }

    /**
     * Adds a document to the fixture and registers its file path for coverage
     * reporting.
     */
    private void addDocumentToFixture(SaplTestFixture fixture, SaplDocument doc) {
        if (doc.filePath() != null) {
            // Use overloaded method that extracts policy name from source
            fixture.withPolicy(doc.sourceCode(), doc.filePath());
        } else {
            fixture.withPolicy(doc.sourceCode());
        }
    }

    /**
     * Finds a document by name from the configuration.
     */
    private SaplDocument findDocumentByName(String name) {
        return config.saplDocuments().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow(() -> {
            var available = config.saplDocuments().stream().map(SaplDocument::name).toList();
            return new IllegalArgumentException(ERROR_DOCUMENT_NOT_FOUND.formatted(name, available));
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
                throw new TestValidationException(ERROR_UNIT_TEST_COMBINING_ALG);
            }
            return;
        }

        // Integration test: use specified or security default
        if (given.combiningAlgorithm != null) {
            var algorithm = parseCombiningAlgorithm(given.combiningAlgorithm);
            fixture.withCombiningAlgorithm(algorithm);
        } else {
            // Use security default
            fixture.withCombiningAlgorithm(config.defaultAlgorithm());
        }
    }

    /**
     * Parses a combining algorithm from grammar context.
     */
    private CombiningAlgorithm parseCombiningAlgorithm(CombiningAlgorithmContext ctx) {
        var votingMode      = parseVotingMode(ctx.votingMode());
        var defaultDecision = parseDefaultDecision(ctx.defaultDecision());
        var errorHandling   = ctx.errorHandling() != null ? parseErrorHandling(ctx.errorHandling())
                : ErrorHandling.PROPAGATE;
        return new CombiningAlgorithm(votingMode, defaultDecision, errorHandling);
    }

    private VotingMode parseVotingMode(VotingModeContext ctx) {
        return switch (ctx) {
        case FirstContext ignored           -> VotingMode.FIRST;
        case PriorityDenyContext ignored    -> VotingMode.PRIORITY_DENY;
        case PriorityPermitContext ignored  -> VotingMode.PRIORITY_PERMIT;
        case UnanimousStrictContext ignored -> VotingMode.UNANIMOUS_STRICT;
        case UnanimousContext ignored       -> VotingMode.UNANIMOUS;
        case UniqueContext ignored          -> VotingMode.UNIQUE;
        default                             ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_VOTING_MODE.formatted(ctx.getText()));
        };
    }

    private DefaultDecision parseDefaultDecision(DefaultDecisionContext ctx) {
        return switch (ctx) {
        case DenyDefaultContext ignored    -> DefaultDecision.DENY;
        case AbstainDefaultContext ignored -> DefaultDecision.ABSTAIN;
        case PermitDefaultContext ignored  -> DefaultDecision.PERMIT;
        default                            ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_DEFAULT_DECISION.formatted(ctx.getText()));
        };
    }

    private ErrorHandling parseErrorHandling(ErrorHandlingContext ctx) {
        return switch (ctx) {
        case AbstainErrorsContext ignored   -> ErrorHandling.ABSTAIN;
        case PropagateErrorsContext ignored -> ErrorHandling.PROPAGATE;
        default                             ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_ERROR_HANDLING.formatted(ctx.getText()));
        };
    }

    /**
     * Applies variables to the fixture.
     * <p>
     * Variables are applied in order: config-level first, then test-level.
     * Test-level variables override config-level variables with the same name.
     */
    private void applyVariables(SaplTestFixture fixture, MergedGiven given) {
        // First apply config-level variables (from pdp.json or programmatic config)
        for (var entry : config.pdpVariables().entrySet()) {
            fixture.givenVariable(entry.getKey(), entry.getValue());
        }

        // Then apply test-level variables (these override config-level variables)
        if (given.variables != null) {
            var testVariables = ValueConverter.convertObjectToMap(given.variables.variables);
            for (var entry : testVariables.entrySet()) {
                fixture.givenVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Applies secrets to the fixture.
     * <p>
     * Secrets are applied in order: config-level first, then test-level.
     * Test-level secrets override config-level secrets with the same name.
     * <p>
     * Secrets are only accessible to PIPs, not policies. This protects against
     * accidental leakage through misconfigured policies or logging.
     */
    private void applySecrets(SaplTestFixture fixture, MergedGiven given) {
        // First apply config-level secrets (from programmatic config)
        for (var entry : config.pdpSecrets().entrySet()) {
            fixture.givenSecret(entry.getKey(), entry.getValue());
        }

        // Then apply test-level secrets (these override config-level secrets)
        if (given.secrets != null) {
            var testSecrets = ValueConverter.convertObjectToMap(given.secrets.secrets);
            for (var entry : testSecrets.entrySet()) {
                fixture.givenSecret(entry.getKey(), entry.getValue());
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
                        : DEFAULT_MOCK_FUNCTION_ERROR;
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
                applyEnvironmentAttributeMock(fixture, mockId, envAttr, attrMock.initialValue);
            } else if (attrRef instanceof EntityAttributeReferenceContext entityAttr) {
                applyEntityAttributeMock(fixture, mockId, entityAttr, attrMock.initialValue);
            }
        }
    }

    private void applyEnvironmentAttributeMock(SaplTestFixture fixture, String mockId,
            EnvironmentAttributeReferenceContext envAttr, @Nullable ValueOrErrorContext initialValueCtx) {
        var attributeName = buildAttributeName(envAttr.attributeFullName);
        var parameters    = buildAttributeParameters(envAttr.attributeParameters());

        if (initialValueCtx == null) {
            fixture.givenEnvironmentAttribute(mockId, attributeName, parameters);
            return;
        }

        var initialValue = resolveInitialValue(initialValueCtx);
        fixture.givenEnvironmentAttribute(mockId, attributeName, parameters, initialValue);
    }

    private void applyEntityAttributeMock(SaplTestFixture fixture, String mockId,
            EntityAttributeReferenceContext entityAttr, @Nullable ValueOrErrorContext initialValueCtx) {
        var attributeName = buildAttributeName(entityAttr.attributeFullName);
        var entityMatcher = MatcherConverter.convert(entityAttr.entityMatcher);
        var parameters    = buildAttributeParameters(entityAttr.attributeParameters());

        if (initialValueCtx == null) {
            fixture.givenAttribute(mockId, attributeName, entityMatcher, parameters);
            return;
        }

        var initialValue = resolveInitialValue(initialValueCtx);
        fixture.givenAttribute(mockId, attributeName, entityMatcher, parameters, initialValue);
    }

    private Value resolveInitialValue(ValueOrErrorContext ctx) {
        var valueOrError = ValueConverter.convertValueOrError(ctx);
        if (valueOrError.isError()) {
            var errorMessage = valueOrError.errorMessage() != null ? valueOrError.errorMessage()
                    : DEFAULT_MOCK_ATTRIBUTE_ERROR;
            return Value.error(errorMessage);
        }
        return valueOrError.value();
    }

    /**
     * Builds an attribute name from the grammar context.
     */
    private String buildAttributeName(AttributeNameContext ctx) {
        return ctx.parts.stream().map(TestIdContext::getText).collect(Collectors.joining("."));
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

        var subject     = ValueConverter.convert(authSub.subject);
        var action      = ValueConverter.convert(authSub.action);
        var resource    = ValueConverter.convert(authSub.resource);
        var environment = authSub.env != null ? ValueConverter.convertObject(authSub.env) : Value.UNDEFINED;
        var secrets     = authSub.subscriptionSecrets != null
                ? (ObjectValue) ValueConverter.convertObject(authSub.subscriptionSecrets)
                : Value.EMPTY_OBJECT;

        var subscription = new AuthorizationSubscription(subject, action, resource, environment, secrets);

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
        switch (expectation) {
        case SingleExpectationContext single      -> {
            // expect permit|deny|... [with obligations ...] [with resource ...] [with
            // advice ...]
            var matcher = buildDecisionMatcher(single.authorizationDecision());
            decisionResult.expectDecisionMatches(matcher);
        }
        case MatcherExpectationContext matcherExp ->
            // expect decision any, is permit, with obligation ..., etc.
            applyCombinedMatchers(decisionResult, matcherExp.matchers);
        case StreamExpectationContext streamExp -> {
            // expect - permit once - deny 2 times - ...
            for (var expectStep : streamExp.expectStep()) {
                executeExpectStep(decisionResult, expectStep);
            }
        }
        default                                 -> { /* NO-OP */ }
        }
    }

    /**
     * Executes a single expect step in stream expectations.
     */
    private void executeExpectStep(SaplTestFixture.DecisionResult decisionResult, ExpectStepContext step) {
        switch (step) {
        case NextDecisionStepContext nextStep           -> {
            // permit once, deny 2 times
            var decision = parseDecisionType(nextStep.expectedDecision);
            var times    = parseNumericAmount(nextStep.amount);
            var matcher  = createBasicDecisionMatcher(decision);
            for (int i = 0; i < times; i++) {
                decisionResult.expectDecisionMatches(matcher);
            }
        }
        case NextWithDecisionStepContext nextWithStep   -> {
            // permit with obligation {...}
            var matcher = buildDecisionMatcher(nextWithStep.expectedDecision);
            decisionResult.expectDecisionMatches(matcher);
        }
        case NextWithMatcherStepContext nextMatcherStep ->
            // decision any, is permit, with obligation ...
            applyCombinedMatchers(decisionResult, nextMatcherStep.matchers);
        default -> { /* NO-OP */ }
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
                            : DEFAULT_MOCK_EMIT_ERROR;
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
        case HasObligationOrAdviceMatcherContext hasCtx -> {
            var isObligation = "obligation".equalsIgnoreCase(hasCtx.matcherType.getText());
            if (isObligation) {
                decisionResult.expectDecisionMatches(decision -> decision != null
                        && DecisionMatcherHelper.matchesObligationConstraint(decision, hasCtx.extendedMatcher));
            } else {
                decisionResult.expectDecisionMatches(decision -> decision != null
                        && DecisionMatcherHelper.matchesAdviceConstraint(decision, hasCtx.extendedMatcher));
            }
        }
        case HasResourceMatcherContext resCtx           -> {
            // with resource [equals|matching]
            if (resCtx.defaultMatcher != null) {
                decisionResult.expectDecisionMatches(decision -> decision != null
                        && !Value.UNDEFINED.equals(decision.resource())
                        && DecisionMatcherHelper.matchesResourceConstraint(decision.resource(), resCtx.defaultMatcher));
            } else {
                // Just "with resource" - check that resource is present (not UNDEFINED)
                decisionResult.expectDecisionMatches(
                        decision -> decision != null && !Value.UNDEFINED.equals(decision.resource()));
            }
        }
        default                                         -> throw new IllegalArgumentException(
                ERROR_UNKNOWN_DECISION_MATCHER.formatted(ctx.getClass().getSimpleName()));
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
            List<AuthorizationDecisionMatcherContext> matchers) {
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
        case AnyDecisionMatcherContext ignored          -> true;
        case IsDecisionMatcherContext isCtx             -> {
            var expectedDecision = parseDecisionType(isCtx.decision);
            yield decision.decision() == expectedDecision;
        }
        case HasObligationOrAdviceMatcherContext hasCtx -> {
            var isObligation = "obligation".equalsIgnoreCase(hasCtx.matcherType.getText());
            if (isObligation) {
                yield DecisionMatcherHelper.matchesObligationConstraint(decision, hasCtx.extendedMatcher);
            } else {
                yield DecisionMatcherHelper.matchesAdviceConstraint(decision, hasCtx.extendedMatcher);
            }
        }
        case HasResourceMatcherContext resCtx           -> {
            if (Value.UNDEFINED.equals(decision.resource())) {
                yield false; // "with resource" requires resource to be present (not UNDEFINED)
            }
            if (resCtx.defaultMatcher != null) {
                yield DecisionMatcherHelper.matchesResourceConstraint(decision.resource(), resCtx.defaultMatcher);
            }
            // Just "with resource" - resource is present
            yield true;
        }
        default                                         ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_MATCHER_TYPE.formatted(ctx.getClass().getSimpleName()));
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
        return switch (ctx) {
        case PermitDecisionContext ignored        -> Decision.PERMIT;
        case DenyDecisionContext ignored          -> Decision.DENY;
        case IndeterminateDecisionContext ignored -> Decision.INDETERMINATE;
        case NotApplicableDecisionContext ignored -> Decision.NOT_APPLICABLE;
        default                                   ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_DECISION_TYPE.formatted(ctx.getText()));
        };
    }

    /**
     * Parses a numeric amount from grammar context.
     */
    private int parseNumericAmount(NumericAmountContext ctx) {
        return switch (ctx) {
        case OnceAmountContext ignored      -> 1;
        case MultipleAmountContext multiple -> Integer.parseInt(multiple.amount.getText());
        default                             ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_AMOUNT_TYPE.formatted(ctx.getText()));
        };
    }

    /**
     * Executes the verify block.
     */
    private void executeVerifyBlock(SaplTestFixture fixture, VerifyBlockContext verifyBlock) {
        for (var verifyStep : verifyBlock.verifyStep()) {
            switch (verifyStep) {
            case FunctionVerificationContext funcVerify  -> {
                var functionName = buildFunctionName(funcVerify.functionFullName);
                var parameters   = buildFunctionParameters(funcVerify.functionParameters());
                var times        = buildTimes(funcVerify.timesCalled);
                fixture.getMockingFunctionBroker().verify(functionName, parameters, times);
            }
            case AttributeVerificationContext attrVerify -> {
                var attrRef = attrVerify.attributeReference();
                var times   = buildTimes(attrVerify.timesCalled);

                if (attrRef instanceof EnvironmentAttributeReferenceContext envAttr) {
                    var attributeName = buildAttributeName(envAttr.attributeFullName);
                    var parameters    = buildAttributeParameters(envAttr.attributeParameters());
                    fixture.getMockingAttributeBroker().verifyEnvironmentAttribute(attributeName, parameters, times);
                } else if (attrRef instanceof EntityAttributeReferenceContext entityAttr) {
                    var attributeName = buildAttributeName(entityAttr.attributeFullName);
                    var entityMatcher = MatcherConverter.convert(entityAttr.entityMatcher);
                    var parameters    = buildAttributeParameters(entityAttr.attributeParameters());
                    fixture.getMockingAttributeBroker().verifyAttribute(attributeName, entityMatcher, parameters,
                            times);
                }
            }
            default                                      -> { /* NO-OP */ }
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
        SecretsDefinitionContext     secrets;
        List<FunctionMockContext>    functionMocks  = new ArrayList<>();
        List<AttributeMockContext>   attributeMocks = new ArrayList<>();

        void addItem(GivenItemContext item, boolean isScenarioLevel) {
            switch (item) {
            case DocumentGivenItemContext docItem  -> {
                // Only reject single document ('document') at scenario level - unit tests
                // should have
                // document at requirement level. Multiple documents ('documents') are allowed
                // at
                // scenario level for integration tests with different document combinations.
                if (isScenarioLevel && docItem.documentSpecification() instanceof SingleDocumentContext) {
                    throw new TestValidationException(ERROR_UNIT_TEST_DOC_SPEC);
                }
                this.documentSpecification = docItem.documentSpecification();
            }
            case AlgorithmGivenItemContext algItem -> this.combiningAlgorithm = algItem.combiningAlgorithm();
            case VariablesGivenItemContext varItem -> this.variables = varItem.variablesDefinition();
            case SecretsGivenItemContext secItem   -> this.secrets = secItem.secretsDefinition();
            case MockGivenItemContext mockItem     -> addMock(mockItem.mockDefinition());
            default                                -> { /* NO-OP */ }
            }
        }

        private void addMock(MockDefinitionContext mockDef) {
            switch (mockDef) {
            case FunctionMockContext funcMock  -> functionMocks.add(funcMock);
            case AttributeMockContext attrMock -> attributeMocks.add(attrMock);
            default                            -> { /* NO-OP */ }
            }
        }
    }
}
