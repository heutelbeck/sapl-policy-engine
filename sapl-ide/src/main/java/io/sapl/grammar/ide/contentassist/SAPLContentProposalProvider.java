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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.grammar.sapl.impl.ConditionImpl;
import io.sapl.grammar.sapl.impl.PolicyBodyImpl;
import io.sapl.grammar.sapl.impl.ValueDefinitionImpl;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.TimePolicyInformationPoint;
import lombok.extern.slf4j.Slf4j;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
@Slf4j
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

	private final Collection<String> unwantedKeywords = Set.of("null", "undefined", "true", "false");

	private final Collection<String> allowedKeywords = Set.of("as");

	private final Collection<String> authzSubProposals = Set.of("subject", "action", "resource", "environment");

	private final LibraryAttributeFinder pipAttributeFinder;
	private final AttributeContext attributeContext;
	private final FunctionContext functionContext;

	public SAPLContentProposalProvider() throws InitializationException {
		super();
		pipAttributeFinder = new DefaultLibraryAttributeFinder();
		attributeContext = new AnnotationAttributeContext();
		attributeContext.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));
		functionContext = new AnnotationFunctionContext();
		functionContext.loadLibrary(new FilterFunctionLibrary());
		functionContext.loadLibrary(new StandardFunctionLibrary());
		functionContext.loadLibrary(new TemporalFunctionLibrary());

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
			handleBasicProposals(feature, context, acceptor);
			return;

		case "policy":
			handlePolicyProposals(feature, context, acceptor);
			return;

		case "step":
			handleStepProposals(feature, context, acceptor);
			return;
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

	private List<String> constructAttributeProposalsForAvailableIdSteps(EList<String> idSteps,
			boolean isEnvirionmentAttribute) {
		var prefix = constructPrefixStringForSearchFromSteps(idSteps);
		var templates = attributeContext.getCodeTemplatesWithPrefix(prefix, isEnvirionmentAttribute);
		var proposals = new ArrayList<String>(templates.size());
		for (var template : templates)
			proposals.add(template);
		Collections.sort(proposals);
		return proposals;
	}

	private String constructPrefixStringForSearchFromSteps(EList<String> steps) {
		return String.join(".", steps);
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
			addSimpleProposals(templates, context, acceptor);
			addProposalsWithImportsForTemplates(model, templates, context, acceptor);
			return;
		}

		if ("value".equals(feature)) {
			// try to resolve for available variables

			// try to move up to the policy body and
			// keep outer condition object as reference
			EObject reference = null;
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
	}

	private void addProposalsWithImportsForTemplates(EObject model, List<String> templates,
			ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
		var sapl = TreeNavigationHelper.goToFirstParent(model, SAPL.class);

		if (sapl == null)
			return;

		var imports = sapl.getImports();

		if (imports == null)
			return;
		for (var anImport : imports) {
			if (SaplPackage.Literals.WILDCARD_IMPORT.isSuperTypeOf(anImport.eClass())) {
				var wildCard = (WildcardImport) anImport;
				addProposalsWithWildcard(wildCard, templates, context, acceptor);
				continue;
			}
			if (SaplPackage.Literals.LIBRARY_IMPORT.isSuperTypeOf(anImport.eClass())) {
				var wildCard = (LibraryImport) anImport;
				addProposalsWithLibraryImport(wildCard, templates, context, acceptor);
				continue;
			}
			addProposalsWithImport(anImport, templates, context, acceptor);
		}

	}

	private void addProposalsWithImport(Import anImport, List<String> templates, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		var steps = anImport.getLibSteps();
		var functionName = anImport.getFunctionName();
		var prefix = importPrefixFromSteps(steps) + functionName;
		log.info("import LibSteps:" + anImport.getLibSteps() + " '" + prefix + "'");
		for (var template : templates)
			if (template.startsWith(prefix))
				addSimpleProposal(functionName + template.substring(prefix.length()), context, acceptor);
	}

	private void addProposalsWithWildcard(WildcardImport wildCard, List<String> templates,
			final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {

		log.info("import.* LibSteps:" + wildCard.getLibSteps());

		var prefix = importPrefixFromSteps(wildCard.getLibSteps());

		for (var template : templates)
			if (template.startsWith(prefix))
				addSimpleProposal(template.substring(prefix.length()), context, acceptor);
	}

	private void addProposalsWithLibraryImport(LibraryImport libImport, List<String> templates,
			final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
		log.info("import as LibSteps:" + libImport.getLibSteps());

		var shortPrefix = String.join(".", libImport.getLibSteps());
		var prefix = shortPrefix + '.';
		for (var template : templates)
			if (template.startsWith(prefix))
				addSimpleProposal(libImport.getLibAlias() + '.' + template.substring(prefix.length()), context,
						acceptor);
			else if (template.startsWith(shortPrefix)) // renaming of function
				addSimpleProposal(libImport.getLibAlias() + template.substring(shortPrefix.length()), context, acceptor);
	}

	private String importPrefixFromSteps(EList<String> steps) {
		return String.join(".", steps) + '.';
	}

	private void addProposalsForAttributeStepsIfPresent(ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		var idSteps = extractIdStepsFromContextForAttributeSteps(context);
		if (idSteps != null) {
			log.info("idsteps present");
			var proposals = constructAttributeProposalsForAvailableIdSteps(idSteps, false);
			addSimpleProposals(proposals, context, acceptor);
		}
	}

	private EList<String> extractIdStepsFromContextForAttributeSteps(ContentAssistContext context) {
		var model = context.getCurrentModel();
		var attributeFinderStep = TreeNavigationHelper.goToLastParent(model, AttributeFinderStep.class);
		if (attributeFinderStep != null)
			return attributeFinderStep.getIdSteps();
		var headAttributeFinderStep = TreeNavigationHelper.goToLastParent(model, HeadAttributeFinderStep.class);
		if (headAttributeFinderStep != null)
			return headAttributeFinderStep.getIdSteps();
		return null;
	}

	private void addProposalsForBasicAttributesIfPresent(ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		var idSteps = extractIdStepsFromContextForEnvirionmentAttributes(context);
		if (idSteps != null) {
			log.info("idsteps present");
			var proposals = constructAttributeProposalsForAvailableIdSteps(idSteps, true);
			addSimpleProposals(proposals, context, acceptor);
			addProposalsWithImportsForTemplates(context.getCurrentModel(), proposals, context, acceptor);
		}
	}

	private EList<String> extractIdStepsFromContextForEnvirionmentAttributes(ContentAssistContext context) {
		var model = context.getCurrentModel();
		var basicEnvironmentHeadAttribute = TreeNavigationHelper.goToLastParent(model,
				BasicEnvironmentHeadAttribute.class);
		if (basicEnvironmentHeadAttribute != null)
			return basicEnvironmentHeadAttribute.getIdSteps();
		var basicEnvironmentAttribute = TreeNavigationHelper.goToLastParent(model, BasicEnvironmentAttribute.class);
		if (basicEnvironmentAttribute != null)
			return basicEnvironmentAttribute.getIdSteps();
		return null;
	}

	private boolean handlePolicyProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		if ("saplname".equals(feature)) {
			var entry = getProposalCreator().createProposal("\"\"", context);
			entry.setKind(ContentAssistEntry.KIND_TEXT);
			entry.setDescription("policy name");
			acceptor.accept(entry, 0);
			return true;
		} else if ("body".equals(feature)) {
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

		log.debug("SAPLContentProposalProvider.filterKeyword \"" + keyword.getValue() + "\"");
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
		// log.info("entry [" + entry + "] Proposal: " + proposal);
		acceptor.accept(entry, 0);
	}
}
