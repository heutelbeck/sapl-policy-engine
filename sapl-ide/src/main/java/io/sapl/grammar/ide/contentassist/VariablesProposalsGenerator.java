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
package io.sapl.grammar.ide.contentassist;

import static io.sapl.grammar.ide.contentassist.ExpressionSchemaResolver.offsetOf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.Schema;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VariablesProposalsGenerator {
    public static final Collection<String> AUTHORIZATION_SUBSCRIPTION_VARIABLES = Set.of("subject", "action",
            "resource", "environment");

    /**
     * Creates all possible variables proposals, including expansions based on
     * content or schema.
     *
     * @param context the ContentAssistContext
     * @param pdpConfiguration the configuration containing the environment
     * variables
     * @return a List of proposals.
     */
    public static List<String> variableProposalsForContext(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        /* first add environment variables and their expansions */
        final var proposals = createEnvironmentVariableProposals(pdpConfiguration);
        /*
         * Only add the following if not in schema definition, else no further variables
         * have been defined yet.
         */
        if (null == TreeNavigationUtil.goToFirstParent(context.getCurrentModel(), Schema.class)) {
            /*
             * The default variable names can always be valid identifier outside of the
             * schema definition.
             */
            proposals.addAll(AUTHORIZATION_SUBSCRIPTION_VARIABLES);
            proposals.addAll(createSubscriptionElementSchemaExpansionProposals(context, pdpConfiguration));
            proposals.addAll(createPolicySetHeaderVariablesProposals(context, pdpConfiguration));
            proposals.addAll(createPolicyBodyInScopeVariableProposals(context, pdpConfiguration));
            /*
             * Potential extension here: evaluate all variables and values excluding
             * attribute access (or maybe include resulting schema if available) and add
             * proposals on the calculated content.
             */
        }
        return proposals;
    }

    /**
     * Generate proposals for environment variables.
     *
     * @param pdpConfiguration configuration with environment variables
     * @return expanded proposals for environment variables
     */
    private static List<String> createEnvironmentVariableProposals(PDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<String>();
        pdpConfiguration.variables().entrySet().forEach(variableEntry -> {
            final var name = variableEntry.getKey();
            proposals.add(name);
            final var value = variableEntry.getValue();
            if (!value.isSecret()) {
                generatePathProposalsForValue(value).forEach(suffix -> proposals.add(name + "." + suffix));
            }
        });
        return proposals;
    }

    /*
     * Extracts all ValueDefinitions from a potential policy set and returns the
     * variable names as well as schema expansions.
     */
    private ArrayList<String> createPolicySetHeaderVariablesProposals(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<String>();
        if (context.getRootModel() instanceof SAPL sapl && sapl.getPolicyElement() instanceof PolicySet policySet) {
            for (var valueDefinition : policySet.getValueDefinitions()) {
                proposals.addAll(
                        createValueDefinitionProposalsWithSchemaExtensions(valueDefinition, pdpConfiguration, context));
            }
        }
        return proposals;
    }

    /*
     * Extracts all ValueDefinitions from the current policy body in order and stops
     * when it finds the statement where the cursor currently resides in. All
     * variable names on the way are returned.
     */
    private static List<String> createPolicyBodyInScopeVariableProposals(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var currentModel  = context.getCurrentModel();
        final var currentOffset = context.getOffset();
        final var policyBody    = TreeNavigationUtil.goToFirstParent(currentModel, PolicyBody.class);
        final var proposals     = new ArrayList<String>();

        if (null == policyBody) {
            return proposals;
        }

        for (var statement : policyBody.getStatements()) {
            if (offsetOf(statement) >= currentOffset) {
                break;
            }
            if (statement instanceof ValueDefinition valueDefinition) {
                proposals.addAll(
                        createValueDefinitionProposalsWithSchemaExtensions(valueDefinition, pdpConfiguration, context));
            }
        }
        return proposals;
    }

    /*
     * This method adds the variable name of the value and if the value definition
     * has an explicit schema declaration, the matching schema extensions are added.
     */
    private static List<String> createValueDefinitionProposalsWithSchemaExtensions(ValueDefinition valueDefinition,
            PDPConfiguration pdpConfiguration, ContentAssistContext context) {

        final var proposals    = new ArrayList<String>();
        final var variableName = valueDefinition.getName();
        if (Strings.isNullOrEmpty(variableName)) {
            return proposals;
        }
        proposals.add(variableName);
        final var schemas = ExpressionSchemaResolver.inferValueDefinitionSchemas(valueDefinition, context,
                pdpConfiguration);
        for (var schema : schemas) {
            proposals.addAll(
                    SchemaProposalsGenerator.getCodeTemplates(variableName, schema, pdpConfiguration.variables()));
        }
        return proposals;
    }

    private static List<String> createSubscriptionElementSchemaExpansionProposals(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<String>();
        if (context.getRootModel() instanceof SAPL sapl) {
            for (var schema : sapl.getSchemas()) {
                proposals.addAll(SchemaProposalsGenerator.getCodeTemplates(schema.getSubscriptionElement(),
                        schema.getSchemaExpression(), pdpConfiguration.variables()));
            }
        }
        return proposals;
    }

    private static List<String> generatePathProposalsForValue(Val value) {
        if (value.isUndefined() || value.isError()) {
            return List.of();
        }
        return generatePathProposalsForJsonNode(value.get());
    }

    private List<String> generatePathProposalsForJsonNode(JsonNode jsonNode) {
        if (jsonNode instanceof ObjectNode objectNode) {
            return generatePathProposalsForObjectNode(objectNode);
        } else if (jsonNode instanceof ArrayNode) {
            return List.of("[?]");
        } else {
            return List.of();
        }
    }

    private List<String> generatePathProposalsForObjectNode(ObjectNode objectNode) {
        final var proposals = new ArrayList<String>();
        objectNode.fields().forEachRemaining(entry -> {
            final var key = entry.getKey();
            proposals.add(key);
            generatePathProposalsForJsonNode(entry.getValue()).forEach(suffix -> {
                proposals.add(key + "." + suffix);
            });
        });
        return proposals;
    }

}
