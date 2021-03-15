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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.interpreter.InitializationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;

public class TemporalFunctionLibraryTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());
	static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES);

	static final String authzSubscription = "{ \"subject\": \"somebody\", \"action\": \"read\", \"resource\": {}, \"environment\": {}}";

	static AuthorizationSubscription authzSubscriptionObj;

	@BeforeClass
	public static void beforeClass() throws InitializationException, JsonProcessingException {
		FUNCTION_CTX.loadLibrary(new StandardFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new TemporalFunctionLibrary());
		ATTRIBUTE_CTX.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
		authzSubscriptionObj = MAPPER.readValue(authzSubscription, AuthorizationSubscription.class);
	}

	@Test
	public void nowPlus10Seconds() {
		var zoneId = Val.of("UTC");
		var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
		var plus10 = TemporalFunctionLibrary.plusSeconds(now, Val.of(10L));
		var expected = Instant.parse(now.get().asText()).plusSeconds(10).toString();
		assertThat("plusSeconds() not working as expected", plus10.get().asText(), equalTo(expected));
	}

	@Test
	public void dayOfWeekFrom() {
		var zoneId = Val.of("UTC");
		var now = new ClockPolicyInformationPoint().now(zoneId, Collections.emptyMap()).blockFirst();
		var dayOfWeek = TemporalFunctionLibrary.dayOfWeekFrom(now);
		var expected = DayOfWeek.from(Instant.now().atOffset(ZoneOffset.UTC)).toString();
		assertThat("dayOfWeekFrom() not working as expected", dayOfWeek.get().asText(), equalTo(expected));
	}

	@Test
	public void policyWithMatchingTemporalBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where time.before(\"UTC\".<clock.now>, time.plusSeconds(\"UTC\".<clock.now>, 10));";
		var expectedAuthzDecision = AuthorizationDecision.PERMIT;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("temporal functions not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithNonMatchingTemporalBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where time.after(\"UTC\".<clock.now>, time.plusSeconds(\"UTC\".<clock.now>, 10));";
		var expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("temporal functions not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithDayOfWeekBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where time.dayOfWeekFrom(\"UTC\".<clock.now>) == \"SUNDAY\";";
		AuthorizationDecision expectedAuthzDecision;
		if (DayOfWeek.from(Instant.now().atOffset(ZoneOffset.UTC)) == DayOfWeek.SUNDAY) {
			expectedAuthzDecision = AuthorizationDecision.PERMIT;
		} else {
			expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE;
		}
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("dayOfWeek() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithLocalDateTimeBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where standard.length(time.localDateTime(\"UTC\".<clock.now>)) in [16, 19];";
		var expectedAuthzDecision = AuthorizationDecision.PERMIT;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("localDateTime() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithLocalTimeBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where standard.length(time.localTime(\"UTC\".<clock.now>)) in [5, 8];";
		var expectedAuthzDecision = AuthorizationDecision.PERMIT;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("localTime() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithLocalHourBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where var hour = time.localHour(\"UTC\".<clock.now>); hour >= 0 && hour <= 23;";
		var expectedAuthzDecision = AuthorizationDecision.PERMIT;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("localHour() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithLocalMinuteBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where var minute = time.localMinute(\"UTC\".<clock.now>); minute >= 0 && minute <= 59;";
		var expectedAuthzDecision = AuthorizationDecision.PERMIT;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("localMinute() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	public void policyWithLocalSecondBody() {
		var policyDefinition = "policy \"test\" permit action == \"read\" where var second = time.localSecond(\"UTC\".<clock.now>); second >= 0 && second <= 59;";
		var expectedAuthzDecision = AuthorizationDecision.PERMIT;
		var authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("localSecond() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}
}
