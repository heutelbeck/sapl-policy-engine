package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.ecore.EObject;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TargetExpressionUtil {

	/**
	 * Used to check for illegal attributes or lazy operators in target expressions.
	 * 
	 * @param object an EObject in the AST
	 * @return true, the object is the target expression in a Policy or Policy Set.
	 */
	public boolean isInTargetExpression(EObject object) {
		EObject current = object;
		while (current.eContainer() != null) {
			var container = current.eContainer();
			var containerName = container.eClass().getName();
			if (containerName.equals("Policy")) {
				var policy = (Policy) container;
				var targetExpression = policy.getTargetExpression();
				if (current == targetExpression) {
					return true;
				}
			} else if (containerName.equals("PolicySet")) {
				var policy = (PolicySet) container;
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
