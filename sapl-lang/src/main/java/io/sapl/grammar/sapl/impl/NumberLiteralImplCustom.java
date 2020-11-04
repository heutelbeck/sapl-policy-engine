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

import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

public class NumberLiteralImplCustom extends NumberLiteralImpl {

	@Override
	public Flux<Val> evaluate(EvaluationContext ctx, Val relativeNode) {
		return Val.fluxOf(getNumber());
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		hash = 37 * hash + Objects.hashCode(getNumber());
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
		final NumberLiteralImplCustom otherImpl = (NumberLiteralImplCustom) other;
		return Objects.equals(getNumber(), otherImpl.getNumber());
	}

}
