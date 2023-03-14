/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

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

	private void lazyLoadDependencies() {
		if (attributeContext == null) {
			attributeContext = SpringContext.getBean(AttributeContext.class);
		}
		if (functionContext == null) {
			functionContext = SpringContext.getBean(FunctionContext.class);
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

		ParserRule parserRule     = GrammarUtil.containingParserRule(assignment);
		String     parserRuleName = parserRule.getName().toLowerCase();
		String     feature        = assignment.getFeature().toLowerCase();

		switch (parserRuleName) {
		case "numberliteral":
		case "stringliteral":
			return;

		case "import":
			handleImportProposals(feature, context, acceptor);
			return;

		case "basic":
			handleBasicProposals(feature, context, acceptor);
			return;

		case "policy":
			handlePolicyProposals(feature, context, acceptor);
			return;

		case "step":
			handleStepProposals(feature, context, acceptor);
			return;
		default:
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

		Collection<String> proposals;
		if ("libsteps".equals(feature)) {
			proposals = new LinkedList<>(attributeContext.getAllFullyQualifiedFunctions());
			proposals.addAll(attributeContext.getAvailableLibraries());
			proposals.addAll(functionContext.getAllFullyQualifiedFunctions());
			proposals.addAll(functionContext.getAvailableLibraries());
			addDocumentationToImportProposals(proposals, context, acceptor);
			addDocumentationToTemplates(proposals, context, acceptor);
		} else {
			proposals = Set.of();
		}

		if (proposals.isEmpty())
			return;

		// add proposals to list of proposals
		addSimpleProposals(proposals, context, acceptor);
	}

	private void handleBasicProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {

		EObject model = context.getCurrentModel();

		if ("idsteps".equals(feature)) {
			addProposalsForBasicAttributesIfPresent(context, acceptor);
			return;
		}

		if ("fsteps".equals(feature)) {
			var templates = functionContext.getCodeTemplates();
			addDocumentationToTemplates(templates, context, acceptor);
			addSimpleProposals(templates, context, acceptor);
			addProposalsWithImportsForTemplates(templates, context, acceptor);
			return;
		}

		if ("value".equals(feature)) {
			// try to resolve for available variables

			// try to move up to the policy body
			if (model.eContainer() instanceof Condition) {
				model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
			}

			// look up all defined variables in the policy
			if (model instanceof PolicyBody) {
				var                policyBody    = (PolicyBody) model;
				Collection<String> definedValues = new HashSet<>();

				int currentOffset = context.getOffset();

				// iterate through defined statements which are either conditions or
				// variables
				for (var statement : policyBody.getStatements()) {
					// add any encountered valuable to the list of proposals
					if (statement instanceof ValueDefinition) {
						var valueDefinition = (ValueDefinition) statement;

						// check if variable definition is happening after cursor
						INode valueDefinitionNode   = NodeModelUtils.getNode(valueDefinition);
						int   valueDefinitionOffset = valueDefinitionNode.getOffset();

						if (currentOffset > valueDefinitionOffset) {
							definedValues.add(valueDefinition.getName());
						} else {
							break;
						}
					}
				}

				// add variables to list of proposals
				addSimpleProposals(definedValues, context, acceptor);
			}
			// add authorization subscriptions proposals
			addSimpleProposals(authzSubProposals, context, acceptor);
		}
	}

	private void addDocumentationToImportProposals(Collection<String> proposals, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		var documentedAttributeCodeTemplates = attributeContext.getDocumentedAttributeCodeTemplates();
		for (var proposal : proposals) {
			var documentationForAttributeCodeTemplate = documentedAttributeCodeTemplates.get(proposal);
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
				var entry = getProposalCreator().createProposal(template, context);
				if (entry != null) {
					entry.setDocumentation(documentation);
					entry.setDescription(template);
					acceptor.accept(entry, 0);
				}
			}
		}
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
				addSimpleProposal(libImport.getLibAlias() + '.' + template.substring(prefix.length()), context,
						acceptor);
			else if (template.startsWith(shortPrefix)) // renaming of function
				addSimpleProposal(libImport.getLibAlias() + template.substring(shortPrefix.length()), context,
						acceptor);
	}

	private String importPrefixFromSteps(EList<String> steps) {
		return String.join(".", steps) + '.';
	}

	private void addProposalsForAttributeStepsIfPresent(ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		var proposals = attributeContext.getAttributeCodeTemplates();
		addSimpleProposals(proposals, context, acceptor);
	}

	private void addProposalsForBasicAttributesIfPresent(ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		var proposals = attributeContext.getEnvironmentAttributeCodeTemplates();
		addSimpleProposals(proposals, context, acceptor);
		addProposalsWithImportsForTemplates(proposals, context, acceptor);
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
		String keyValue = keyword.getValue();

		// remove unwanted technical terms
		if (unwantedKeywords.contains(keyValue))
			return false;

		return super.filterKeyword(keyword, context);
	}

	private void addSimpleProposals(final Collection<String> proposals, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		for (var proposal : proposals)
			addSimpleProposal(proposal, context, acceptor);
	}

	private void addSimpleProposal(final String proposal, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		var entry = getProposalCreator().createProposal(proposal, context);
		acceptor.accept(entry, 0);
	}

}
