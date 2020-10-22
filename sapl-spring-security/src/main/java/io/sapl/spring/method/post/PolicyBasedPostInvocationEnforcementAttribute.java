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
package io.sapl.spring.method.post;

import org.springframework.expression.Expression;

import io.sapl.spring.method.AbstractPolicyBasedEnforcementAttribute;

public class PolicyBasedPostInvocationEnforcementAttribute extends AbstractPolicyBasedEnforcementAttribute
		implements PostInvocationEnforcementAttribute {

	private static final long serialVersionUID = -3012177291107121964L;

	public PolicyBasedPostInvocationEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

	public PolicyBasedPostInvocationEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

}
