/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.google.common.base.Strings;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.grammar.ide.contentassist.ContextAnalyzer.ContextAnalysisResult;
import io.sapl.grammar.ide.contentassist.ProposalCreator.Proposal;
import io.sapl.grammar.sapl.*;
import lombok.experimental.UtilityClass;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.sapl.grammar.ide.contentassist.ExpressionSchemaResolver.offsetOf;

@UtilityClass
public class VariablesProposalsGenerator {
    public static final Collection<String> AUTHORIZATION_SUBSCRIPTION_VARIABLES = Set.of("subject", "action",
            "resource", "environment");

    /**
     * Creates all possible variables proposals, including expansions based on
     * content or schema.
     *
     * @param analysis only add proposals starting with this prefix, but remove
     * prefix from proposal.
     * @param context the ContentAssistContext
     * @param pdpConfiguration the configuration containing the environment
     * variables
     * @return a List of proposals.
     */
    public static List<Proposal> variableProposalsForContext(ContextAnalysisResult analysis,
            ContentAssistContext context, ContentAssistPDPConfiguration pdpConfiguration) {
        /* first add environment variables and their expansions */
        final var proposals = createEnvironmentVariableProposals(pdpConfiguration);
        /*
         * Only add the following if not in schema definition, else no further variables
         * have been defined yet.
         */
        if (null == TreeNavigationUtil.goToFirstParent(context.getCurrentModel(), Schema.class)) {
            /*
             * The default variable names can always be valid identifier outside the
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

        return toListOfNormalizedEntries(proposals, analysis);
    }

    private List<Proposal> toListOfNormalizedEntries(Collection<String> proposals, ContextAnalysisResult analysis) {
        final var entries = new ArrayList<Proposal>(proposals.size());
        proposals.forEach(proposal -> ProposalCreator
                .createNormalizedEntry(proposal, analysis.prefix(), analysis.ctxPrefix()).ifPresent(entries::add));
        return entries;
    }

    /**
     * Generate proposals for environment variables.
     *
     * @param pdpConfiguration configuration with environment variables
     * @return expanded proposals for environment variables
     */
    private static List<String> createEnvironmentVariableProposals(ContentAssistPDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<String>();
        pdpConfiguration.variables().forEach((name, value) -> {
            proposals.add(name);
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
            ContentAssistPDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<String>();
        if (context.getRootModel() instanceof final SAPL sapl
                && sapl.getPolicyElement() instanceof final PolicySet policySet) {
            for (final var valueDefinition : policySet.getValueDefinitions()) {
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
            ContentAssistPDPConfiguration pdpConfiguration) {
        final var currentModel  = context.getCurrentModel();
        final var currentOffset = context.getOffset();
        final var policyBody    = TreeNavigationUtil.goToFirstParent(currentModel, PolicyBody.class);
        final var proposals     = new ArrayList<String>();

        if (null == policyBody) {
            return proposals;
        }

        for (final var statement : policyBody.getStatements()) {
            if (offsetOf(statement) >= currentOffset) {
                break;
            }
            if (statement instanceof final ValueDefinition valueDefinition) {
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
            ContentAssistPDPConfiguration pdpConfiguration, ContentAssistContext context) {

        final var proposals    = new ArrayList<String>();
        final var variableName = valueDefinition.getName();
        if (Strings.isNullOrEmpty(variableName)) {
            return proposals;
        }
        proposals.add(variableName);
        final var schemas = ExpressionSchemaResolver.inferValueDefinitionSchemas(valueDefinition, context,
                pdpConfiguration);
        for (final var schema : schemas) {
            proposals.addAll(
                    SchemaProposalsGenerator.getCodeTemplates(variableName, schema, pdpConfiguration.variables()));
        }
        return proposals;
    }

    private static List<String> createSubscriptionElementSchemaExpansionProposals(ContentAssistContext context,
            ContentAssistPDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<String>();
        if (context.getRootModel() instanceof final SAPL sapl) {
            for (final var schema : sapl.getSchemas()) {
                proposals.addAll(SchemaProposalsGenerator.getCodeTemplates(schema.getSubscriptionElement(),
                        schema.getSchemaExpression(), pdpConfiguration));
            }
        }
        return proposals;
    }

    private List<String> generatePathProposalsForValue(Value value) {
        if (value instanceof ObjectValue objectNode) {
            return generatePathProposalsForObjectValue(objectNode);
        } else if (value instanceof ArrayValue) {
            return List.of("[?]");
        } else {
            return List.of();
        }
    }

    private List<String> generatePathProposalsForObjectValue(ObjectValue objectValue) {
        final var proposals = new ArrayList<String>();
        objectValue.forEach((key, value) -> {
            proposals.add(key);
            generatePathProposalsForValue(value).forEach(suffix -> proposals.add(key + "." + suffix));
        });
        return proposals;
    }

}
