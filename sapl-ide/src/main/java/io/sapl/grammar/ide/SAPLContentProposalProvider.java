package io.sapl.grammar.ide;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.xtext.AbstractElement;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalCreator;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

import io.sapl.interpreter.InitializationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

	private Set<String> unwantedKeywords = new HashSet<String>(Arrays.<String>asList("null", "undefined"));
	private Set<String> wantedKeywords = new HashSet<String>(Arrays.<String>asList("as"));

	private LibraryAttributeFinder pipAttributeFinder;

	public SAPLContentProposalProvider() throws InitializationException {
		super();
		pipAttributeFinder = new DefaultLibraryAttributeFinder();
	}

	@Override
	protected void _createProposals(final RuleCall ruleCall, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		super._createProposals(ruleCall, context, acceptor);
	}

	@Override
	protected void _createProposals(Keyword keyword, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {

		String keyValue = keyword.getValue();

		if (!wantedKeywords.contains(keyValue)) {
			// remove short keywords
			if (keyValue.length() < 3)
				return;
		}

		log.debug("SAPLContentProposalProvider._createProposals KEYWORD \"" + keyword.getValue() + "\"");
		super._createProposals(keyword, context, acceptor);
	}

	@Override
	protected void _createProposals(AbstractElement element, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		log.debug("SAPLContentProposalProvider._createProposals ABSTRACT_ELEMENT");
		super._createProposals(element, context, acceptor);
	}

	@Override
	protected void _createProposals(CrossReference reference, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		log.debug("SAPLContentProposalProvider._createProposals CROSSREFERENCE");
		super._createProposals(reference, context, acceptor);
	}

	@Override
	protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		ParserRule parserRule = GrammarUtil.containingParserRule(assignment);
		String parserRuleName = parserRule.getName().toLowerCase();
		String feature = assignment.getFeature().toLowerCase();

		IdeContentProposalCreator proposalCreator = getProposalCreator();

		if (parserRuleName.equals("import")) {
			// retrieve current text and cursor position
			String policy = context.getRootNode().getText().toLowerCase();
			Integer offset = context.getOffset();

			Collection<String> proposals = SAPLContentProposalProviderHelper.createImportProposals(feature, policy,
					offset, pipAttributeFinder);
			if (proposals.isEmpty())
				return;

			// add proposals to list of proposals
			for (String proposal : proposals) {
				var entry = proposalCreator.createProposal(proposal, context);
				acceptor.accept(entry, 0);
			}
			return;
		} else if (parserRuleName.equals("basic")) {
			Collection<String> unwantedFeatureProposals = new HashSet<String>();
			unwantedFeatureProposals.add("fsteps");
			unwantedFeatureProposals.add("identifier");

			if (unwantedFeatureProposals.contains(feature))
				return;
		} else if (parserRuleName.equals("numberliteral")) {
			return;
		} else if (parserRuleName.equals("stringliteral")) {
			return;
		} else if (parserRuleName.equals("policy")) {
			if (feature.equals("saplname")) {
				var entry = proposalCreator.createProposal("\"\"", context, ContentAssistEntry.KIND_TEXT, null);
				entry.setDescription("policy name");
				acceptor.accept(entry, 0);
				return;
			}
		}

		log.debug("SAPLContentProposalProvider._createProposals ASSIGNMENT - Rule \"" + parserRuleName
				+ "\" - Feature \"" + feature + "\"");

		super._createProposals(assignment, context, acceptor);
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
}
