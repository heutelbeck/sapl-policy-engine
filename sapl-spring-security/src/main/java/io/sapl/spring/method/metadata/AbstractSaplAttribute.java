/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.metadata;

import org.springframework.expression.Expression;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Superclass for the SAPL ConfigAttributes, taking care of initializing SpEL expression
 * upon instantiation.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public abstract class AbstractSaplAttribute implements SaplAttribute {

	private final Expression subjectExpression;

	private final Expression actionExpression;

	private final Expression resourceExpression;

	private final Expression environmentExpression;

	private final Class<?> genericsType;

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
		return '[' + getClass().getSimpleName() + ": subject='"
				+ (subjectExpression == null ? "null" : subjectExpression.getExpressionString()) + "', action='"
				+ (actionExpression == null ? "null" : actionExpression.getExpressionString()) + "', resource='"
				+ (resourceExpression == null ? "null" : resourceExpression.getExpressionString()) + "', environment='"
				+ (environmentExpression == null ? "null" : environmentExpression.getExpressionString())
				+ "', genericsType='" + (genericsType == null ? "null" : genericsType.getSimpleName()) + "']";
	}

}
