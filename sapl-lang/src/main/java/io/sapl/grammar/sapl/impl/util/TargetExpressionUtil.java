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
package io.sapl.grammar.sapl.impl.util;

import org.eclipse.emf.ecore.EObject;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TargetExpressionUtil {

	private static final String POLICY_SET = "PolicySet";

	private static final String POLICY = "Policy";

	/**
	 * Used to check for illegal attributes or lazy operators in target expressions.
	 * 
	 * @param object an EObject in the AST
	 * @return true, the object is the target expression in a Policy or Policy Set.
	 */
	public boolean isInTargetExpression(EObject object) {
		EObject current = object;
		while (current.eContainer() != null) {
			var container     = current.eContainer();
			var containerName = container.eClass().getName();
			if (POLICY.equals(containerName)) {
				var policy           = (Policy) container;
				var targetExpression = policy.getTargetExpression();
				if (current == targetExpression) {
					return true;
				}
			} else if (POLICY_SET.equals(containerName)) {
				var policy           = (PolicySet) container;
				var targetExpression = policy.getTargetExpression();
				if (current == targetExpression) {
					return true;
				}
			}
			current = container;
		}
		return false;
	}

}
