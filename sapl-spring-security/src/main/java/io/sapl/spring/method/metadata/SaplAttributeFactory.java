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

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

/**
 * This factory is used to create the ConfigAttributes for SAPL method security.
 */
public class SaplAttributeFactory implements AopInfrastructureBean {

	private final Object parserLock = new Object();

	private ExpressionParser parser;

	private MethodSecurityExpressionHandler handler;

	public SaplAttributeFactory(MethodSecurityExpressionHandler handler) {
		this.handler = handler;
	}

	public SaplAttribute attributeFrom(PreEnforce annotation) {
		return new PreEnforceAttribute(parameterToExpression(annotation.subject()),
				parameterToExpression(annotation.action()), parameterToExpression(annotation.resource()),
				parameterToExpression(annotation.environment()), annotation.genericsType());
	}

	public SaplAttribute attributeFrom(PostEnforce annotation) {
		return new PostEnforceAttribute(parameterToExpression(annotation.subject()),
				parameterToExpression(annotation.action()), parameterToExpression(annotation.resource()),
				parameterToExpression(annotation.environment()), annotation.genericsType());
	}

	public SaplAttribute attributeFrom(EnforceTillDenied annotation) {
		return new EnforceTillDeniedAttribute(parameterToExpression(annotation.subject()),
				parameterToExpression(annotation.action()), parameterToExpression(annotation.resource()),
				parameterToExpression(annotation.environment()), annotation.genericsType());
	}

	public SaplAttribute attributeFrom(EnforceDropWhileDenied annotation) {
		return new EnforceDropWhileDeniedAttribute(parameterToExpression(annotation.subject()),
				parameterToExpression(annotation.action()), parameterToExpression(annotation.resource()),
				parameterToExpression(annotation.environment()), annotation.genericsType());
	}

	public SaplAttribute attributeFrom(EnforceRecoverableIfDenied annotation) {
		return new EnforceRecoverableIfDeniedAttribute(parameterToExpression(annotation.subject()),
				parameterToExpression(annotation.action()), parameterToExpression(annotation.resource()),
				parameterToExpression(annotation.environment()), annotation.genericsType());
	}

	private Expression parameterToExpression(String parameter) {
		try {
			return parameter == null || parameter.isEmpty() ? null : getParser().parseExpression(parameter);
		}
		catch (ParseException e) {
			throw new IllegalArgumentException("Failed to parse expression '" + e.getExpressionString() + "'", e);
		}
	}

	/**
	 * Delay the lookup of the {@link ExpressionParser} to prevent SEC-2136.
	 *
	 * This is analog to the original spring security implementation
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
