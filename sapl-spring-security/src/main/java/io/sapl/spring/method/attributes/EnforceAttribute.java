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

import io.sapl.spring.method.annotations.EnforcementMode;
import lombok.Getter;

/**
 * Attributes which are created from @Enforce annotations.
 */
public class EnforceAttribute extends PreEnforceAttribute {

	@Getter
	EnforcementMode enforcementMode;

	public EnforceAttribute(Expression subjectExpression, Expression actionExpression, Expression resourceExpression,
			Expression environmentExpression, EnforcementMode enforcementMode, Class<?> genericsType) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression, genericsType);
		this.enforcementMode = enforcementMode;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		var sb = new StringBuilder();
		var str = super.toString();
		sb.append(str.substring(0, str.length() - 1));
		sb.append(", mode=").append(enforcementMode).append(']');
		return sb.toString();
	}
}