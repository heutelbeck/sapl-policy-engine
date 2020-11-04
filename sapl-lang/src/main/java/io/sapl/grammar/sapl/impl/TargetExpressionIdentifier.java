package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.ecore.EObject;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TargetExpressionIdentifier {

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
