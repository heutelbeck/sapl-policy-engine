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

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

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
		var leftFlux = getLeft().evaluate(ctx, relativeNode);
		var rightFlux = getRight().evaluate(ctx, relativeNode).map(Val::requireText);
		return Flux.combineLatest(leftFlux, rightFlux, this::matchRegexp);
	}

	private Val matchRegexp(Val left, Val right) {
		if (left.isError()) {
			return left;
		}
		if (right.isError()) {
			return right;
		}
		if (!left.isTextual()) {
			return Val.FALSE;
		}
		try {
			return Val.of(Pattern.matches(right.getText(), left.getText()));
		} catch (PatternSyntaxException e) {
			return Val.error(REGEX_SYNTAX_ERROR, right);
		}
	}

}
