/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.hamcrest.Matchers.isDeny;
import static io.sapl.hamcrest.Matchers.isIndeterminate;
import static io.sapl.hamcrest.Matchers.isNotApplicable;
import static io.sapl.hamcrest.Matchers.isPermit;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.hamcrest.Matcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.MockingAttributeContext;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.attribute.models.AttributeParentValueMatcher;
import io.sapl.test.mocking.function.MockingFunctionContext;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;
import reactor.test.StepVerifier.Step;
import reactor.test.scheduler.VirtualTimeScheduler;

public abstract class StepsDefaultImpl implements GivenStep, WhenStep, GivenOrWhenStep, ExpectStep, ExpectOrVerifyStep {

    protected static final String ERROR_COULD_NOT_PARSE_JSON = "Error parsing the specified JSON for your AuthorizationSubscription";

    protected static final String ERROR_NULL_JSON_NODE = "Error reading the specified JsonNode for your AuthorizationSubscription. It was null";

    protected static final String ERROR_EXPECT_NEXT_0_OR_NEGATIVE = "0 or a negative value is not allowed for the count of expected events";

    protected static final String ERROR_EXPECT_VIRTUAL_TIME_REGISTRATION_BEFORE_TIMING_ATTRIBUTE_MOCK = "Error expecting to register the Virtual-Time-Mode before mocking an attribute emitting timed values. Did you forget to call \".withVirtualTime()\" first?";

    protected MockingAttributeContext mockingAttributeContext;

    protected MockingFunctionContext mockingFunctionContext;

    protected Map<String, JsonNode> variables;

    protected LinkedList<AttributeMockReturnValues> mockedAttributeValues;

    protected Step<AuthorizationDecision> steps;

    protected boolean withVirtualTime;

    protected final NumberOfExpectSteps numberOfExpectSteps;

    protected StepsDefaultImpl() {
        this.numberOfExpectSteps = new NumberOfExpectSteps();
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Val returns) {
        this.mockingFunctionContext.loadFunctionMockAlwaysSameValue(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Val returns, TimesCalledVerification verification) {
        this.mockingFunctionContext.loadFunctionMockAlwaysSameValue(importName, returns, verification);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunctionOnce(String importName, Val returns) {
        this.mockingFunctionContext.loadFunctionMockOnceReturnValue(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunctionOnce(String importName, Val... returns) {
        this.mockingFunctionContext.loadFunctionMockReturnsSequence(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, FunctionParameters parameter, Val returns) {
        this.mockingFunctionContext.loadFunctionMockAlwaysSameValueForParameters(importName, returns, parameter);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, FunctionParameters parameters, Val returns,
            TimesCalledVerification verification) {
        this.mockingFunctionContext.loadFunctionMockAlwaysSameValueForParameters(importName, returns, parameters,
                verification);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Function<Val[], Val> returns) {
        this.mockingFunctionContext.loadFunctionMockValueFromFunction(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Function<Val[], Val> returns,
            TimesCalledVerification verification) {
        this.mockingFunctionContext.loadFunctionMockValueFromFunction(importName, returns, verification);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, Val... returns) {
        this.mockingAttributeContext.markAttributeMock(importName);
        this.mockedAttributeValues.add(AttributeMockReturnValues.of(importName, List.of(returns)));
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, Duration timing, Val... returns) {
        if (!this.withVirtualTime) {
            throw new SaplTestException(ERROR_EXPECT_VIRTUAL_TIME_REGISTRATION_BEFORE_TIMING_ATTRIBUTE_MOCK);
        }
        this.mockingAttributeContext.loadAttributeMock(importName, timing, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName) {
        this.mockingAttributeContext.markAttributeMock(importName);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, AttributeParentValueMatcher parentValueMatcher,
            Val returns) {
        this.mockingAttributeContext.loadAttributeMockForParentValue(importName, parentValueMatcher, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, AttributeParameters parameters, Val returns) {
        this.mockingAttributeContext.loadAttributeMockForParentValueAndArguments(importName, parameters, returns);
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
        ObjectMapper              objectMapper     = new ObjectMapper();
        JsonNode                  authzSubJsonNode = objectMapper.readTree(jsonAuthzSub);
        AuthorizationSubscription authzSub         = new AuthorizationSubscription(
                authzSubJsonNode.findValue("subject"), authzSubJsonNode.findValue("action"),
                authzSubJsonNode.findValue("resource"), authzSubJsonNode.findValue("environment"));
        createStepVerifier(authzSub);
        return this;
    }

    @Override
    public ExpectStep when(JsonNode jsonNode) {
        if (jsonNode != null) {
            AuthorizationSubscription authzSub = new AuthorizationSubscription(jsonNode.findValue("subject"),
                    jsonNode.findValue("action"), jsonNode.findValue("resource"), jsonNode.findValue("environment"));
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
    public ExpectOrVerifyStep thenAttribute(String importName, Val returns) {
        this.steps = this.steps.then(() -> this.mockingAttributeContext.mockEmit(importName, returns));
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
        this.mockingAttributeContext.assertVerifications();
        this.mockingFunctionContext.assertVerifications();

    }

    private String getDebugMessage(String endOfMessage) {
        StringBuilder builder = new StringBuilder();
        switch (this.numberOfExpectSteps.getNumberOfExpectSteps()) {
        case 1 -> builder.append("1st");
        case 2 -> builder.append("2nd");
        case 3 -> builder.append("3rd");
        default -> builder.append(this.numberOfExpectSteps.getNumberOfExpectSteps()).append("th");
        }

        builder.append(" expect step failed: Expected ").append(endOfMessage);

        return builder.toString();
    }

}
