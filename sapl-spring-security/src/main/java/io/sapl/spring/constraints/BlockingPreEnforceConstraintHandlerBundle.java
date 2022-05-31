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

import static io.sapl.spring.constraints.BundleUtil.consumeAll;
import static io.sapl.spring.constraints.BundleUtil.runAll;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.aopalliance.intercept.MethodInvocation;

public class BlockingPreEnforceConstraintHandlerBundle {

	final List<Runnable>                   onDecisionHandlers       = new LinkedList<>();
	final List<Consumer<MethodInvocation>> methodInvocationHandlers = new LinkedList<>();

	public void handleOnDecisionConstraints() {
		runAll(onDecisionHandlers).run();
	}

	public void handleMethodInvocationHandlers(MethodInvocation methodInvocation) {
		consumeAll(methodInvocationHandlers).accept(methodInvocation);
	}

}
