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
package io.sapl.spring.constraints;

import java.util.function.Consumer;

import org.aopalliance.intercept.MethodInvocation;

import lombok.RequiredArgsConstructor;

/**
 * 
 * This bundle aggregates all constraint handlers for a specific decision which
 * are useful in a blocking PreEnforce scenario.
 * 
 * @author Dominic Heutelbeck
 *
 */
@RequiredArgsConstructor
public class BlockingPreEnforceConstraintHandlerBundle {
	private final Runnable                   onDecisionHandlers;
	private final Consumer<MethodInvocation> methodInvocationHandlers;

	/**
	 * Runs all onDecision constraint handlers.
	 */
	public void handleOnDecisionConstraints() {
		onDecisionHandlers.run();
	}

	/**
	 * Runs all method invocation handlers. These handlers may modify the
	 * methodInvocation.
	 * 
	 * @param methodInvocation
	 */
	public void handleMethodInvocationHandlers(MethodInvocation methodInvocation) {
		methodInvocationHandlers.accept(methodInvocation);
	}

}
