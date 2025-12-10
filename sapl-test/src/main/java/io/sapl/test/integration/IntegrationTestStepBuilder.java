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
package io.sapl.test.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.test.SaplTestException;
import io.sapl.test.steps.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.hamcrest.Matcher;
import reactor.test.StepVerifier;
import reactor.test.StepVerifier.Step;

import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;

import static io.sapl.test.steps.AuthorizationDecisionMatchers.*;

/**
 * Step builder for integration tests using a full Policy Decision Point.
 */
@UtilityClass
class IntegrationTestStepBuilder {

    /**
     * Create Builder starting at the When-Step for integration tests.
     *
     * @param pdp the Policy Decision Point
     * @param variables the variables map
     * @return WhenStep to start constructing the test case
     */
    static WhenStep newBuilderAtWhenStep(PolicyDecisionPoint pdp, Map<String, Value> variables) {
        return new IntegrationSteps(pdp, variables);
    }

    /**
     * Integration test steps implementation.
     */
    private static class IntegrationSteps implements WhenStep, ExpectStep, ExpectOrVerifyStep {

        private static final String ERROR_NULL_JSON_NODE       = "Error reading the specified JsonNode for your AuthorizationSubscription. It was null";
        private static final String ERROR_EXPECT_NEXT_NEGATIVE = "0 or a negative value is not allowed for the count of expected events";

        private final PolicyDecisionPoint pdp;
        private final Map<String, Value>  variables;
        private final NumberOfExpectSteps numberOfExpectSteps;

        private Step<AuthorizationDecision> steps;

        IntegrationSteps(PolicyDecisionPoint pdp, Map<String, Value> variables) {
            this.pdp                 = pdp;
            this.variables           = variables;
            this.numberOfExpectSteps = new NumberOfExpectSteps();
        }

        @Override
        public ExpectStep when(AuthorizationSubscription authzSub) {
            val decisionFlux = pdp.decide(authzSub);
            this.steps = StepVerifier.create(decisionFlux);
            return this;
        }

        @Override
        public ExpectStep when(String jsonAuthzSub) throws JsonProcessingException {
            var objectMapper     = new ObjectMapper();
            var authzSubJsonNode = objectMapper.readTree(jsonAuthzSub);
            var authzSub         = AuthorizationSubscription.of(
                    ValueJsonMarshaller.fromJsonNode(authzSubJsonNode.findValue("subject")),
                    ValueJsonMarshaller.fromJsonNode(authzSubJsonNode.findValue("action")),
                    ValueJsonMarshaller.fromJsonNode(authzSubJsonNode.findValue("resource")),
                    ValueJsonMarshaller.fromJsonNode(authzSubJsonNode.findValue("environment")));
            return when(authzSub);
        }

