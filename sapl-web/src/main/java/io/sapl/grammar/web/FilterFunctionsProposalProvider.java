package io.sapl.grammar.web;

import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalCreator;

/**
 *
 */
public interface FilterFunctionsProposalProvider {
    void appendFilterFunctionNames(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
	    IdeContentProposalCreator proposalCreator);

    void appendFilterLibraryNames(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
	    IdeContentProposalCreator proposalCreator);
}
