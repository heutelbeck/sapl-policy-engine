package io.sapl.grammar.web;

import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IdeCrossrefProposalProvider;
import org.eclipse.xtext.resource.IEObjectDescription;

/**
 * This class implements a stub to install a SAPL-specific proposal provider for
 * cross references.
 *
 */
public class SAPLCrossrefProposalProvider extends IdeCrossrefProposalProvider {

	public SAPLCrossrefProposalProvider() {
	}

	@Override
	protected ContentAssistEntry createProposal(IEObjectDescription candidate, CrossReference crossRef,
			ContentAssistContext context) {
		return super.createProposal(candidate, crossRef, context);
	}

}
