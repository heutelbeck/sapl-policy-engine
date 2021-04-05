/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TemporalFunctionLibraryTest {

    static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
    static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
    static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());
    static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
            SYSTEM_VARIABLES);

    static final String authzSubscription = "{ \"subject\": \"somebody\", \"action\": \"read\", \"resource\": {}, \"environment\": {}}";

    static AuthorizationSubscription authzSubscriptionObj;

    @BeforeAll
    static void beforeClass() throws InitializationException, JsonProcessingException {
        FUNCTION_CTX.loadLibrary(new StandardFunctionLibrary());
        FUNCTION_CTX.loadLibrary(new TemporalFunctionLibrary());
        ATTRIBUTE_CTX.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
        authzSubscriptionObj = MAPPER.readValue(authzSubscription, AuthorizationSubscription.class);
    }

    @Test
    void nowPlus10Nanos() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var plus10 = TemporalFunctionLibrary.plusNanos(now, Val.of(10L));
        var expected = Instant.parse(now.get().asText()).plusNanos(10).toString();
        assertThat(plus10, is(val(expected)));
    }

    @Test
    void nowPlus10Millis() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var plus10 = TemporalFunctionLibrary.plusMillis(now, Val.of(10L));
        var expected = Instant.parse(now.get().asText()).plusMillis(10).toString();
        assertThat(plus10, is(val(expected)));
    }

    @Test
    void nowPlus10Seconds() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var plus10 = TemporalFunctionLibrary.plusSeconds(now, Val.of(10L));
        var expected = Instant.parse(now.get().asText()).plusSeconds(10).toString();
        assertThat(plus10, is(val(expected)));
    }

    @Test
    void nowMinus10Nanos() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var plus10 = TemporalFunctionLibrary.minusNanos(now, Val.of(10L));
        var expected = Instant.parse(now.get().asText()).minusNanos(10).toString();
        assertThat(plus10, is(val(expected)));
    }

    @Test
    void nowMinus10Millis() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var plus10 = TemporalFunctionLibrary.minusMillis(now, Val.of(10L));
        var expected = Instant.parse(now.get().asText()).minusMillis(10).toString();
        assertThat(plus10, is(val(expected)));
    }

    @Test
    void nowMinus10Seconds() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var plus10 = TemporalFunctionLibrary.minusSeconds(now, Val.of(10L));
        var expected = Instant.parse(now.get().asText()).minusSeconds(10).toString();
        assertThat(plus10, is(val(expected)));
    }

    @Test
    void dayOfWeekFrom() {
        var zoneId = Val.of("UTC");
        var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
        var dayOfWeek = TemporalFunctionLibrary.dayOfWeekFrom(now);
        var expected = DayOfWeek.from(Instant.now().atOffset(ZoneOffset.UTC)).toString();
        assertThat(dayOfWeek, is(val(expected)));
    }

    @Test
    void betweenTest() {
        var now = Instant.now();
        var yesterday = now.minus(1, ChronoUnit.DAYS);
        var tomorrow = now.plus(1, ChronoUnit.DAYS);

        //yesterday < now < tomorrow
        var isBetween = TemporalFunctionLibrary.between(Val.of(now.toString()), Val.of(yesterday.toString()), Val.of(tomorrow.toString()));
        assertThat(isBetween.getBoolean(), is(true));

        //yesterday = now < tomorrow
        now = yesterday;
        isBetween = TemporalFunctionLibrary.between(Val.of(now.toString()), Val.of(yesterday.toString()), Val.of(tomorrow.toString()));
        assertThat(isBetween.getBoolean(), is(true));

        //yesterday = now = tomorrow
        now = tomorrow;
        isBetween = TemporalFunctionLibrary.between(Val.of(now.toString()), Val.of(yesterday.toString()), Val.of(tomorrow.toString()));
        assertThat(isBetween.getBoolean(), is(true));
    }

    @Test
    void should_return_error_for_invalid_time_arguments() {
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::before);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::after);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::plusMillis);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::plusNanos);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::plusSeconds);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::minusMillis);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::minusNanos);
        assertErrorValIsReturnedTwoArgs(TemporalFunctionLibrary::minusSeconds);

        assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::dayOfWeekFrom);
        assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::localDateTime);
        assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::localTime);
        assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::localHour);
        assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::localMinute);
        assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::localSecond);


        assertThat(TemporalFunctionLibrary.between(Val.NULL, Val.of((BigDecimal) null), Val.of((String) null)).isError(), is(true));
        assertThat(TemporalFunctionLibrary.between(Val.UNDEFINED, Val.UNDEFINED, Val.UNDEFINED).isError(), is(true));
        assertThat(TemporalFunctionLibrary.between(Val.of("abc"), Val.of("def"), Val.of("ghi")).isError(), is(true));


    }

    private void assertErrorValIsReturnedOneArg(Function<Val, Val> function) {
        assertThat(function.apply(Val.NULL).isError(), is(true));
        assertThat(function.apply(Val.of((String) null)).isError(), is(true));
        assertThat(function.apply(Val.UNDEFINED).isError(), is(true));
        assertThat(function.apply(Val.of("abc")).isError(), is(true));
    }

    private void assertErrorValIsReturnedTwoArgs(BiFunction<Val, Val, Val> function) {
        assertThat(function.apply(Val.NULL, Val.of((String) null)).isError(), is(true));
        assertThat(function.apply(Val.UNDEFINED, Val.UNDEFINED).isError(), is(true));
        assertThat(function.apply(Val.of("abc"), Val.of("def")).isError(), is(true));
    }

    private void assertErrorValIsReturnedThreeArgs(BiFunction<Val, Val, Val> function) {
        assertThat(function.apply(Val.NULL, Val.of((String) null)).isError(), is(true));
        assertThat(function.apply(Val.UNDEFINED, Val.UNDEFINED).isError(), is(true));
        assertThat(function.apply(Val.of("abc"), Val.of("def")).isError(), is(true));
    }

    @Test
    void policyWithMatchingTemporalBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where time.before(\"UTC\".<clock.now>, time.plusSeconds(\"UTC\".<clock.now>, 10));";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;

        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithNonMatchingTemporalBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where time.after(\"UTC\".<clock.now>, time.plusSeconds(\"UTC\".<clock.now>, 10));";
        var expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithDayOfWeekBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where time.dayOfWeekFrom(\"UTC\".<clock.now>) == \"SUNDAY\";";
        AuthorizationDecision expectedAuthzDecision;
        if (DayOfWeek.from(Instant.now().atOffset(ZoneOffset.UTC)) == DayOfWeek.SUNDAY) {
            expectedAuthzDecision = AuthorizationDecision.PERMIT;
        } else {
            expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE;
        }
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithLocalDateTimeBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where standard.length(time.localDateTime(\"UTC\".<clock.now>)) in [16, 19];";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithLocalTimeBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where standard.length(time.localTime(\"UTC\".<clock.now>)) in [5, 8];";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithLocalHourBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where var hour = time.localHour(\"UTC\".<clock.now>); hour >= 0 && hour <= 23;";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithLocalMinuteBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where var minute = time.localMinute(\"UTC\".<clock.now>); minute >= 0 && minute <= 59;";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithLocalSecondBody() {
        var policyDefinition = "policy \"test\" permit action == \"read\" where var second = time.localSecond(\"UTC\".<clock.now>); second >= 0 && second <= 59;";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    private void assertThatPolicyEvaluatesTo(String policyDefinition, AuthorizationDecision expectedAuthzDecision) {
        StepVerifier.create(INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT))
                .assertNext(authzDecision -> {
                    assertThat(authzDecision, is(expectedAuthzDecision));
                }).verifyComplete();
    }

}
