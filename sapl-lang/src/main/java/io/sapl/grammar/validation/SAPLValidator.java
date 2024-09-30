/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.validation.Check;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.Schema;
import io.sapl.grammar.sapl.ValueDefinition;

/**
 * This class contains custom validation rules.
 * <p>
 * See <a href=
 * "https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation">Validation</a>
 */
public class SAPLValidator extends AbstractSAPLValidator {

    protected static final String MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION     = "Lazy AND (&&) is not allowed in target expressions, please use eager and (&) instead.";
    protected static final String MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION      = "Lazy OR (||) is not allowed in target expressions, please use eager or (|) instead.";
    protected static final String MSG_ATTRIBUTES_NOT_ALLOWED_IN_TARGET_EXPRESSION = "Attribute access is forbidden in target expression.";
    protected static final String MSG_ATTRIBUTES_NOT_ALLOWED_IN_SCHEMA_EXPRESSION = "Attribute access is forbidden in schema expression.";

    // @formatter:off
    protected static final Map<EClass,String> BLOCKED_TARGET_EXPRESSION_ELEMENTS = Map.of(
                SaplPackage.Literals.OR,                               MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION,
                SaplPackage.Literals.AND,                              MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION,
                SaplPackage.Literals.ATTRIBUTE_FINDER_STEP,            MSG_ATTRIBUTES_NOT_ALLOWED_IN_TARGET_EXPRESSION,
                SaplPackage.Literals.HEAD_ATTRIBUTE_FINDER_STEP,       MSG_ATTRIBUTES_NOT_ALLOWED_IN_TARGET_EXPRESSION,
                SaplPackage.Literals.BASIC_ENVIRONMENT_ATTRIBUTE,      MSG_ATTRIBUTES_NOT_ALLOWED_IN_TARGET_EXPRESSION,
                SaplPackage.Literals.BASIC_ENVIRONMENT_HEAD_ATTRIBUTE, MSG_ATTRIBUTES_NOT_ALLOWED_IN_TARGET_EXPRESSION
            );
    // @formatter:on

    // @formatter:off
    protected static final Map<EClass,String> BLOCKED_SCHEMA_EXPRESSION_ELEMENTS = Map.of(
                SaplPackage.Literals.ATTRIBUTE_FINDER_STEP,            MSG_ATTRIBUTES_NOT_ALLOWED_IN_SCHEMA_EXPRESSION,
                SaplPackage.Literals.HEAD_ATTRIBUTE_FINDER_STEP,       MSG_ATTRIBUTES_NOT_ALLOWED_IN_SCHEMA_EXPRESSION,
                SaplPackage.Literals.BASIC_ENVIRONMENT_ATTRIBUTE,      MSG_ATTRIBUTES_NOT_ALLOWED_IN_SCHEMA_EXPRESSION,
                SaplPackage.Literals.BASIC_ENVIRONMENT_HEAD_ATTRIBUTE, MSG_ATTRIBUTES_NOT_ALLOWED_IN_SCHEMA_EXPRESSION
            );
    // @formatter:on

    /**
     * Eager Boolean logic and attributes forbidden in target expressions
     *
     * @param policy a policy
     */
    @Check
    public void policyRuleNoAndAllowedInTargetExpression(final Policy policy) {
        for (var blacklistEntry : BLOCKED_TARGET_EXPRESSION_ELEMENTS.entrySet()) {
            genericCheckForElementInAST(policy.getTargetExpression(), blacklistEntry.getKey(),
                    blacklistEntry.getValue());
        }
    }

    /**
     * Eager Boolean logic and attributes forbidden in target expressions
     *
     * @param policySet a policy set
     */
    @Check
    public void policyRuleNoAndAllowedInTargetExpression(final PolicySet policySet) {
        for (var blacklistEntry : BLOCKED_TARGET_EXPRESSION_ELEMENTS.entrySet()) {
            genericCheckForElementInAST(policySet.getTargetExpression(), blacklistEntry.getKey(),
                    blacklistEntry.getValue());
        }
    }

    /**
     * Attributes forbidden in schema expressions
     *
     * @param schema a schema statement
     */
    @Check
    public void policyRuleNoAndAllowedInTargetExpression(final Schema schema) {
        for (var blacklistEntry : BLOCKED_SCHEMA_EXPRESSION_ELEMENTS.entrySet()) {
            genericCheckForElementInAST(schema.getSchemaExpression(), blacklistEntry.getKey(),
                    blacklistEntry.getValue());
        }
    }

    /**
     * Attributes forbidden in schema expressions
     *
     * @param valueDefinition a value definition statement
     */
    @Check
    public void policyRuleNoAndAllowedInTargetExpression(final ValueDefinition valueDefinition) {
        for (var blacklistEntry : BLOCKED_SCHEMA_EXPRESSION_ELEMENTS.entrySet()) {
            for (var schemaExpression : valueDefinition.getSchemaVarExpression()) {
                genericCheckForElementInAST(schemaExpression, blacklistEntry.getKey(), blacklistEntry.getValue());
            }
        }
    }

    /**
     * looks for given class in a subtree of the AST
     *
     * @param startNode start node
     * @param aClass forbidden type
     * @param message an error message
     */
    public void genericCheckForElementInAST(final EObject startNode, final EClass aClass, final String message) {
        var foundItem = containsClass(startNode, aClass);
        if (foundItem != null) {
            error(message, foundItem, null);
        }
    }

    /**
     * scan content of given EObject recursively
     *
     * @param eObj object to search through
     * @param eClass class to look up
     * @return discovered object or null
     */
    public EObject containsClass(final EObject eObj, final EClass eClass) {
        if (eObj == null)
            return null;

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
