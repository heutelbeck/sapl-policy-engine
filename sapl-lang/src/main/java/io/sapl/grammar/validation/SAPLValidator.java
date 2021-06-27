/**
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

import io.sapl.grammar.sapl.And;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Or;
import io.sapl.grammar.sapl.Policy;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.validation.Check;

/**
 * This class contains custom validation rules.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
@SuppressWarnings("all")
public class SAPLValidator extends AbstractSAPLValidator {
  protected static final String MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "Lazy and (&&) is not allowed, please use eager and (&) instead.";
  
  protected static final String MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "Lazy or (||) is not allowed, please use eager or (|) instead.";
  
  protected static final String MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "AttributeFinderStep is not allowed in target expression.";
  
  /**
   * According to SAPL documentation, no lazy And operators are allowed in the target expression.
   */
  @Check
  public void policyRuleNoAndAllowedInTargetExpression(final Policy policy) {
    this.<And>genericCheckForTargetExpression(policy, And.class, SAPLValidator.MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
  }
  
  /**
   * According to SAPL documentation, no lazy Or operators are allowed in the target expression.
   */
  @Check
  public void policyRuleNoOrAllowedInTargetExpression(final Policy policy) {
    this.<Or>genericCheckForTargetExpression(policy, Or.class, SAPLValidator.MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
  }
  
  /**
   * According to SAPL documentation, no lazy Or operators are allowed in the target expression.
   */
  @Check
  public void policyRuleNoAttributeFinderAllowedInTargetExpression(final Policy policy) {
    this.<AttributeFinderStep>genericCheckForTargetExpression(policy, AttributeFinderStep.class, SAPLValidator.MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
  }
  
  /**
   * looks for given class in the target expression of given Policy
   */
  public <T extends EObject> void genericCheckForTargetExpression(final Policy policy, final Class<T> aClass, final String message) {
    final T foundItem = this.<T>findClass(policy.getTargetExpression(), aClass);
    if ((foundItem != null)) {
      this.error(message, foundItem, null);
    }
  }
  
  /**
   * scan content of given EObject recursively
   */
  public <T extends EObject> T findClass(final EObject eObj, final Class<T> aClass) {
    boolean _isInstance = aClass.isInstance(eObj);
    if (_isInstance) {
      return ((T) eObj);
    }
    EList<EObject> _eContents = eObj.eContents();
    for (final EObject o : _eContents) {
      {
        T result = this.<T>findClass(o, aClass);
        if ((result != null)) {
          return result;
        }
      }
    }
    return null;
  }
}
