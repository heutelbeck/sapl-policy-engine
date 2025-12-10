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
package io.sapl.test.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.MockingAttributeBroker;
import io.sapl.test.mocking.attribute.models.AttributeEntityValueMatcher;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.function.MockingFunctionBroker;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;
import org.hamcrest.Matcher;
import reactor.test.StepVerifier.Step;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.sapl.test.steps.AuthorizationDecisionMatchers.*;

public abstract class StepsDefaultImpl implements GivenStep, WhenStep, GivenOrWhenStep, ExpectStep, ExpectOrVerifyStep {

    protected static final String ERROR_COULD_NOT_PARSE_JSON = "Error parsing the specified JSON for your AuthorizationSubscription";

    protected static final String ERROR_NULL_JSON_NODE = "Error reading the specified JsonNode for your AuthorizationSubscription. It was null";

    protected static final String ERROR_EXPECT_NEXT_0_OR_NEGATIVE = "0 or a negative value is not allowed for the count of expected events";

    protected static final String ERROR_EXPECT_VIRTUAL_TIME_REGISTRATION_BEFORE_TIMING_ATTRIBUTE_MOCK = "Error expecting to register the Virtual-Time-Mode before mocking an attribute emitting timed values. Did you forget to call \".withVirtualTime()\" first?";

    protected MockingAttributeBroker mockingAttributeBroker;

    protected MockingFunctionBroker mockingFunctionBroker;

    protected Map<String, Value> variables;

    protected LinkedList<AttributeMockReturnValues> mockedAttributeValues;

    protected Step<AuthorizationDecision> steps;

    protected boolean withVirtualTime;

    protected final NumberOfExpectSteps numberOfExpectSteps;

    protected StepsDefaultImpl() {
        this.numberOfExpectSteps = new NumberOfExpectSteps();
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Value returns) {
        this.mockingFunctionBroker.mockFunctionAlwaysReturns(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Value returns, TimesCalledVerification verification) {
        // Note: TimesCalledVerification is not yet supported in the new API
        // TODO: Add verification support to MockingFunctionBroker
        this.mockingFunctionBroker.mockFunctionAlwaysReturns(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunctionOnce(String importName, Value returns) {
        this.mockingFunctionBroker.mockFunctionReturnsSequence(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunctionOnce(String importName, Value... returns) {
        this.mockingFunctionBroker.mockFunctionReturnsSequence(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, FunctionParameters parameter, Value returns) {
        this.mockingFunctionBroker.mockFunctionForParameterMatchers(importName, parameter, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, FunctionParameters parameters, Value returns,
            TimesCalledVerification verification) {
        // Note: Verification not yet fully supported
        this.mockingFunctionBroker.mockFunctionForParameterMatchers(importName, parameters, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Function<List<Value>, Value> returns) {
        this.mockingFunctionBroker.mockFunctionComputed(importName,
                invocation -> returns.apply(invocation.arguments()));
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Function<List<Value>, Value> returns,
            TimesCalledVerification verification) {
        // Note: Verification not yet supported in new API
        this.mockingFunctionBroker.mockFunctionComputed(importName,
                invocation -> returns.apply(invocation.arguments()));
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, Value... returns) {
        this.mockingAttributeBroker.markAttributeMock(importName);
        this.mockedAttributeValues.add(AttributeMockReturnValues.of(importName, List.of(returns)));
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, Duration timing, Value... returns) {
        if (!this.withVirtualTime) {
            throw new SaplTestException(ERROR_EXPECT_VIRTUAL_TIME_REGISTRATION_BEFORE_TIMING_ATTRIBUTE_MOCK);
        }
        this.mockingAttributeBroker.mockAttributeWithTimedSequence(importName, timing, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName) {
        this.mockingAttributeBroker.markAttributeMock(importName);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, AttributeEntityValueMatcher parentValueMatcher,
            Value returns) {
        // Note: AttributeEntityValueMatcher integration needs to be updated
        // TODO: Update to use new API's entity matcher
        this.mockingAttributeBroker.mockAttributeAlwaysReturns(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, AttributeParameters parameters, Value returns) {
        this.mockingAttributeBroker.mockAttributeForParameterMatchers(importName, parameters, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep withVirtualTime() {
        this.withVirtualTime = true;

        VirtualTimeScheduler.getOrSet();

        return this;
    }

    @Override
    public ExpectStep when(AuthorizationSubscription authzSub) {
        createStepVerifier(authzSub);
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
        createStepVerifier(authzSub);
        return this;
    }

    @Override
    public ExpectStep when(JsonNode jsonNode) {
        if (jsonNode != null) {
            var authzSub = AuthorizationSubscription.of(ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("subject")),
                    ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("action")),
                    ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("resource")),
                    ValueJsonMarshaller.fromJsonNode(jsonNode.findValue("environment")));
            createStepVerifier(authzSub);
            return this;
        }
        throw new SaplTestException(ERROR_NULL_JSON_NODE);
    }

    protected abstract void createStepVerifier(AuthorizationSubscription authzSub);

    @Override
    public VerifyStep expectPermit() {
        return this.expectNext(isPermit(), "AuthorizationDecision.PERMIT");
    }

    @Override
    public VerifyStep expectDeny() {
        return this.expectNext(isDeny(), "AuthorizationDecision.DENY");
    }

    @Override
    public VerifyStep expectIndeterminate() {
        return this.expectNext(isIndeterminate(), "AuthorizationDecision.INDETERMINATE");
    }

    @Override
    public VerifyStep expectNotApplicable() {
        return this.expectNext(isNotApplicable(), "AuthorizationDecision.NOT_APPLICABLE");
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
        return this.expectNext(matcher, matcher.toString());
    }

    @Override
    public ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher) {
        return this.expectNext(matcher, matcher.toString());
    }

    private ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher, String message) {
        this.numberOfExpectSteps.addExpectStep();
        this.steps = this.steps.expectNextMatches(matcher::matches).as(getDebugMessage(message));
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextPermit() {
        return this.expectNextPermit(1);
    }

    @Override
    public ExpectOrVerifyStep expectNextPermit(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
            this.expectNext(isPermit(), "AuthorizationDecision.PERMIT");
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextDeny() {
        return this.expectNextDeny(1);
    }

    @Override
    public ExpectOrVerifyStep expectNextDeny(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
            this.expectNext(isDeny(), "AuthorizationDecision.DENY");
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextIndeterminate() {
        return this.expectNextIndeterminate(1);
    }

    @Override
    public ExpectOrVerifyStep expectNextIndeterminate(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
            this.expectNext(isIndeterminate(), "AuthorizationDecision.INDETERMINATE");
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextNotApplicable() {
        return this.expectNextNotApplicable(1);
    }

    @Override
    public ExpectOrVerifyStep expectNextNotApplicable(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
            this.expectNext(isNotApplicable(), "AuthorizationDecision.NOT_APPLICABLE");
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep thenAttribute(String importName, Value returns) {
        this.steps = this.steps.then(() -> this.mockingAttributeBroker.emitToAttribute(importName, returns));
        return this;
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
        // TODO: Add verification support to MockingAttributeBroker and
        // MockingFunctionBroker
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
