package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.common.util.EList;

import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import lombok.experimental.UtilityClass;

/**
 * Some helper methods for unit tests.
 */
@UtilityClass
public class MockUtil {
	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

	public void mockPolicyTargetExpressionContainerExpression(Expression expression) {
		var policy = FACTORY.createPolicy();
		var targetExpressionFeature = policy.eClass().getEStructuralFeature("targetExpression");
		policy.eSet(targetExpressionFeature, expression);
	}

	public void mockPolicySetTargetExpressionContainerExpression(Expression expression) {
		var policySet = FACTORY.createPolicySet();
		var targetExpressionFeature = policySet.eClass().getEStructuralFeature("targetExpression");
		policySet.eSet(targetExpressionFeature, expression);
	}

	@SuppressWarnings("unchecked")
	public void mockPolicyTargetExpressionContainerExpressionForAttributeFinderStep(AttributeFinderStep expression) {
		var basicIdentifier = FACTORY.createBasicIdentifier();
		mockPolicyTargetExpressionContainerExpression(basicIdentifier);
		var stepsFeature = basicIdentifier.eClass().getEStructuralFeature("steps");
		var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
		stepsInstance.add(expression);
	}

	@SuppressWarnings("unchecked")
	public void mockPolicySetTargetExpressionContainerExpressionForAttributeFinderStep(AttributeFinderStep expression) {
		var basicIdentifier = FACTORY.createBasicIdentifier();
		mockPolicySetTargetExpressionContainerExpression(basicIdentifier);
		var stepsFeature = basicIdentifier.eClass().getEStructuralFeature("steps");
		var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
		stepsInstance.add(expression);
	}

}
