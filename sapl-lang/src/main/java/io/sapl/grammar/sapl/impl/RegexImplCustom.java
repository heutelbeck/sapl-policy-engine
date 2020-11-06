/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
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
	public Flux<Val> evaluate(@NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		final Flux<Val> left = getLeft().evaluate(ctx, relativeNode);
		final Flux<String> right = getRight().evaluate(ctx, relativeNode).concatMap(Val::toText);
		return Flux.combineLatest(left, right, Tuples::of).distinctUntilChanged().concatMap(this::matchRegexp);
	}

	private Flux<Val> matchRegexp(Tuple2<Val, String> tuple) {
		if (tuple.getT1().isUndefined()) {
			return Val.fluxOfFalse();
		}
		if (!tuple.getT1().get().isTextual()) {
			return Val.fluxOfFalse();
		}
		try {
			return Val.fluxOf(Pattern.matches(tuple.getT2(), tuple.getT1().get().asText()));
		} catch (PatternSyntaxException e) {
			return Flux.error(new PolicyEvaluationException(e, REGEX_SYNTAX_ERROR, tuple.getT2()));
		}
	}

}
