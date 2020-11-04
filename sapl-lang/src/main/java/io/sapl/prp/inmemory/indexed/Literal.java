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
package io.sapl.prp.inmemory.indexed;

import com.google.common.base.Preconditions;

import java.util.Objects;

public class Literal {

	private final Bool bool;

	private int hash;

	private boolean hasHashCode;

	private final boolean hasNegation;

	public Literal(final Bool bool) {
		this(bool, false);
	}

	public Literal(final Bool bool, boolean negation) {
		this.bool = Preconditions.checkNotNull(bool);
		hasNegation = negation;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Literal other = (Literal) obj;
		if (hashCode() != other.hashCode()) {
			return false;
		}
		if (!Objects.equals(hasNegation, other.hasNegation)) {
			return false;
		}
		return Objects.equals(bool, other.bool);
	}

	public boolean evaluate() {
		boolean result = bool.evaluate();
		if (hasNegation) {
			return !result;
		}
		return result;
	}

//	public Mono<Boolean> evaluate(final FunctionContext functionCtx, final VariableContext variableCtx)
	//			throws PolicyEvaluationException {
	//		Mono<Boolean> result = bool.evaluate(functionCtx, variableCtx);
	//		if (hasNegation) {
	//			return !result;
	//		}
	//		return result;
	//	}

	public Bool getBool() {
		return bool;
	}

	@Override
	public int hashCode() {
		if (!hasHashCode) {
			int h = 5;
			h = 19 * h + Objects.hashCode(bool);
			h = 19 * h + Objects.hashCode(hasNegation);
			hash = h;
			hasHashCode = true;
		}
		return hash;
	}

	public boolean isImmutable() {
		return bool.isImmutable();
	}

	public boolean isNegated() {
		return hasNegation;
	}

	public Literal negate() {
		return new Literal(bool, !hasNegation);
	}

	public boolean sharesBool(final Literal other) {
		return bool.equals(other.bool);
	}

	public boolean sharesNegation(final Literal other) {
		return hasNegation == other.hasNegation;
	}

}
