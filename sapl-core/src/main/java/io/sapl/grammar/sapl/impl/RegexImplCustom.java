/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class RegexImplCustom extends io.sapl.grammar.sapl.impl.RegexImpl {

	private static final String REGEX_SYNTAX_ERROR = "Syntax error in regular expression '%s'.";

	private static final int HASH_PRIME_13 = 67;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<JsonNode> left = getLeft().evaluate(ctx, isBody, relativeNode).flatMap(Value::toJsonNode);
		final Flux<String> right = getRight().evaluate(ctx, isBody, relativeNode).flatMap(Value::toString);
		return Flux.combineLatest(left, right, Tuples::of).distinctUntilChanged().flatMap(this::matchRegexp);
	}

	private Flux<Optional<JsonNode>> matchRegexp(Tuple2<JsonNode, String> tuple) {
		if (tuple.getT1().isNull()) {
			return Value.fluxOfFalse();
		}
		if (!tuple.getT1().isTextual()) {
			return Flux.error(new PolicyEvaluationException(
					String.format("Type mismatch. Expected String or null. Got: %s", tuple.getT2())));
		}
		try {
			return Value.fluxOf(Pattern.matches(tuple.getT2(), tuple.getT1().asText()));
		} catch (PatternSyntaxException e) {
			return Flux.error(new PolicyEvaluationException(String.format(REGEX_SYNTAX_ERROR, tuple.getT2()), e));
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_13 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_13 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_13 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final RegexImplCustom otherImpl = (RegexImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl, otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl, otherImports, imports);
	}

}
