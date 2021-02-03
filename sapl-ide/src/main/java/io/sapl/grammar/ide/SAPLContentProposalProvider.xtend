package io.sapl.grammar.ide

import org.eclipse.xtext.Assignment
import org.eclipse.xtext.Keyword
import org.eclipse.xtext.RuleCall
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider

class SAPLContentProposalProvider extends IdeContentProposalProvider {
	
	override dispatch createProposals(RuleCall ruleCall, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
		System.out.println("createProposals rule call")
		super._createProposals(ruleCall, context, acceptor)
	}
	
	override dispatch createProposals(Assignment assignment, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
		System.out.println("createProposals assignment")
		super._createProposals(assignment, context, acceptor)
	}

	override protected filterKeyword(Keyword keyword, ContentAssistContext context) {
		System.out.println("filterKeyword")
		super.filterKeyword(keyword, context)
	}
}
