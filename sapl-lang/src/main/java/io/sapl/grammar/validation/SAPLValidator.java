/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.validation;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.validation.Check;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SaplPackage;

/**
 * This class contains custom validation rules.
 *
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
public class SAPLValidator extends AbstractSAPLValidator {

	protected static final String MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "Lazy and (&&) is not allowed, please use eager and (&) instead.";

	protected static final String MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "Lazy or (||) is not allowed, please use eager or (|) instead.";

	protected static final String MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "AttributeFinderStep is not allowed in target expression.";

	protected static final String MSG_HAFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "HeadAttributeFinderStep is not allowed in target expression.";

	protected static final String MSG_BEA_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "BasicEnvironmentAttribute is not allowed in target expression.";

	protected static final String MSG_BEHA_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "BasicEnvironmentHeadAttribute is not allowed in target expression.";

	/**
	 * No lazy And operators are allowed in the target expression.
	 * @param policy a policy
	 */
	@Check
	public void policyRuleNoAndAllowedInTargetExpression(final Policy policy) {
		genericCheckForTargetExpression(policy, SaplPackage.Literals.AND,
				SAPLValidator.MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	/**
	 * No lazy Or operators are allowed in the target expression.
	 * @param policy a policy
	 */
	@Check
	public void policyRuleNoOrAllowedInTargetExpression(final Policy policy) {
		genericCheckForTargetExpression(policy, SaplPackage.Literals.OR,
				SAPLValidator.MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	/**
	 * No lazy Or operators are allowed in the target expression.
	 * @param policy a policy
	 */
	@Check
	public void policyRuleNoAttributeFinderAllowedInTargetExpression(final Policy policy) {
		genericCheckForTargetExpression(policy, SaplPackage.Literals.ATTRIBUTE_FINDER_STEP,
				SAPLValidator.MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Check
	public void policyRuleNoHeaderAttributeFinderAllowedInTargetExpression(final Policy policy) {
		genericCheckForTargetExpression(policy, SaplPackage.Literals.HEAD_ATTRIBUTE_FINDER_STEP,
				SAPLValidator.MSG_HAFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Check
	public void policyRuleNoBasicEnvironmentAttributeAllowedInTargetExpression(final Policy policy) {
		genericCheckForTargetExpression(policy, SaplPackage.Literals.BASIC_ENVIRONMENT_ATTRIBUTE,
				SAPLValidator.MSG_BEA_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Check
	public void policyRuleNoBasicHeadEnvironmentAttributeAllowedInTargetExpression(final Policy policy) {
		genericCheckForTargetExpression(policy, SaplPackage.Literals.BASIC_ENVIRONMENT_HEAD_ATTRIBUTE,
				SAPLValidator.MSG_BEHA_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	/**
	 * looks for given class in the target expression of given Policy
	 * @param policy a policy
	 * @param aClass class to look up
	 * @param message an error message
	 */
	public void genericCheckForTargetExpression(final Policy policy, final EClass aClass, final String message) {
		var foundItem = containsClass(policy.getTargetExpression(), aClass);
		if (foundItem != null) {
			error(message, foundItem, null);
		}
	}

	/**
	 * scan content of given EObject recursively
	 * @param eObj object to search through
	 * @param eClass class to look up
	 * @return discovered object or null
	 */
	public EObject containsClass(final EObject eObj, final EClass eClass) {
		if (eClass.isSuperTypeOf(eObj.eClass()))
			return eObj;

		for (var o : eObj.eContents()) {
			var result = containsClass(o, eClass);
			if (result != null)
				return result;
		}

		return null;
	}

}
