/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Checks for a value matching a regular expression.
 *
 * Grammar: Comparison returns Expression: Prefixed (({Regex.left=current} '=~')
 * right=Prefixed)? ;
 */
public class RegexImplCustom extends RegexImpl {

	private static final String REGEX_SYNTAX_ERROR = "Syntax error in regular expression '%s'.";

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<Optional<JsonNode>> left = getLeft().evaluate(ctx, isBody, relativeNode);
		final Flux<String> right = getRight().evaluate(ctx, isBody, relativeNode).flatMap(Value::toString);
		return Flux.combineLatest(left, right, Tuples::of).distinctUntilChanged().flatMap(this::matchRegexp);
	}

	private Flux<Optional<JsonNode>> matchRegexp(Tuple2<Optional<JsonNode>, String> tuple) {
		if (!tuple.getT1().isPresent()) {
			return Value.fluxOfFalse();
		}
		if (!tuple.getT1().get().isTextual()) {
			return Value.fluxOfFalse();
		}
		try {
			return Value.fluxOf(Pattern.matches(tuple.getT2(), tuple.getT1().get().asText()));
		}
		catch (PatternSyntaxException e) {
			return Flux.error(new PolicyEvaluationException(String.format(REGEX_SYNTAX_ERROR, tuple.getT2()), e));
		}
	}

}
