package io.sapl.grammar.web;

import org.eclipse.xtext.AbstractElement;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

/**
 * This class implements a stub to install SAPL-specific content assist
 * proposals beyond the xtext default.
 *
 */
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

	private final FilterFunctionsProposalProvider filterFunctionsProposalProvider = SAPLTextAreaServiceAdapter
			.getInstance();

	@Override
	protected void _createProposals(AbstractElement element, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		super._createProposals(element, context, acceptor);
	}

	@Override
	protected void _createProposals(Assignment assignment, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		if ("libSteps".equals(assignment.getFeature())) {
			filterFunctionsProposalProvider.appendFilterLibraryNames(context, acceptor, getProposalCreator());
		} else if ("functionName".equals(assignment.getFeature())) {
			filterFunctionsProposalProvider.appendFilterFunctionNames(context, acceptor, getProposalCreator());
		} else {

			super._createProposals(assignment, context, acceptor);
		}
	}

	@Override
	protected void _createProposals(Keyword keyword, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		super._createProposals(keyword, context, acceptor);
		// filterFunctionsProposalProvider.appendFilterFunctions(null,
		// acceptor);
	}

	@Override
	protected void _createProposals(RuleCall ruleCall, ContentAssistContext context,
			IIdeContentProposalAcceptor acceptor) {
		super._createProposals(ruleCall, context, acceptor);
		// filterFunctionsProposalProvider.appendFilterFunctions(null,
		// acceptor);
	}

}
