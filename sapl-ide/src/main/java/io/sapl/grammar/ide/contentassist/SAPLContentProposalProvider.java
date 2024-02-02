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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

import com.google.common.base.Strings;

import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
@Slf4j
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

    private static final Collection<String> BLACKLIST_OF_KEYWORD_PROPOSALS = Set.of("null", "undefined", "true",
            "false");
    private static final Collection<String> WHITELIST_OF_KEYWORD_PROPOSALS = Set.of("as");

    public static final Collection<String> AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS = Set.of("subject",
            "action", "resource", "environment");

    private static final int MINIMUM_KEYWORD_LENGTH = 3;

    private PDPConfigurationProvider pdpConfigurationProvider;

    private void lazyLoadDependencies() {
        if (pdpConfigurationProvider == null) {
            pdpConfigurationProvider = SpringContext.getBean(PDPConfigurationProvider.class);
        }
    }

    /**
     * Here SPAL filters out very short and blacklisted keywords.
     */
    @Override
    protected boolean filterKeyword(final Keyword keyword, final ContentAssistContext context) {
        var keywordValue = keyword.getValue();

        if (WHITELIST_OF_KEYWORD_PROPOSALS.contains(keywordValue))
            return true;

        if ((keywordValue.length() < MINIMUM_KEYWORD_LENGTH) || BLACKLIST_OF_KEYWORD_PROPOSALS.contains(keywordValue)) {
            return false;
        } else {
            return super.filterKeyword(keyword, context);
        }
    }

    /*
     * This method generates the domain specific recommendations for SAPL.
     */
    @Override
    protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        var parserRule       = GrammarUtil.containingParserRule(assignment);
        var parserRuleName   = parserRule.getName().toLowerCase();
        var feature          = assignment.getFeature().toLowerCase();
        var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration().blockFirst();

        log.trace("[" + parserRuleName + "->" + feature + "]");

        switch (parserRuleName) {
        case "import" -> {
            createImportProposals(feature, context, acceptor, pdpConfiguration);
            return;
        }
        case "schema" -> {
            createSchemaProposals(feature, context, acceptor, pdpConfiguration);
            return;
        }
        case "policy", "policyset" -> {
            createPolicyOrPolicySetNameStringProposals(feature, context, acceptor, pdpConfiguration);
            return;
        }
        case "basic" -> {
            createBasicProposals(feature, context, acceptor, pdpConfiguration);
            return;
        }
        case "step" -> {
            createIdStepProposals(context, acceptor, pdpConfiguration);
            return;
        }
        default -> {
            // NOOP Calling super would only introduce unwanted technical terms in proposals
        }
        }
    }

    /*
     * This method generates proposals for an initial identifier
     *
     * Should return: subscription variables, previously defined variable names in
     * the document, names of environment variables
     */
    private void createBasicIdentifierProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        // Simple names referencing environment variables. No schema available.
        createEnvironmentVariableProposals(context, acceptor, pdpConfiguration);
        createPolicySetHeaderVariablesProposals(context, acceptor, pdpConfiguration);
        createPolicyBodyInScopeVariableProposals(pdpConfiguration, context, acceptor);
        createProposalsContainingSubscriptionElementIdentifiers(context, acceptor);
        createSubscriptionElementSchemaExpansionProposals(context, acceptor, pdpConfiguration);

    }

    private void createEnvironmentVariableProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        addProposals(pdpConfiguration.variables().keySet(), context, acceptor);
    }

    private void createSubscriptionElementSchemaExpansionProposals(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if (context.getRootModel() instanceof SAPL sapl) {
            for (var schema : sapl.getSchemas()) {
                addProposals(SchemaProposalGenerator.getCodeTemplates(schema.getSubscriptionElement(),
                        schema.getSchemaExpression(), pdpConfiguration.variables()), context, acceptor);
            }
        }
    }

    /*
     * Extracts all ValueDefinitions from a potential policy set and returns the
     * variable names as well as schema expansions.
     */
    private void createPolicySetHeaderVariablesProposals(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if (context.getRootModel() instanceof SAPL sapl && sapl.getPolicyElement() instanceof PolicySet policySet) {
            for (var valueDefinition : policySet.getValueDefinitions()) {
                createValueDefinitionProposalsWithSchemaExtensions(valueDefinition, pdpConfiguration, context,
                        acceptor);
            }
        }
    }

    /*
     * Extracts all ValueDefinitions from the current policy body in order and stops
     * when it finds the statement where the cursor currently resides in. All
     * variable names on the way are returned.
     */
    private void createPolicyBodyInScopeVariableProposals(PDPConfiguration pdpConfiguration,
            ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        var currentModel  = context.getCurrentModel();
        var currentOffset = context.getOffset();
        var policyBody    = TreeNavigationUtil.goToFirstParent(currentModel, PolicyBody.class);

        if (policyBody == null)
            return;

        for (var statement : policyBody.getStatements()) {
            if (offsetOf(statement) >= currentOffset) {
                break;
            }
            if (statement instanceof ValueDefinition valueDefinition) {
                createValueDefinitionProposalsWithSchemaExtensions(valueDefinition, pdpConfiguration, context,
                        acceptor);
            }
        }
    }

    /*
     * This method adds the variable name of the value and if the value definition
     * has an explicit schema declaration, the matching schema extensions are added.
     */
    private void createValueDefinitionProposalsWithSchemaExtensions(ValueDefinition valueDefinition,
            PDPConfiguration pdpConfiguration, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        var variableName = valueDefinition.getName();
        if (Strings.isNullOrEmpty(variableName))
            return;
        addProposal(variableName, context, acceptor);
        var schemas = ExpressionSchemaResolver.inferValueDefinitionSchemas(valueDefinition, context, pdpConfiguration);
        for (var schema : schemas) {
            addProposals(SchemaProposalGenerator.getCodeTemplates(variableName, schema, pdpConfiguration.variables()),
                    context, acceptor);
        }
    }

    /*
     * Adds the fully qualified names and library names for attributes and functions
     * as completion options in import statements.
     */
    private void createImportProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if (!"libsteps".equals(feature))
            return;

        var attributeContext = pdpConfiguration.attributeContext();
        addProposals(attributeContext.getAllFullyQualifiedFunctions(), context, acceptor);
        addProposals(attributeContext.getAvailableLibraries(), context, acceptor);

        var functionContext = pdpConfiguration.functionContext();
        addProposals(functionContext.getAllFullyQualifiedFunctions(), context, acceptor);
        addProposals(functionContext.getAvailableLibraries(), context, acceptor);
    }

    /*
     * Proposals for the schema definitions in the document
     */
    private void createSchemaProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if ("subscriptionelement".equals(feature)) {
            createProposalsContainingSubscriptionElementIdentifiers(context, acceptor);
            return;
        } else if ("schemaexpression".equals(feature)) {
            createEnvironmentVariableProposals(context, acceptor, pdpConfiguration);
        }
    }

    private void createBasicProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if ("identifier".equals(feature)) {
            createBasicIdentifierProposals(feature, context, acceptor, pdpConfiguration);
        } else if ("idsteps".equals(feature) || "steps".equals(feature)) {
            createBasicIdentifierProposals(feature, context, acceptor, pdpConfiguration);
            createIdStepProposals(context, acceptor, pdpConfiguration);
        } else if ("fsteps".equals(feature)) {
            createFStepsProposals(context, acceptor, pdpConfiguration);
            createIdStepProposals(context, acceptor, pdpConfiguration);
        }
    }

    private void createProposalsContainingSubscriptionElementIdentifiers(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        addProposals(AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS, context, acceptor);
    }

    /*
     * fsteps are the name fragments of attributes
     */
    private void createIdStepProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {

        var attributeContext    = pdpConfiguration.attributeContext();
        var documentedTemplates = attributeContext.getDocumentedAttributeCodeTemplates();
        for (var documentedTemplate : documentedTemplates.entrySet()) {
            var template = documentedTemplate.getKey();
            var documentation      = documentedTemplate.getValue();
            var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
            createAttributeProposals(fullyQualifiedName, template, documentation, context, acceptor, pdpConfiguration);
        }
    }

    private void createAttributeProposals(String fullyQualifiedName, String template, String documentation,
            ContentAssistContext context, IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        var attributeContext = pdpConfiguration.attributeContext();
        var schemas          = attributeContext.getAttributeSchemas();
        var proposals        = proposalsWithImportsForTemplate(template, context, acceptor);
        proposals.add(template);
        addProposalsWithSharedDocumentation(proposals, documentation, context, acceptor);
        var schema = schemas.get(fullyQualifiedName);
        if (schema != null) {
            for (var prefix : proposals) {
                var extendedProposals = SchemaProposalGenerator.getCodeTemplates(prefix, schema,
                        pdpConfiguration.variables());
                addProposals(extendedProposals, context, acceptor);
            }
        }
    }

    /*
     * fsteps are the name fragments of function names
     */
    private void createFStepsProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        var functionContext     = pdpConfiguration.functionContext();
        var documentedTemplates = functionContext.getDocumentedCodeTemplates();
        for (var documentedTemplate : documentedTemplates.entrySet()) {
            var template           = documentedTemplate.getKey();
            var documentation      = documentedTemplate.getValue();
            var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
            createFunctionProposals(fullyQualifiedName, template, documentation, context, acceptor, pdpConfiguration);
        }
    }

    private void createFunctionProposals(String fullyQualifiedName, String template, String documentation,
            ContentAssistContext context, IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        var functionContext = pdpConfiguration.functionContext();
        var schemas         = functionContext.getFunctionSchemas();
        var proposals       = proposalsWithImportsForTemplate(template, context, acceptor);
        proposals.add(template);
        addProposalsWithSharedDocumentation(proposals, documentation, context, acceptor);
        var schema = schemas.get(fullyQualifiedName);
        if (schema != null) {
            for (var prefix : proposals) {
                var extendedProposals = SchemaProposalGenerator.getCodeTemplates(prefix, schema,
                        pdpConfiguration.variables());
                addProposals(extendedProposals, context, acceptor);
            }
        }
    }

    /**
     * This method strips < and > characters and removes a potential parameter list
     * in brackets from the tail end of a generated template.
     *
     * This is explicitly not using String.replace as there is no need for regular
     * expression processing and this is much more efficient.
     *
     * @param template a code template
     * @return the fully qualified function name in the template
     */
    private static String fullyQualifiedNameFromTemplate(String template) {
        var sb = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            var c = template.charAt(i);
            if (c == '(')
                break;
            if (template.charAt(i) != '<' && template.charAt(i) != '>') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Collection<String> proposalsWithImportsForTemplate(String template, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var proposals = new ArrayList<String>();
        if (context.getRootModel() instanceof SAPL sapl) {
            var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (var anImport : imports) {
                if (anImport instanceof WildcardImport wildcardImport) {
                    proposalsWithWildcard(wildcardImport, template, context, acceptor).ifPresent(i -> proposals.add(i));
                } else if (anImport instanceof LibraryImport libraryImport) {
                    proposalsWithLibraryImport(libraryImport, template, context, acceptor)
                            .ifPresent(i -> proposals.add(i));
                } else {
                    proposalsWithImport(anImport, template, context, acceptor).ifPresent(i -> proposals.add(i));
                }
            }
        }
        return proposals;
    }

    private Optional<String> proposalsWithImport(Import anImport, String template, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var steps              = anImport.getLibSteps();
        var functionName       = anImport.getFunctionName();
        var prefix             = joinStepsToPrefix(steps) + functionName;
        var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);    
        if (fullyQualifiedName.startsWith(prefix))
            return Optional.of(template.replaceFirst(prefix, functionName));
        else
            return Optional.empty();
    }

    private Optional<String> proposalsWithWildcard(WildcardImport wildCard, String template,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
        var prefix             = joinStepsToPrefix(wildCard.getLibSteps());
        var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
        if (fullyQualifiedName.startsWith(prefix))
            return Optional.of(template.replaceFirst(prefix, ""));
        else
            return Optional.empty();
    }

    private String joinStepsToPrefix(EList<String> steps) {
        return joinStepsToName(steps) + '.';
    }

    private String joinStepsToName(EList<String> steps) {
        return String.join(".", steps);
    }

    private Optional<String> proposalsWithLibraryImport(LibraryImport libImport, String template,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
        var shortPrefix        = String.join(".", libImport.getLibSteps());
        var prefix             = shortPrefix + '.';
        var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
        if (fullyQualifiedName.startsWith(prefix))
            return Optional.of(template.replaceFirst(shortPrefix, libImport.getLibAlias()));
        else
            return Optional.empty();
    }

    /*
     * Only offers to add a blank string for adding a name.
     */
    private void createPolicyOrPolicySetNameStringProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if (!"saplname".equals(feature))
            return;

        var entry = getProposalCreator().createProposal("\"\"", context);
        entry.setKind(ContentAssistEntry.KIND_TEXT);
        entry.setDescription("policy name");
        acceptor.accept(entry, 0);
    }

    private void addProposals(final Collection<String> proposals, ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(proposal -> addProposal(proposal, context, acceptor));
    }

    private void addProposal(final String proposal, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        log.trace("- '" + proposal + "'");
        var contextWithCorrectedPrefix = ContextUtil.getContextWithFullPrefix(context, proposal.contains(">"));
        var entry                      = getProposalCreator().createProposal(proposal, contextWithCorrectedPrefix);
        acceptor.accept(entry, 0);
    }

    private void addProposalsWithSharedDocumentation(final Collection<String> proposals, String sharedDocumentation,
            ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(proposal -> addProposalWithDocumentation(proposal, sharedDocumentation, context, acceptor));
    }

    private void addProposalWithDocumentation(final String proposal, final String documentation,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
        log.trace("- '" + proposal + "' doc: '" + documentation + "'");
        var contextWithCorrectedPrefix = ContextUtil.getContextWithFullPrefix(context, proposal.contains(">"));
        var entry                      = getProposalCreator().createProposal(proposal, contextWithCorrectedPrefix);
        if (entry != null && documentation != null)
            entry.setDocumentation(documentation);
        acceptor.accept(entry, 0);
    }

}
