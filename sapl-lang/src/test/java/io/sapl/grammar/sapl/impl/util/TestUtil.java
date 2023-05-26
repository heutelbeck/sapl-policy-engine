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
package io.sapl.grammar.sapl.impl.util;

import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import io.sapl.interpreter.DocumentEvaluationResult;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.test.StepVerifier;

@Slf4j
public class TestUtil {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final static boolean DEBUG_TESTS = false;

	public static Predicate<DocumentEvaluationResult> hasDecision(AuthorizationDecision expected) {
		return saplDecision -> {
			if (DEBUG_TESTS) {
				log.debug("Expected: {}", expected);
				log.debug("Actual  : {}", saplDecision.getAuthorizationDecision());
				log.debug("From    : {}", saplDecision);
			}
			return expected.equals(saplDecision.getAuthorizationDecision());
		};
	}

	public static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = SaplFactory.eINSTANCE.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}

	@SneakyThrows
	public static void expressionEvaluatesTo(String expression, String... expected) {
		if (DEBUG_TESTS) {
			log.debug("Expression: {}", expression);
			for (var e : expected)
				log.debug("Expected  : {}", e);
		}

		var expectations = new Val[expected.length];
		var i            = 0;
		for (var ex : expected) {
			expectations[i++] = Val.ofJson(ex);
		}
		expressionEvaluatesTo(ParserUtil.expression(expression), expectations);
	}

	@SneakyThrows
	public static void expressionEvaluatesTo(String expression, Val... expected) {
		if (DEBUG_TESTS) {
			log.debug("Expression: {}", expression);
			for (var e : expected)
				log.debug("Expected  : {}", e);
		}
		expressionEvaluatesTo(ParserUtil.expression(expression), expected);
	}

	@SneakyThrows
	public static void expressionErrors(String expression) {
		if (DEBUG_TESTS) {
			log.debug("Expression: {}", expression);
			log.debug("Expected  : ERROR");
		}
		expressionErrors(ParserUtil.expression(expression));
	}

	@SneakyThrows
	public static void expressionErrors(String expression, String errorMessage) {
		if (DEBUG_TESTS) {
			log.debug("Expression: {}", expression);
			log.debug("Expected  : ERROR[{}", errorMessage + "]");
		}
		expressionErrors(ParserUtil.expression(expression), errorMessage);
	}

	public static void expressionEvaluatesTo(Expression expression, Val... expected) {
		StepVerifier.create(
				expression.evaluate().doOnNext(TestUtil::logResult).contextWrite(MockUtil::setUpAuthorizationContext))
				.expectNext(expected).verifyComplete();
	}

	public static void expressionErrors(Expression expression) {
		StepVerifier
				.create(expression.evaluate().doOnNext(TestUtil::logResult)
						.contextWrite(MockUtil::setUpAuthorizationContext))
				.expectNextMatches(Val::isError).verifyComplete();
	}

	public static void expressionErrors(Expression expression, String errorMessage) {
		StepVerifier
				.create(expression.evaluate().doOnNext(TestUtil::logResult)
						.contextWrite(MockUtil::setUpAuthorizationContext))
				.expectNextMatches(val -> val.isError() && val.getMessage().equals(errorMessage)).verifyComplete();
	}

	private static void logResult(Val result) {
		if (DEBUG_TESTS) {
			try {
				log.debug("Actual    :\n{}",
						MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result.getTrace()));
			} catch (JsonProcessingException e) {
				log.debug("Error", e);
			}
		}
	}
}
