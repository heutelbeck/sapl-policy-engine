/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.attributes;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.ConfigAttribute;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Superclass for the SAPL ConfigAttributes, taking care of initializing SpEL
 * expression upon instantiation.
 */
@Getter
@EqualsAndHashCode
public abstract class AbstractPolicyBasedEnforcementAttribute implements ConfigAttribute {

	private static final long serialVersionUID = -2697854599354396960L;

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private final Expression subjectExpression;
	private final Expression actionExpression;
	private final Expression resourceExpression;
	private final Expression environmentExpression;
	private final Class<?> genericsType;

	protected AbstractPolicyBasedEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression, Class<?> genericsType) {
		this(subjectExpression == null ? null : PARSER.parseExpression(subjectExpression),
				actionExpression == null ? null : PARSER.parseExpression(actionExpression),
				resourceExpression == null ? null : PARSER.parseExpression(resourceExpression),
				environmentExpression == null ? null : PARSER.parseExpression(environmentExpression), genericsType);
	}

	protected AbstractPolicyBasedEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression, Class<?> genericsType) {
		this.subjectExpression = subjectExpression;
		this.actionExpression = actionExpression;
		this.resourceExpression = resourceExpression;
		this.environmentExpression = environmentExpression;
		this.genericsType = genericsType;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.security.access.ConfigAttribute#getAttribute()
	 */
	@Override
	public String getAttribute() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('[').append(getClass().getSimpleName()).append(": subject='")
				.append(subjectExpression == null ? "null" : subjectExpression.getExpressionString());
		sb.append("', action='").append(actionExpression == null ? "null" : actionExpression.getExpressionString());
		sb.append("', resource='")
				.append(resourceExpression == null ? "null" : resourceExpression.getExpressionString());
		sb.append("', environment='")
				.append(environmentExpression == null ? "null" : environmentExpression.getExpressionString());
		sb.append("', genericsType='").append(genericsType == null ? "null" : genericsType.getSimpleName())
				.append("']");
		return sb.toString();
	}

}
