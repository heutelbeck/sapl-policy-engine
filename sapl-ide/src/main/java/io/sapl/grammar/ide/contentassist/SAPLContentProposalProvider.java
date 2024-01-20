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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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

import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

    private final Collection<String> unwantedKeywords = Set.of("null", "undefined", "true", "false");

    private final Collection<String> allowedKeywords = Set.of("as");

    private final Collection<String> authzSubProposals = Set.of("subject", "action", "resource", "environment");

    private AttributeContext attributeContext;

    private FunctionContext functionContext;

    private VariablesAndCombinatorSource variablesAndCombinatorSource;

    private void lazyLoadDependencies() {
        if (attributeContext == null) {
            attributeContext = SpringContext.getBean(AttributeContext.class);
        }
        if (functionContext == null) {
            functionContext = SpringContext.getBean(FunctionContext.class);
        }
        if (variablesAndCombinatorSource == null) {
            variablesAndCombinatorSource = SpringContext.getBean(VariablesAndCombinatorSource.class);
        }
    }

    @Override
    protected void _createProposals(Keyword keyword, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        String keyValue = keyword.getValue();

        // remove all short keywords unless they are explicitly allowed
        if (!allowedKeywords.contains(keyValue) && (keyValue.length() < 3)) {
            return;
        }

        super._createProposals(keyword, context, acceptor);
    }

    @Override
    protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        var parserRule     = GrammarUtil.containingParserRule(assignment);
        var parserRuleName = parserRule.getName().toLowerCase();
        var feature        = assignment.getFeature().toLowerCase();

        switch (parserRuleName) {
        case "import" -> {
            handleImportProposals(feature, context, acceptor);
            return;
        }
        case "schema" -> {
            handleSchemaProposals(feature, context, acceptor);
            return;
        }
        case "basic" -> {
            handleBasicProposals(feature, context, acceptor);
            return;
        }
        case "policy" -> {
            handlePolicyProposals(feature, context, acceptor);
            return;
        }
        case "step" -> {
            handleStepProposals(feature, context, acceptor);
            return;
        }
        case "numberliteral", "stringliteral" -> {
            return;
        }
        default -> {
            // NOOP
        }
        }

        super._createProposals(assignment, context, acceptor);
    }

    private void handleStepProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {

        if ("idsteps".equals(feature))
            addProposalsForAttributeStepsIfPresent(context, acceptor);

    }

    private void handleImportProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {

        List<String> proposals;
        if ("libsteps".equals(feature)) {
            var helper = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource, functionContext,
                    attributeContext, context);
            proposals = new LinkedList<>(attributeContext.getAllFullyQualifiedFunctions());
            proposals.addAll(attributeContext.getAvailableLibraries());
            proposals.addAll(functionContext.getAllFullyQualifiedFunctions());
            proposals.addAll(functionContext.getAvailableLibraries());
            proposals.addAll(helper.getFunctionProposals());
            proposals.addAll(helper.getAttributeProposals());
            addDocumentationToImportProposals(proposals, context, acceptor);
            addDocumentationToTemplates(proposals, context, acceptor);
        } else {
            proposals = List.of();
        }

        if (proposals.isEmpty())
            return;

        // add proposals to list of proposals
        for (var i = 0; i < proposals.size(); i++) {
            var proposal = proposals.get(i);
            proposal = proposal.replaceFirst("<", "");
            proposal = proposal.replaceFirst(">", "");
            proposals.set(i, proposal);
        }
        addSimpleProposals(proposals, context, acceptor);
    }

    private void handleSchemaProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {

        var model = context.getCurrentModel();

        if ("subscriptionelement".equals(feature)) {
            addSimpleProposals(authzSubProposals, context, acceptor);
            return;
        }

        // Feature is schemaexpression
        var validSchemas = getValidSchemas(context, model);
        addSimpleProposals(validSchemas, context, acceptor);

    }

    private void handleBasicProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {

        EObject model = context.getCurrentModel();

        if ("idsteps".equals(feature)) {
            var helper             = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource,
                    functionContext, attributeContext, context);
            var attributeProposals = helper.getAttributeProposals();

            var templates = new ArrayList<String>();
            templates.addAll(attributeContext.getAttributeCodeTemplates());
            templates.addAll(attributeProposals);
            addProposalsWithImportsForTemplates(templates, context, acceptor);
            addProposalsForAttributeStepsIfPresent(context, acceptor);
            return;
        }

        if ("fsteps".equals(feature)) {
            var definedSchemas = getValidSchemas(context, model);
            addSimpleProposals(definedSchemas, context, acceptor);

            var helper            = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource,
                    functionContext, attributeContext, context);
            var functionProposals = helper.getFunctionProposals();

            var templates = new ArrayList<String>();
            templates.addAll(functionContext.getCodeTemplates());
            templates.addAll(functionProposals);
            addDocumentationToTemplates(templates, context, acceptor);
            addSimpleProposals(templates, context, acceptor);
            addProposalsWithImportsForTemplates(templates, context, acceptor);
            return;
        }

        if ("value".equals(feature)) {
            var helper        = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource,
                    functionContext, attributeContext, context);
            var definedValues = helper.getProposals(model, ValueDefinitionProposalExtractionHelper.ProposalType.VALUE);
            // add variables to list of proposals
            addSimpleProposals(definedValues, context, acceptor);
            // add authorization subscriptions proposals
            addSimpleProposals(authzSubProposals, context, acceptor);
        }

        if ("steps".equals(feature)) {
            var helper             = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource,
                    functionContext, attributeContext, context);
            var attributeProposals = helper.getAttributeProposals();

            var templates = new ArrayList<String>();
            templates.addAll(attributeContext.getAttributeCodeTemplates());
            templates.addAll(attributeProposals);

            addProposalsWithImportsForTemplates(templates, context, acceptor);
            addProposalsForAttributeStepsIfPresent(context, acceptor);
        }

    }

    private void addDocumentationToImportProposals(Collection<String> proposals, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var documentedAttributeCodeTemplates = attributeContext.getDocumentedAttributeCodeTemplates();
        for (var proposal : proposals) {
            var fullProposal                          = "<".concat(proposal);
            var documentationForAttributeCodeTemplate = documentedAttributeCodeTemplates.get(fullProposal);
            if (documentationForAttributeCodeTemplate != null) {
                var entry = getProposalCreator().createProposal(proposal, context);
                if (entry != null) {
                    entry.setDocumentation(documentationForAttributeCodeTemplate);
                    entry.setDescription(proposal);
                    acceptor.accept(entry, 0);
                }
            }
        }
    }

    private void addDocumentationToTemplates(Collection<String> templates, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var documentedCodeTemplates = functionContext.getDocumentedCodeTemplates();
        for (var template : templates) {
            var documentation = documentedCodeTemplates.get(template);
            if (documentation != null) {
                var contextWithCorrectedPrefix = getContextWithCorrectedPrefix(context);
                var entry                      = getProposalCreator().createProposal(template,
                        contextWithCorrectedPrefix);
                if (entry != null) {
                    entry.setDocumentation(documentation);
                    entry.setDescription(template);
                    acceptor.accept(entry, 0);
                }
            }
        }
    }

    private ContentAssistContext getContextWithCorrectedPrefix(ContentAssistContext context) {
        var helper = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource, functionContext,
                attributeContext, context);
        var offset = context.getOffset();
        return helper.getContextWithFullPrefix(offset, false);
    }

    private ContentAssistContext getContextWithCorrectedPrefixForAttribute(ContentAssistContext context) {
        var helper = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource, functionContext,
                attributeContext, context);
        var offset = context.getOffset();
        return helper.getContextWithFullPrefix(offset, true);
    }

    private Collection<String> getValidSchemas(ContentAssistContext context, EObject model) {
        var helper = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource, functionContext,
                attributeContext, context);
        return helper.getProposals(model, ValueDefinitionProposalExtractionHelper.ProposalType.SCHEMA);
    }

    private void addProposalsWithImportsForTemplates(Collection<String> templates, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var sapl    = Objects.requireNonNullElse(
                TreeNavigationHelper.goToFirstParent(context.getCurrentModel(), SAPL.class),
                SaplFactory.eINSTANCE.createSAPL());
        var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());

        for (var anImport : imports) {
            if (SaplPackage.Literals.WILDCARD_IMPORT.isSuperTypeOf(anImport.eClass())) {
                var wildCard = (WildcardImport) anImport;
                addProposalsWithWildcard(wildCard, templates, context, acceptor);
            } else if (SaplPackage.Literals.LIBRARY_IMPORT.isSuperTypeOf(anImport.eClass())) {
                var wildCard = (LibraryImport) anImport;
                addProposalsWithLibraryImport(wildCard, templates, context, acceptor);
            } else {
                addProposalsWithImport(anImport, templates, context, acceptor);
            }
        }

    }

    private void addProposalsWithImport(Import anImport, Collection<String> templates, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var steps        = anImport.getLibSteps();
        var functionName = anImport.getFunctionName();
        var prefix       = importPrefixFromSteps(steps) + functionName;
        for (var template : templates)
            if (template.startsWith(prefix))
                addSimpleProposal(functionName + template.substring(prefix.length()), context, acceptor);
    }

    private void addProposalsWithWildcard(WildcardImport wildCard, Collection<String> templates,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {

        var prefix = importPrefixFromSteps(wildCard.getLibSteps());

        for (var template : templates)
            if (template.startsWith(prefix))
                addSimpleProposal(template.substring(prefix.length()), context, acceptor);
    }

    private void addProposalsWithLibraryImport(LibraryImport libImport, Collection<String> templates,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {

        var shortPrefix = String.join(".", libImport.getLibSteps());
        var prefix      = shortPrefix + '.';
        for (var template : templates)
            if (template.startsWith(prefix))
                addSimpleProposals(List.of(libImport.getLibAlias() + '.' + template.substring(prefix.length())),
                        context, acceptor);
    }

    private String importPrefixFromSteps(EList<String> steps) {
        return String.join(".", steps) + '.';
    }

    private void addProposalsForAttributeStepsIfPresent(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        var definedSchemas = getValidSchemas(context, context.getCurrentModel());
        addSimpleProposalsForAttribute(definedSchemas, context, acceptor);

        var proposals = new LinkedList<>(attributeContext.getAttributeCodeTemplates());
        proposals.addAll(attributeContext.getEnvironmentAttributeCodeTemplates());

        var helper             = new ValueDefinitionProposalExtractionHelper(variablesAndCombinatorSource,
                functionContext, attributeContext, context);
        var attributeProposals = helper.getAttributeProposals();
        attributeProposals.addAll(proposals);
        addSimpleProposalsForAttribute(attributeProposals, context, acceptor);
    }

    private void handlePolicyProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        if ("saplname".equals(feature)) {
            var entry = getProposalCreator().createProposal("\"\"", context);
            entry.setKind(ContentAssistEntry.KIND_TEXT);
            entry.setDescription("policy name");
            acceptor.accept(entry, 0);
        } else if ("body".equals(feature)) {
            addSimpleProposals(authzSubProposals, context, acceptor);
        }
    }

    @Override
    protected boolean filterKeyword(final Keyword keyword, final ContentAssistContext context) {
        var keyValue = keyword.getValue();

        // remove unwanted technical terms
        if (unwantedKeywords.contains(keyValue))
            return false;

        return super.filterKeyword(keyword, context);
    }

    private void addSimpleProposals(final Collection<String> proposals, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        var contextWithCorrectedPrefix = getContextWithCorrectedPrefix(context);
        for (var proposal : proposals) {
            if (proposal.contains(">"))
                addSimpleProposalsForAttribute(List.of(proposal), context, acceptor);
            else
                addSimpleProposal(proposal, contextWithCorrectedPrefix, acceptor);
        }
    }

    private void addSimpleProposalsForAttribute(final Collection<String> proposals, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        var contextWithCorrectedPrefix = getContextWithCorrectedPrefixForAttribute(context);
        for (var proposal : proposals) {
            addSimpleProposal("<".concat(proposal), contextWithCorrectedPrefix, acceptor);
        }
    }

    private void addSimpleProposal(final String proposal, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        var entry = getProposalCreator().createProposal(proposal, context);
        acceptor.accept(entry, 0);
    }

}
