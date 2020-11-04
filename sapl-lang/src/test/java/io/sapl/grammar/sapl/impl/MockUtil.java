package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.common.util.EList;

import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import lombok.experimental.UtilityClass;

/**
 * Some helper methods for unit tests.
 */
@UtilityClass
public class MockUtil {
	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

	/**
	 * This utility method creates a simple parent structure for the expression
	 * PolicyBody -> Condition -> Expression This is needed for testing expressions
	 * which are only allowed within a policy body and not in a target expression.
	 * 
	 * @param expression an expression
	 */
	@SuppressWarnings("unchecked")
	public void mockPolicyBodyForExpression(Expression expression) {
		var policyBody = FACTORY.createPolicyBody();
		var statementsFeature = policyBody.eClass().getEStructuralFeature("statements");
		var statementsFeatureInstance = (EList<Object>) policyBody.eGet(statementsFeature, true);
		var condition = FACTORY.createCondition();
		statementsFeatureInstance.add(condition);
		var expressionFeature = condition.eClass().getEStructuralFeature("expression");
		condition.eSet(expressionFeature, expression);
	}
}
