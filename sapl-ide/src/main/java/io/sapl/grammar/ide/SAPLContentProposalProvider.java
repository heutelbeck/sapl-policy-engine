package io.sapl.grammar.ide;

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

@Slf4j
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

	private Collection<String> unwantedKeywords = Set.of("null", "undefined", "true", "false");
	private Collection<String> allowedKeywords = Set.of("as");
	private Collection<String> authzSubProposals = Set.of("subject", "action", "resource", "environment");

	private LibraryAttributeFinder pipAttributeFinder;

	public SAPLContentProposalProvider() throws InitializationException {
		super();
		pipAttributeFinder = new DefaultLibraryAttributeFinder();
	}

	@Override
	protected void _createProposals(Keyword keyword, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {

		String keyValue = keyword.getValue();

		if (!allowedKeywords.contains(keyValue)) {
			// remove short keywords
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
			if (handleImportProposals(feature, context, acceptor))
				return;

		case "basic":
			if (handleBasicProposals(feature, context, acceptor))
				return;

		case "policy":
			if (handlePolicyProposals(feature, context, acceptor))
				return;
		}

		logDebug("SAPLContentProposalProvider._createProposals ASSIGNMENT - Rule \"" + parserRuleName
				+ "\" - Feature \"" + feature + "\"");

		super._createProposals(assignment, context, acceptor);
	}

	private boolean handleImportProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		// retrieve current text and cursor position
		String policy = context.getRootNode().getText().toLowerCase();
		Integer offset = context.getOffset();

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
			return true;

		// add proposals to list of proposals
		addSimpleProposals(proposals, context, acceptor);
		return true;
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
		importStatement = importStatement.replace("\n", " ").trim();
		// remove all spaces we're only interested in statement e.g. "clock.now"
		importStatement = importStatement.replace(" ", "");
		// look up proposals
		return pipAttributeFinder.GetAvailableAttributes(importStatement);
	}

	private boolean handleBasicProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		Collection<String> unwantedFeatureProposals = Set.of("fsteps", "identifier");
		if (unwantedFeatureProposals.contains(feature))
			return true;

		if (feature.equals("value")) {
			EObject reference = null;
			EObject model = context.getCurrentModel();
			if (model.eContainer() instanceof ConditionImpl) {
				reference = goToLastParent(model, ConditionImpl.class);
				model = goToFirstParent(model, PolicyBodyImpl.class);
			}

			if (model instanceof PolicyBodyImpl) {
				var policyBody = (PolicyBodyImpl) model;
				Collection<String> definedValues = new HashSet<>();
				for (var statement : policyBody.getStatements()) {
					// collect only values defined above the condition
					if (statement == reference)
						break;

					if (statement instanceof ValueDefinitionImpl) {
						var valueDefinition = (ValueDefinitionImpl) statement;
						definedValues.add(valueDefinition.getName());
					}
				}

				addSimpleProposals(definedValues, context, acceptor);
			}

			addSimpleProposals(authzSubProposals, context, acceptor);
		}
		return false;
	}

	private boolean handlePolicyProposals(String feature, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		if (feature.equals("saplname")) {
			var entry = getProposalCreator().createProposal("\"\"", context);
			entry.setKind(ContentAssistEntry.KIND_TEXT);
			entry.setDescription("policy name");
			acceptor.accept(entry, 0);
			return true;
		} else if (feature.equals("body")) {
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

	private <T> T goToFirstParent(EObject object, Class<T> classType) {
		while (object != null) {
			if (classType.isInstance(object))
				return classType.cast(object);

			object = object.eContainer();
		}
		return null;
	}

	private <T> T goToLastParent(EObject object, Class<T> classType) {
		EObject parent = null;

		while (object != null) {
			if (classType.isInstance(object))
				parent = object;

			object = object.eContainer();
		}

		if (parent != null && classType.isInstance(parent))
			return classType.cast(parent);
		return null;
	}
}
