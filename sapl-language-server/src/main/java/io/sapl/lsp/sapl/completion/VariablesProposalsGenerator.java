/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapl.completion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.SchemaStatementContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.util.TreeNavigationUtil;
import lombok.experimental.UtilityClass;

/**
 * Generates variable completion proposals for SAPL documents.
 * This includes:
 * - Authorization subscription variables (subject, action, resource,
 * environment)
 * - Environment variables from configuration
 * - Value definitions from policy set headers
 * - Value definitions from policy bodies (scope-aware)
 * - Schema expansions for all of the above
 */
@UtilityClass
public class VariablesProposalsGenerator {

    /**
     * Authorization subscription variable names.
     */
    public static final Collection<String> AUTHORIZATION_SUBSCRIPTION_VARIABLES = Set.of("subject", "action",
            "resource", "environment");

    /**
     * Creates all possible variable proposals, including expansions based on
     * content or schema.
     *
     * @param sapl the root SAPL parse tree
     * @param cursorOffset the current cursor offset for scope filtering
     * @param config the LSP configuration containing environment variables
     * @param inSchemaContext true if cursor is within a schema definition
     * @return list of variable proposal strings
     */
    public static List<String> variableProposalsForContext(SaplContext sapl, int cursorOffset, LSPConfiguration config,
            boolean inSchemaContext) {
        if (sapl == null) {
            return new ArrayList<>();
        }

        // First add environment variables and their expansions
        var proposals = createEnvironmentVariableProposals(config);

        // Only add the following if not in schema definition, else no further variables
        // have been defined yet.
        if (!inSchemaContext) {
            // The default variable names can always be valid identifiers outside the
            // schema definition.
            proposals.addAll(AUTHORIZATION_SUBSCRIPTION_VARIABLES);
            proposals.addAll(createSubscriptionElementSchemaExpansionProposals(sapl, config));
            proposals.addAll(createPolicySetHeaderVariablesProposals(sapl, cursorOffset, config));
            proposals.addAll(createPolicyBodyInScopeVariableProposals(sapl, cursorOffset, config));
        }

        return proposals;
    }

    /**
     * Generate proposals for environment variables.
     *
     * @param config configuration with environment variables
     * @return expanded proposals for environment variables
     */
    private static List<String> createEnvironmentVariableProposals(LSPConfiguration config) {
        var proposals = new ArrayList<String>();
        config.variables().forEach((name, value) -> {
            proposals.add(name);
            if (!value.isSecret()) {
                generatePathProposalsForValue(value).forEach(suffix -> proposals.add(name + "." + suffix));
            }
        });
        return proposals;
    }

    /**
     * Extracts all ValueDefinitions from a potential policy set and returns the
     * variable names as well as schema expansions.
     */
    private static List<String> createPolicySetHeaderVariablesProposals(SaplContext sapl, int cursorOffset,
            LSPConfiguration config) {
        var proposals = new ArrayList<String>();

        var policyElement = sapl.policyElement();
        if (policyElement instanceof PolicySetElementContext policySetElement) {
            var policySet = policySetElement.policySet();
            for (var valueDefinition : policySet.valueDefinition()) {
                if (isInScope(valueDefinition, cursorOffset)) {
                    proposals.addAll(createValueDefinitionProposalsWithSchemaExtensions(valueDefinition, sapl,
                            cursorOffset, config));
                }
            }
        }

        return proposals;
    }

    /**
     * Extracts all ValueDefinitions from the current policy body in order and stops
     * when it finds the statement where the cursor currently resides in. All
     * variable names on the way are returned.
     */
    private static List<String> createPolicyBodyInScopeVariableProposals(SaplContext sapl, int cursorOffset,
            LSPConfiguration config) {
        var proposals = new ArrayList<String>();

        var policyElement = sapl.policyElement();
        if (policyElement == null) {
            return proposals;
        }

        List<PolicyBodyContext> policyBodies = new ArrayList<>();

        if (policyElement instanceof PolicyOnlyElementContext policyOnlyElement) {
            var policy = policyOnlyElement.policy();
            if (policy.policyBody() != null) {
                policyBodies.add(policy.policyBody());
            }
        } else if (policyElement instanceof PolicySetElementContext policySetElement) {
            for (var policy : policySetElement.policySet().policy()) {
                if (policy.policyBody() != null) {
                    policyBodies.add(policy.policyBody());
                }
            }
        }

        for (var policyBody : policyBodies) {
            for (var statement : policyBody.statement()) {
                var statementOffset = TreeNavigationUtil.offsetOf(statement);
                if (statementOffset >= cursorOffset) {
                    break;
                }
                if (statement instanceof ValueDefinitionStatementContext valueDefStatement) {
                    var valueDefinition = valueDefStatement.valueDefinition();
                    proposals.addAll(createValueDefinitionProposalsWithSchemaExtensions(valueDefinition, sapl,
                            cursorOffset, config));
                }
            }
        }

        return proposals;
    }

    /**
     * This method adds the variable name of the value and if the value definition
     * has an explicit schema declaration, the matching schema extensions are added.
     */
    private static List<String> createValueDefinitionProposalsWithSchemaExtensions(
            ValueDefinitionContext valueDefinition, SaplContext sapl, int cursorOffset, LSPConfiguration config) {
        var proposals    = new ArrayList<String>();
        var variableName = valueDefinition.name != null ? valueDefinition.name.getText() : null;

        if (variableName == null || variableName.isBlank()) {
            return proposals;
        }

        proposals.add(variableName);

        var schemas = ExpressionSchemaResolver.inferValueDefinitionSchemas(valueDefinition, sapl, cursorOffset, config);
        for (var schema : schemas) {
            proposals.addAll(SchemaProposalsGenerator.getCodeTemplates(variableName, schema, config.variables()));
        }

        return proposals;
    }

    private static List<String> createSubscriptionElementSchemaExpansionProposals(SaplContext sapl,
            LSPConfiguration config) {
        var proposals = new ArrayList<String>();

        for (var schemaStatement : sapl.schemaStatement()) {
            var subscriptionElement = getSubscriptionElementName(schemaStatement);
            if (subscriptionElement != null && !subscriptionElement.isBlank()) {
                var schemaExpression = schemaStatement.schemaExpression;
                proposals.addAll(
                        SchemaProposalsGenerator.getCodeTemplates(subscriptionElement, schemaExpression, config));
            }
        }

        return proposals;
    }

    private static String getSubscriptionElementName(SchemaStatementContext schemaStatement) {
        if (schemaStatement.subscriptionElement == null) {
            return null;
        }
        return schemaStatement.subscriptionElement.getText();
    }

    private static boolean isInScope(ValueDefinitionContext definition, int cursorOffset) {
        return cursorOffset > TreeNavigationUtil.offsetOf(definition);
    }

    private static List<String> generatePathProposalsForValue(Value value) {
        if (value instanceof ObjectValue objectValue) {
            return generatePathProposalsForObjectValue(objectValue);
        } else if (value instanceof ArrayValue) {
            return List.of("[?]");
        } else {
            return List.of();
        }
    }

    private static List<String> generatePathProposalsForObjectValue(ObjectValue objectValue) {
        var proposals = new ArrayList<String>();
        objectValue.forEach((key, value) -> {
            proposals.add(key);
            generatePathProposalsForValue(value).forEach(suffix -> proposals.add(key + "." + suffix));
        });
        return proposals;
    }

}
