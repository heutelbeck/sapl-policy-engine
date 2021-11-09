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
package io.sapl.grammar.ide.contentassist;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

import io.sapl.grammar.sapl.impl.ConditionImpl;
import io.sapl.grammar.sapl.impl.PolicyBodyImpl;
import io.sapl.grammar.sapl.impl.ValueDefinitionImpl;
import io.sapl.interpreter.InitializationException;
import lombok.extern.slf4j.Slf4j;

/**
 * This class enhances the auto-completion proposals that the language server offers.
 */
@Slf4j
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

	private final Collection<String> unwantedKeywords = Set.of("null", "undefined", "true", "false");

	private final Collection<String> allowedKeywords = Set.of("as");

	private final Collection<String> authzSubProposals = Set.of("subject", "action", "resource", "environment");

	private final LibraryAttributeFinder pipAttributeFinder;

	public SAPLContentProposalProvider() throws InitializationException {
		super();
		pipAttributeFinder = new DefaultLibraryAttributeFinder();
	}

	@Override
	protected void _createProposals(Keyword keyword, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {

		String keyValue = keyword.getValue();

		// remove all short keywords unless they are explicitly allowed
		if (!allowedKeywords.contains(keyValue)) {
			if (keyValue.length() < 3)
				return;
		}

		logDebug("SAPLContentProposalProvider._createProposals KEYWORD \"" + keyword.getValue() + "\"");
		super._createProposals(keyword, context, acceptor);
	}

	@Override
	protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		ParserRule parserRule = GrammarUtil.containingParserRule(assignment);
		String parserRuleName = parserRule.getName().toLowerCase();
		String feature = assignment.getFeature().toLowerCase();

		switch (parserRuleName) {
		case "numberliteral":
		case "stringliteral":
			return;

		case "import":
			handleImportProposals(feature, context, acceptor);
			return;

		case "basic":
			if (handleBasicProposals(feature, context, acceptor))
				return;

		case "policy":
			if (handlePolicyProposals(feature, context, acceptor))
				return;

		case "step":
			if (handleStepProposals(feature))
				return;
		}

		logDebug("SAPLContentProposalProvider._createProposals ASSIGNMENT - Rule \"" + parserRuleName
				+ "\" - Feature \"" + feature + "\"");

		super._createProposals(assignment, context, acceptor);
	}

	private boolean handleStepProposals(String feature) {
		return "id".equals(feature);
	}

	private void handleImportProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		// retrieve current text and cursor position
		String policy = context.getRootNode().getText().toLowerCase();
		int offset = context.getOffset();

		Collection<String> proposals;
		switch (feature) {
		case "libsteps":
			proposals = createLibstepsProposals(policy, offset);
			break;

		default:
			proposals = Set.of();
			break;
		}

		if (proposals.isEmpty())
			return;

		// add proposals to list of proposals
		addSimpleProposals(proposals, context, acceptor);
		return;
	}

	private Collection<String> createLibstepsProposals(final String policy, final int offset) {
		final String IMPORT_KEYWORD = "import";

		// remove all text after the cursor
		String importStatement = policy.substring(0, offset);
		// find last import statement and remove all text before it
		int beginning = importStatement.lastIndexOf(IMPORT_KEYWORD);
		importStatement = importStatement.substring(beginning + 1);
		// remove the import keyword
		importStatement = importStatement.substring(IMPORT_KEYWORD.length());
		// remove all new lines
		importStatement = importStatement.replace('\n', ' ').trim();
		// remove all spaces we're only interested in statement e.g. "time.now"
		importStatement = importStatement.replace(" ", "");
		// look up proposals
		return pipAttributeFinder.getAvailableAttributes(importStatement);
	}

	private boolean handleBasicProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {

		// remove technical proposals
		Collection<String> unwantedFeatureProposals = Set.of("fsteps", "identifier", "idsteps", "id");
		if (unwantedFeatureProposals.contains(feature))
			return true;

		// try to resolve for available variables
		if ("value".equals(feature)) {

			// try to move up to the policy body and
			// keep outer condition object as reference
			EObject reference = null;
			EObject model = context.getCurrentModel();
			if (model.eContainer() instanceof ConditionImpl) {
				reference = TreeNavigationHelper.goToLastParent(model, ConditionImpl.class);
				model = TreeNavigationHelper.goToFirstParent(model, PolicyBodyImpl.class);
			}

			// look up all defined variables in the policy
			if (model instanceof PolicyBodyImpl) {
				var policyBody = (PolicyBodyImpl) model;
				Collection<String> definedValues = new HashSet<>();

				// iterate through defined statements which are either conditions or
				// variables
				for (var statement : policyBody.getStatements()) {

					// collect only variables defined above the given condition
					if (statement == reference)
						break;

					// add any encountered valuable to the list of proposals
					if (statement instanceof ValueDefinitionImpl) {
						var valueDefinition = (ValueDefinitionImpl) statement;
						definedValues.add(valueDefinition.getName());
					}
				}

				// add variables to list of proposals
				addSimpleProposals(definedValues, context, acceptor);
			}

			// add authorization subscriptions proposals
			addSimpleProposals(authzSubProposals, context, acceptor);
		}
		return false;
	}

	private boolean handlePolicyProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		if ("saplname".equals(feature)) {
			var entry = getProposalCreator().createProposal("\"\"", context);
			entry.setKind(ContentAssistEntry.KIND_TEXT);
			entry.setDescription("policy name");
			acceptor.accept(entry, 0);
			return true;
		}
		else if ("body".equals(feature)) {
			addSimpleProposals(authzSubProposals, context, acceptor);
		}
		return false;
	}

	@Override
	protected boolean filterKeyword(final Keyword keyword, final ContentAssistContext context) {
		String keyValue = keyword.getValue();

		// remove unwanted technical terms
		if (unwantedKeywords.contains(keyValue))
			return false;

		logDebug("SAPLContentProposalProvider.filterKeyword \"" + keyword.getValue() + "\"");
		return super.filterKeyword(keyword, context);
	}

	private void logDebug(String message) {
		log.debug(message);
	}

	private void addSimpleProposals(final Collection<String> proposals, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		for (var proposal : proposals) {
			var entry = getProposalCreator().createProposal(proposal, context);
			acceptor.accept(entry, 0);
		}
	}
}
