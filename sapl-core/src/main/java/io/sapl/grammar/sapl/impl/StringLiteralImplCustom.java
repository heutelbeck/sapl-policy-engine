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

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

public class StringLiteralImplCustom extends io.sapl.grammar.sapl.impl.StringLiteralImpl {

	private static final int HASH_PRIME_09 = 47;
	private static final int INIT_PRIME_02 = 5;

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		return Flux.just(Optional.of(JsonNodeFactory.instance.textNode(getString())));
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_02;
		hash = HASH_PRIME_09 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_09 * hash + Objects.hashCode(getString());
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
		final StringLiteralImplCustom otherImpl = (StringLiteralImplCustom) other;
		return Objects.equals(getString(), otherImpl.getString());
	}

}
