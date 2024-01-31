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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
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
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

    private static final Collection<String> BLACKLIST_OF_KEYWORD_PROPOSALS = Set.of("null", "undefined", "true",
            "false");
    private static final Collection<String> WHITELIST_OF_KEYWORD_PROPOSALS = Set.of("as");

    private static final Collection<String> AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS = Set.of("subject",
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
     * This method is responsible for creating proposals based on grammar keywords.
     * XText calls it for any potential keyword to be used.
     */
    @Override
    protected void _createProposals(Keyword keyword, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        // NOOP just call super. Filtering is done in filterKeywords.
        super._createProposals(keyword, context, acceptor);
    }

    /*
     * This method generates the more domain specific recommendations for SAPL.
     */
    @Override
    protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        var parserRule       = GrammarUtil.containingParserRule(assignment);
        var parserRuleName   = parserRule.getName().toLowerCase();
        var feature          = assignment.getFeature().toLowerCase();
        var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration().blockFirst();

        System.out.println("Request for proposals:");
        System.out.println("parserRuleName: " + parserRuleName);
        System.out.println("feature       : " + feature);

        switch (parserRuleName) {
        case "import" -> {
            // TODO: OK!
            handleImportProposals(feature, context, acceptor, pdpConfiguration);
            break;
        }
        case "schema" -> {
            // TODO: OK!
            handleSchemaProposals(feature, context, acceptor, pdpConfiguration);
            break;
        }
        case "basic" -> {
            handleBasicProposals(feature, context, acceptor, pdpConfiguration);
            break;
        }
        case "policy", "policyset" -> {
            // TODO: OK!
            handlePolicyOrPolicySetProposals(feature, context, acceptor, pdpConfiguration);
            break;
        }
        case "step" -> {
            handleStepProposals(feature, context, acceptor, pdpConfiguration);
            break;
        }
        case "numberliteral", "stringliteral" -> {
            // TODO: OK!
            break;
        }
        default -> {
            // NOOP
        }
        }

        super._createProposals(assignment, context, acceptor);
    }

    /*
     * This method generates proposals for an initial identifier
     * 
     * Should return: subscription variables, previously defined variable names in
     * the document, names of environment variables
     */
    private void handleBasicIdentifierProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        // TODO: OK!
        // Simple names referencing environment variables. No schema available.
        addSimpleProposals(pdpConfiguration.variables().keySet(), context, acceptor, pdpConfiguration);

        // Add schema-based proposals, if schemas are available for all following
        // proposals
        addSimpleProposals(AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS, context, acceptor, pdpConfiguration);
        addSimpleProposals(subscriptionElementSchemaExpansionProposals(context.getRootModel(), pdpConfiguration),
                context, acceptor, pdpConfiguration);
        addSimpleProposals(findVariableNamesInSetHeader(context.getRootModel(), pdpConfiguration), context, acceptor,
                pdpConfiguration);
        addSimpleProposals(findVariablesInStatementsBeforeCurrentOnesInPolicyBody(context, pdpConfiguration), context,
                acceptor, pdpConfiguration);
    }

    private static Collection<String> subscriptionElementSchemaExpansionProposals(EObject root,
            PDPConfiguration pdpConfiguration) {
        var proposals = new ArrayList<String>();
        if (root instanceof SAPL sapl) {
            for (var schema : sapl.getSchemas()) {
                proposals.addAll(SchemaProposalGenerator.getCodeTemplates(schema.getSubscriptionElement(),
                        schema.getSchemaExpression(), pdpConfiguration.variables()));
            }
        }
        return proposals;
    }

    /*
     * Extracts all ValueDefinitions from a potential policy set and returns the
     * variable names as well as schema expansions.
     */
    private static Collection<String> findVariableNamesInSetHeader(EObject root, PDPConfiguration pdpConfiguration) {
        var variableNames = new ArrayList<String>();
        if (root instanceof SAPL sapl && sapl.getPolicyElement() instanceof PolicySet policySet) {
            for (var valueDefinition : policySet.getValueDefinitions()) {
                addVariableNameAndSchemaExtensionToProposals(valueDefinition, variableNames, pdpConfiguration);
            }
        }
        return variableNames;
    }

    /*
     * Extracts all ValueDefinitions from the current policy body in order and stops
     * when it finds the statement where the cursor currently resides in. All
     * variable names on the way are returned.
     * 
     * Known issue: if proposals are triggered on an empty line variables defined in
     * an statement after the cursor may be proposed. I have not found out how to
     * get the last statement before the cursor to create a matching stopping
     * condition.
     */
    private static Collection<String> findVariablesInStatementsBeforeCurrentOnesInPolicyBody(
            ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        var proposals        = new ArrayList<String>();
        var current          = context.getCurrentModel();
        var currentStatement = TreeNavigationHelper.goToFirstParent(current, Statement.class);
        var policyBody       = TreeNavigationHelper.goToFirstParent(current, PolicyBody.class);

        if (policyBody == null)
            return proposals;

        for (var statement : policyBody.getStatements()) {
            if (statement == currentStatement) {
                break;
            }
            if (statement instanceof ValueDefinition valueDefinition) {
                addVariableNameAndSchemaExtensionToProposals(valueDefinition, proposals, pdpConfiguration);
            }
        }
        return proposals;
    }

    private static void addVariableNameAndSchemaExtensionToProposals(ValueDefinition valueDefinition,
            Collection<String> proposals, PDPConfiguration pdpConfiguration) {
        var variableName = valueDefinition.getName();
        if (!Strings.isNullOrEmpty(variableName)) {
            proposals.add(variableName);
            for (var schemaExpression : valueDefinition.getSchemaVarExpression()) {
                proposals.addAll(SchemaProposalGenerator.getCodeTemplates(variableName, schemaExpression,
                        pdpConfiguration.variables()));
            }
        }
    }

    private void handleStepProposals(String feature, ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        if ("idsteps".equals(feature))
            addProposalsForAttributeStepsIfPresent(context, acceptor, pdpConfiguration);
    }

    /*
     * Adds the fully qualified names and library names for attributes and functions
     * as completion options in import statements.
     */
    private void handleImportProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {

        // TODO: Checked Ok for proposals.

        var attributeContext = pdpConfiguration.attributeContext();
        var functionContext  = pdpConfiguration.functionContext();

        var proposals = new ArrayList<String>();
        if ("libsteps".equals(feature)) {
            proposals.addAll(attributeContext.getAllFullyQualifiedFunctions());
            proposals.addAll(attributeContext.getAvailableLibraries());
            proposals.addAll(functionContext.getAllFullyQualifiedFunctions());
            proposals.addAll(functionContext.getAvailableLibraries());
        }

        addSimpleProposals(proposals, context, acceptor, pdpConfiguration);
    }

    /*
     * Proposals for the schema definitions in the document
     */
    private void handleSchemaProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        // TODO: Checked Ok for proposals. I don't see more meaningful extensions

        if ("subscriptionelement".equals(feature)) {
            addSimpleProposals(AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS, context, acceptor,
                    pdpConfiguration);
            return;
        }
        if ("schemaexpression".equals(feature)) {
            // suggests existing environment variables for reference to be imported as a
            // schema for the given subscription element.
            addSimpleProposals(pdpConfiguration.variables().keySet(), context, acceptor, pdpConfiguration);
        }
    }

    private void handleBasicProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {

        if ("identifier".equals(feature)) {
            // TODO: Ok!
            handleBasicIdentifierProposals(feature, context, acceptor, pdpConfiguration);
            return;
        }

        if ("fsteps".equals(feature)) {
            handleFStepsProposals(context, acceptor, pdpConfiguration);
            return;
        }

        var model            = context.getCurrentModel();
        var functionContext  = pdpConfiguration.functionContext();
        var attributeContext = pdpConfiguration.attributeContext();

        if ("idsteps".equals(feature)) {
//            var templates = new ArrayList<String>();
//            templates.addAll(attributeContext.getAttributeCodeTemplates());
//            addProposalsWithImportsForTemplates(templates, context, acceptor, pdpConfiguration);
//            addProposalsForAttributeStepsIfPresent(context, acceptor, pdpConfiguration);
//            return;
        }

        if ("value".equals(feature)) {
//            addSimpleProposals(AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS, context, acceptor,
//                    pdpConfiguration);
            return;
        }

        if ("steps".equals(feature)) {
//            var templates = new ArrayList<String>();
//
//            templates.addAll(attributeContext.getAttributeCodeTemplates());
//
//            addProposalsWithImportsForTemplates(templates, context, acceptor, pdpConfiguration);
//            addProposalsForAttributeStepsIfPresent(context, acceptor, pdpConfiguration);
            return;
        }

    }

    private void handleFStepsProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        var functionContext     = pdpConfiguration.functionContext();
        var documentedTemplates = functionContext.getDocumentedCodeTemplates();
        var schemas             = functionContext.getFunctionSchemas();

        for (var documentedTemplate : documentedTemplates.entrySet()) {
            var template           = documentedTemplate.getKey();
            var documentation      = documentedTemplate.getValue();
            var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
            var proposals          = proposalsWithImportsForTemplate(template, context, acceptor, pdpConfiguration);
            proposals.add(template);
            addSimpleProposals(proposals, context, acceptor, pdpConfiguration);
            var schema = schemas.get(fullyQualifiedName);
            if (schema != null) {
                for (var prefix : proposals) {
                    var extendedProposals = SchemaProposalGenerator.getCodeTemplates(prefix, schema,
                            pdpConfiguration.variables());
                    addSimpleProposals(extendedProposals, context, acceptor, pdpConfiguration);
                }
            }
        }
    }

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
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        var proposals = new ArrayList<String>();

        var root = context.getRootModel();

        if (root instanceof SAPL sapl) {
            var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (var anImport : imports) {
                if (anImport instanceof WildcardImport wildcardImport) {
                    proposalsWithWildcard(wildcardImport, template, context, acceptor).ifPresent(i -> proposals.add(i));
                } else if (anImport instanceof LibraryImport libraryImport) {
                    proposalsWithLibraryImport(libraryImport, template, context, acceptor, pdpConfiguration)
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
        var steps        = anImport.getLibSteps();
        var functionName = anImport.getFunctionName();
        var prefix       = importPrefixFromSteps(steps) + functionName;
        if (template.startsWith(prefix))
            return Optional.of(functionName + template.substring(prefix.length()));
        else
            return Optional.empty();
    }

    private Optional<String> proposalsWithWildcard(WildcardImport wildCard, String template,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
        var prefix = importPrefixFromSteps(wildCard.getLibSteps());
        if (template.startsWith(prefix))
            return Optional.of(template.substring(prefix.length()));
        else
            return Optional.empty();
    }

    private Optional<String> proposalsWithLibraryImport(LibraryImport libImport, String template,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        var shortPrefix = String.join(".", libImport.getLibSteps());
        var prefix      = shortPrefix + '.';
        if (template.startsWith(prefix))
            return Optional.of(libImport.getLibAlias() + '.' + template.substring(prefix.length()));
        else
            return Optional.empty();
    }

    private String importPrefixFromSteps(EList<String> steps) {
        return String.join(".", steps) + '.';
    }

    private void addProposalsForAttributeStepsIfPresent(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {

        var attributeContext = pdpConfiguration.attributeContext();
//        var definedSchemas   = getValidSchemas(context, context.getCurrentModel(), pdpConfiguration);
//        addSimpleProposalsForAttribute(definedSchemas, context, acceptor, pdpConfiguration);

        var proposals = new ArrayList<>(attributeContext.getAttributeCodeTemplates());
        proposals.addAll(attributeContext.getEnvironmentAttributeCodeTemplates());

//        var helper             = new ValueDefinitionProposalExtractionHelper(pdpConfiguration, context);
//        var attributeProposals = helper.getAttributeProposals();
//        proposals.addAll(attributeProposals);

        // addSimpleProposalsForAttribute(proposals, context, acceptor,
        // pdpConfiguration);
        addSimpleProposals(proposals, context, acceptor, pdpConfiguration);
    }

    /*
     * Only offers to add a blank string for adding a name.
     */
    private void handlePolicyOrPolicySetProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        // TODO: Ok
        if ("saplname".equals(feature)) {
            var entry = getProposalCreator().createProposal("\"\"", context);
            entry.setKind(ContentAssistEntry.KIND_TEXT);
            entry.setDescription("policy name");
            acceptor.accept(entry, 0);
        }
    }

    private void addSimpleProposals(final Collection<String> proposals, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        for (var p : proposals)
            System.out.println("- '" + p + "'");
        // var contextWithCorrectedPrefix = getContextWithCorrectedPrefix(context,
        // pdpConfiguration);
        var contextWithCorrectedPrefix = context;
        for (var proposal : proposals) {
//            if (proposal.contains(">"))
//                addSimpleProposalsForAttribute(List.of(proposal), context, acceptor, pdpConfiguration);
//            else
            addSimpleProposal(proposal, contextWithCorrectedPrefix, acceptor);
        }
    }

    private void addSimpleProposal(final String proposal, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        var entry = getProposalCreator().createProposal(proposal, context);
        acceptor.accept(entry, 0);
    }

}
