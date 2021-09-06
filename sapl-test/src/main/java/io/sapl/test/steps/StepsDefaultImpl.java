package io.sapl.test.steps;

import static io.sapl.hamcrest.Matchers.*;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.FunctionCall;
import io.sapl.test.mocking.FunctionParameters;
import io.sapl.test.mocking.MockingAttributeContext;
import io.sapl.test.mocking.MockingFunctionContext;
import io.sapl.test.verification.TimesCalledVerification;

import org.hamcrest.Matcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.test.StepVerifier.Step;
import reactor.test.scheduler.VirtualTimeScheduler;

public abstract class StepsDefaultImpl implements GivenStep, WhenStep, GivenOrWhenStep, ExpectStep, ExpectOrVerifyStep {
    protected static final String ERROR_COULD_NOT_PARSE_JSON = "Error parsing the specified JSON for your AuthorizationSubscription";
    protected static final String ERROR_NULL_JSONNODE = "Error reading the specified JsonNode for your AuthorizationSubscription. It was null";
    protected static final String ERROR_EXPECT_NEXT_0_OR_NEGATIVE = "0 or a negative value is not allowed for the count of expected events";
    protected static final String ERROR_EXPECT_VIRTUAL_TIME_REGISTRATION_BEFORE_TIMING_ATTRIBUTE_MOCK = "Error expecting to register the Virtual-Time-Mode before mocking an attribute emitting timed values. Did you forget to call \".withVirtualTime()\" first?";


    protected MockingAttributeContext mockingAttributeContext;
    protected MockingFunctionContext mockingFunctionContext;
    protected Map<String, JsonNode> variables;
    protected LinkedList<AttributeMockReturnValues> mockedAttributeValues;
    protected Step<AuthorizationDecision> steps;
    protected boolean withVirtualTime;
    protected NumberOfExpectSteps numberOfExpectSteps;

    public StepsDefaultImpl() {
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
    public GivenOrWhenStep givenFunction(String importName, Val returns, FunctionParameters parameter) {
        this.mockingFunctionContext.loadFunctionMockAlwaysSameValueForParameters(importName, returns, parameter);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Val returns, FunctionParameters parameters,
                                         TimesCalledVerification verification) {
        this.mockingFunctionContext.loadFunctionMockAlwaysSameValueForParameters(importName, returns, parameters,
                verification);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Function<FunctionCall, Val> returns) {
        this.mockingFunctionContext.loadFunctionMockValueFromFunction(importName, returns);
        return this;
    }

    @Override
    public GivenOrWhenStep givenFunction(String importName, Function<FunctionCall, Val> returns,
                                         TimesCalledVerification verification) {
        this.mockingFunctionContext.loadFunctionMockValueFromFunction(importName, returns, verification);
        return this;
    }

    @Override
    public GivenOrWhenStep givenAttribute(String importName, Val... returns) {
        // this.mockingAttributeContext.loadAttributeMock(importName, returns);
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
    public ExpectStep when(String jsonauthzSub) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode authzSubJsonNode = objectMapper.readTree(jsonauthzSub);
        AuthorizationSubscription authzSub = new AuthorizationSubscription(authzSubJsonNode.findValue("subject"),
                authzSubJsonNode.findValue("action"), authzSubJsonNode.findValue("resource"),
                authzSubJsonNode.findValue("environment"));
        createStepVerifier(authzSub);
        return this;
    }

    @Override
    public ExpectStep when(JsonNode jsonNode) {
        if (jsonNode != null) {
            AuthorizationSubscription authzSub = new AuthorizationSubscription(jsonNode.findValue("subject"),
                    jsonNode.findValue("action"), jsonNode.findValue("resource"),
                    jsonNode.findValue("environment"));
            createStepVerifier(authzSub);
            return this;
        }
        throw new SaplTestException(ERROR_NULL_JSONNODE);
    }

    protected abstract void createStepVerifier(AuthorizationSubscription authzSub);

    @Override
    public VerifyStep expectPermit() {
        return this.expect(isPermit());
    }

    @Override
    public VerifyStep expectDeny() {
        return this.expect(isDeny());
    }

    @Override
    public VerifyStep expectIndeterminate() {
        return this.expect(isIndeterminate());
    }

    @Override
    public VerifyStep expectNotApplicable() {
        return this.expect(isNotApplicable());
    }

    @Override
    public VerifyStep expect(AuthorizationDecision authDec) {
        this.steps = this.steps.expectNext(authDec);
        this.numberOfExpectSteps.addExpectStep();
        return this;
    }

    @Override
    public VerifyStep expect(Predicate<AuthorizationDecision> pred) {
        this.steps = this.steps.expectNextMatches(pred);
        this.numberOfExpectSteps.addExpectStep();
        return this;
    }

    @Override
    public VerifyStep expect(Matcher<AuthorizationDecision> matcher) {
        this.steps = this.steps.expectNextMatches(dec -> matcher.matches(dec));
        this.numberOfExpectSteps.addExpectStep();
        return this;
    }

    // #

    @Override
    public ExpectOrVerifyStep expectNextPermit() {
        return this.expectNext(isPermit());
    }

    @Override
    public ExpectOrVerifyStep expectNextPermit(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
            this.expectNext(isPermit());
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextDeny() {
        return this.expectNext(isDeny());
    }

    @Override
    public ExpectOrVerifyStep expectNextDeny(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
        	this.expectNext(isDeny());
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextIndeterminate() {
        return this.expectNext(isIndeterminate());
    }

    @Override
    public ExpectOrVerifyStep expectNextIndeterminate(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
        	this.expectNext(isIndeterminate());
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNextNotApplicable() {
    	return this.expectNext(isNotApplicable()); 
    }

    @Override
    public ExpectOrVerifyStep expectNextNotApplicable(Integer count) {
        if (count < 1) {
            throw new SaplTestException(ERROR_EXPECT_NEXT_0_OR_NEGATIVE);
        }
        for (int i = 0; i < count; i++) {
        	 this.expectNext(isNotApplicable()); 
        }
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNext(AuthorizationDecision authDec) {
        this.steps = this.steps.expectNext(authDec);
        this.numberOfExpectSteps.addExpectStep();
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher) {
        this.steps = this.steps.expectNextMatches(dec -> matcher.matches(dec));
        this.numberOfExpectSteps.addExpectStep();
        return this;
    }

    @Override
    public ExpectOrVerifyStep expectNext(Predicate<AuthorizationDecision> pred) {
        this.steps = this.steps.expectNextMatches(pred);
        this.numberOfExpectSteps.addExpectStep();
        return this;
    }

    // #

    @Override
    public ExpectOrVerifyStep thenAttribute(String importName, Val returns) {
        this.steps = this.steps.then(() -> this.mockingAttributeContext.mockEmit(importName, returns));
        return this;
    }

    // #

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
        // this.steps.verifyComplete();

        this.mockingAttributeContext.assertVerifications();
        this.mockingFunctionContext.assertVerifications();

    }
}