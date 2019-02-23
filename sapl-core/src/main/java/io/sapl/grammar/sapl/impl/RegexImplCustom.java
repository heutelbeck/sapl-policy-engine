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
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class RegexImplCustom extends io.sapl.grammar.sapl.impl.RegexImpl {

	private static final String REGEX_TYPE_MISMATCH = "Type mismatch. Matching regular expressions expects string values, but got: '%s'.";
	private static final String REGEX_SYNTAX_ERROR = "Syntax error in regular expression '%s'.";

	private static final int HASH_PRIME_13 = 67;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<Optional<JsonNode>> left = getLeft().evaluate(ctx, isBody, relativeNode);
		final Flux<Optional<JsonNode>> right = getRight().evaluate(ctx, isBody, relativeNode);
		return Flux.combineLatest(left, right, this::matchRegexp).distinctUntilChanged();
	}

	private Optional<JsonNode> matchRegexp(Optional<JsonNode> value, Optional<JsonNode> regexp) {
		try {
			if (!regexp.isPresent()) {
				throw new PolicyEvaluationException(String.format(REGEX_TYPE_MISMATCH, "undefined"));
			}
			if (!value.isPresent() || value.get().isNull()) {
				return Optional.of((JsonNode) JSON.booleanNode(false));
			}
			return regexpMatchTextNodes(value, regexp);
		} catch (PolicyEvaluationException e) {
			throw Exceptions.propagate(e);
		}
	}

	private Optional<JsonNode> regexpMatchTextNodes(Optional<JsonNode> optLeftResult, Optional<JsonNode> optRightResult)
			throws PolicyEvaluationException {
		assertTextual(optLeftResult, optRightResult);
		try {
			return Optional.of((JsonNode) JSON
					.booleanNode(Pattern.matches(optRightResult.get().asText(), optLeftResult.get().asText())));
		} catch (PatternSyntaxException e) {
			throw new PolicyEvaluationException(String.format(REGEX_SYNTAX_ERROR, optRightResult.get().asText()), e);
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