        @Override
        public ExpectStep when(JsonNode jsonNode) {
            if (jsonNode == null) {
                throw new SaplTestException(ERROR_NULL_JSON_NODE);
            }
            var authzSub = AuthorizationSubscription.of(ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("subject")),
                    ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("action")),
                    ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("resource")),
                    ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("environment")));
            return when(authzSub);
        }

        @Override
        public VerifyStep expectPermit() {
            return expectNext(isPermit(), "AuthorizationDecision.PERMIT");
        }

        @Override
        public VerifyStep expectDeny() {
            return expectNext(isDeny(), "AuthorizationDecision.DENY");
        }

        @Override
        public VerifyStep expectIndeterminate() {
            return expectNext(isIndeterminate(), "AuthorizationDecision.INDETERMINATE");
        }

        @Override
        public VerifyStep expectNotApplicable() {
            return expectNext(isNotApplicable(), "AuthorizationDecision.NOT_APPLICABLE");
        }

        @Override
        public VerifyStep expect(AuthorizationDecision authDec) {
            return expectNext(authDec);
        }

        @Override
        public ExpectOrVerifyStep expectNext(AuthorizationDecision authDec) {
            this.numberOfExpectSteps.addExpectStep();
            this.steps = this.steps.expectNext(authDec).as(getDebugMessage("equals " + authDec));
            return this;
        }

        @Override
        public VerifyStep expect(Predicate<AuthorizationDecision> pred) {
            return expectNext(pred);
        }

        @Override
        public ExpectOrVerifyStep expectNext(Predicate<AuthorizationDecision> pred) {
            this.numberOfExpectSteps.addExpectStep();
            this.steps = this.steps.expectNextMatches(pred).as(getDebugMessage("predicate evaluating to true"));
            return this;
        }

        @Override
        public VerifyStep expect(Matcher<AuthorizationDecision> matcher) {
            return expectNext(matcher, matcher.toString());
        }

        @Override
        public ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher) {
            return expectNext(matcher, matcher.toString());
        }

        private ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher, String message) {
            this.numberOfExpectSteps.addExpectStep();
            this.steps = this.steps.expectNextMatches(matcher::matches).as(getDebugMessage(message));
            return this;
        }

        @Override
        public ExpectOrVerifyStep expectNextPermit() {
            return expectNextPermit(1);
        }

        @Override
        public ExpectOrVerifyStep expectNextPermit(Integer count) {
            if (count < 1) {
                throw new SaplTestException(ERROR_EXPECT_NEXT_NEGATIVE);
            }
            for (int i = 0; i < count; i++) {
                expectNext(isPermit(), "AuthorizationDecision.PERMIT");
            }
            return this;
        }

        @Override
        public ExpectOrVerifyStep expectNextDeny() {
            return expectNextDeny(1);
        }

        @Override
        public ExpectOrVerifyStep expectNextDeny(Integer count) {
            if (count < 1) {
                throw new SaplTestException(ERROR_EXPECT_NEXT_NEGATIVE);
            }
            for (int i = 0; i < count; i++) {
                expectNext(isDeny(), "AuthorizationDecision.DENY");
            }
            return this;
        }

        @Override
        public ExpectOrVerifyStep expectNextIndeterminate() {
            return expectNextIndeterminate(1);
        }

        @Override
        public ExpectOrVerifyStep expectNextIndeterminate(Integer count) {
            if (count < 1) {
                throw new SaplTestException(ERROR_EXPECT_NEXT_NEGATIVE);
            }
            for (int i = 0; i < count; i++) {
                expectNext(isIndeterminate(), "AuthorizationDecision.INDETERMINATE");
            }
            return this;
        }

        @Override
        public ExpectOrVerifyStep expectNextNotApplicable() {
            return expectNextNotApplicable(1);
        }

        @Override
        public ExpectOrVerifyStep expectNextNotApplicable(Integer count) {
            if (count < 1) {
                throw new SaplTestException(ERROR_EXPECT_NEXT_NEGATIVE);
            }
            for (int i = 0; i < count; i++) {
                expectNext(isNotApplicable(), "AuthorizationDecision.NOT_APPLICABLE");
            }
            return this;
        }

        @Override
        public ExpectOrVerifyStep thenAttribute(String importName, Value returns) {
            throw new SaplTestException(
                    "thenAttribute is not supported in integration tests. Use unit tests for streaming attribute mocking.");
        }

        @Override
        public ExpectOrVerifyStep thenAwait(Duration duration) {
            this.steps = this.steps.thenAwait(duration);
            return this;
        }

        @Override
        public ExpectOrVerifyStep expectNoEvent(Duration duration) {
            this.steps = this.steps.expectNoEvent(duration);
            return this;
        }

        @Override
        public void verify() {
            this.steps.thenCancel().verify(Duration.ofSeconds(10));
        }

        private String getDebugMessage(String endOfMessage) {
            StringBuilder builder = new StringBuilder();
            switch (this.numberOfExpectSteps.getNumberOfExpectSteps()) {
            case 1  -> builder.append("1st");
            case 2  -> builder.append("2nd");
            case 3  -> builder.append("3rd");
            default -> builder.append(this.numberOfExpectSteps.getNumberOfExpectSteps()).append("th");
            }
            builder.append(" expect step failed: Expected ").append(endOfMessage);
            return builder.toString();
        }

    }

}
