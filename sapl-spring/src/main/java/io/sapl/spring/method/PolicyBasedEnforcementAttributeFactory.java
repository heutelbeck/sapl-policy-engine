/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import io.sapl.spring.method.post.PolicyBasedPostInvocationEnforcementAttribute;
import io.sapl.spring.method.post.PostInvocationEnforcementAttribute;
import io.sapl.spring.method.pre.PolicyBasedPreInvocationEnforcementAttribute;
import io.sapl.spring.method.pre.PreInvocationEnforcementAttribute;

/**
 * This factory is used to create the ConfigAttributes for SAPL method security.
 *
 */
public class PolicyBasedEnforcementAttributeFactory implements PolicyEnforcementAttributeFactory {

	private final Object parserLock = new Object();

	private ExpressionParser parser;

	private MethodSecurityExpressionHandler handler;

	public PolicyBasedEnforcementAttributeFactory(MethodSecurityExpressionHandler handler) {
		this.handler = handler;
	}

	@Override
	public PreInvocationEnforcementAttribute createPreInvocationAttribute(String subjectAttribute,
			String actionAttribute, String resourceAttribute, String environmentAttribute) {
		try {
			Expression subjectExpression = subjectAttribute == null || subjectAttribute.isEmpty() ? null
					: getParser().parseExpression(subjectAttribute);
			Expression actionExpression = actionAttribute == null || actionAttribute.isEmpty() ? null
					: getParser().parseExpression(actionAttribute);
			Expression resourceExpression = resourceAttribute == null || resourceAttribute.isEmpty() ? null
					: getParser().parseExpression(resourceAttribute);
			Expression environmentExpression = environmentAttribute == null || environmentAttribute.isEmpty() ? null
					: getParser().parseExpression(environmentAttribute);
			return new PolicyBasedPreInvocationEnforcementAttribute(subjectExpression, actionExpression,
					resourceExpression, environmentExpression);
		}
		catch (ParseException e) {
			throw new IllegalArgumentException("Failed to parse expression '" + e.getExpressionString() + "'", e);
		}
	}

	@Override
	public PostInvocationEnforcementAttribute createPostInvocationAttribute(String subjectAttribute,
			String actionAttribute, String resourceAttribute, String environmentAttribute) {
		try {
			Expression subjectExpression = subjectAttribute == null || subjectAttribute.isEmpty() ? null
					: getParser().parseExpression(subjectAttribute);
			Expression actionExpression = actionAttribute == null || actionAttribute.isEmpty() ? null
					: getParser().parseExpression(actionAttribute);
			Expression resourceExpression = resourceAttribute == null || resourceAttribute.isEmpty() ? null
					: getParser().parseExpression(resourceAttribute);
			Expression environmentExpression = environmentAttribute == null || environmentAttribute.isEmpty() ? null
					: getParser().parseExpression(environmentAttribute);
			return new PolicyBasedPostInvocationEnforcementAttribute(subjectExpression, actionExpression,
					resourceExpression, environmentExpression);
		}
		catch (ParseException e) {
			throw new IllegalArgumentException("Failed to parse expression '" + e.getExpressionString() + "'", e);
		}
	}

	/**
	 * Delay the lookup of the {@link ExpressionParser} to prevent SEC-2136
	 * @return the parser
	 */
	private ExpressionParser getParser() {
		if (parser != null) {
			return parser;
		}
		synchronized (parserLock) {
			parser = handler.getExpressionParser();
			handler = null;
		}
		return parser;
	}

}
