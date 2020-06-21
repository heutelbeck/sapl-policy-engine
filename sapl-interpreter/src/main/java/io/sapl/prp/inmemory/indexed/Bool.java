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

import java.util.Map;
import java.util.Objects;

import com.google.common.base.Preconditions;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.core.Exceptions;

public class Bool {

	static final String BOOL_NOT_IMMUTABLE = "Unable to evaluate volatile Bool in static context.";
	static final String CONDITION_NOT_BOOLEAN = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";

	private boolean constant;

	private Expression expression;

	private int hash;

	private boolean hasHashCode;

	private Map<String, String> imports;

	private boolean isConstantExpression;

	public Bool(boolean value) {
		isConstantExpression = true;
		constant = value;
	}

	public Bool(final Expression expression, final Map<String, String> imports) {
		this.expression = Preconditions.checkNotNull(expression);
		this.imports = imports;
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
		final Bool other = (Bool) obj;
		if (hashCode() != other.hashCode()) {
			return false;
		}
		if (!Objects.equals(isConstantExpression, other.isConstantExpression)) {
			return false;
		}
		if (isConstantExpression) {
			return Objects.equals(constant, other.constant);
		} else {
			return expression.isEqualTo(other.expression, other.imports, imports);
		}
	}

	public boolean evaluate() {
		if (isConstantExpression) {
			return constant;
		}
		throw new IllegalStateException(BOOL_NOT_IMMUTABLE);
	}

	public boolean evaluate(final FunctionContext functionCtx, final VariableContext variableCtx)
			throws PolicyEvaluationException {
		if (!isConstantExpression) {
			EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx, imports);
			try {
				Val result = expression.evaluate(ctx, false, Val.undefined()).blockFirst();
				if (result.isDefined() && result.get().isBoolean()) {
					return result.get().asBoolean();
				}
				throw new PolicyEvaluationException(CONDITION_NOT_BOOLEAN, result);
			} catch (RuntimeException e) {
				throw new PolicyEvaluationException(Exceptions.unwrap(e));
			}
		}
		return constant;
	}

	@Override
	public int hashCode() {
		if (!hasHashCode) {
			int h = 7;
			h = 59 * h + Objects.hashCode(isConstantExpression);
			if (isConstantExpression) {
				h = 59 * h + Objects.hashCode(constant);
			} else {
				h = 59 * h + expression.hash(imports);
			}
			hash = h;
			hasHashCode = true;
		}
		return hash;
	}

	public boolean isImmutable() {
		return isConstantExpression;
	}

}
