package io.sapl.grammar.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.xtext.AbstractElement;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

public class SAPLContentProposalProvider extends IdeContentProposalProvider {

	private List<String> unwantedProposals = new ArrayList<String>(Arrays.<String>asList("@", "!", "(", ")", "[", "]",
			"{", "}", "null", "undefined", "&", "&&", "*", "+", "-", ".", "..", "/", "|", "||"));

	@Override
	protected boolean filterKeyword(final Keyword keyword, final ContentAssistContext context) {
		String keyValue = keyword.getValue();
		boolean contains = this.unwantedProposals.contains(keyValue);
		if (contains) {
			return false;
		}
		return super.filterKeyword(keyword, context);
	}

	@Override
	public void createProposals(final AbstractElement assignment, final ContentAssistContext context,
			final IIdeContentProposalAcceptor acceptor) {
		if (assignment instanceof Assignment) {
			_createProposals((Assignment) assignment, context, acceptor);
			return;
		} else if (assignment instanceof CrossReference) {
			_createProposals((CrossReference) assignment, context, acceptor);
			return;
		} else if (assignment instanceof Keyword) {
			_createProposals((Keyword) assignment, context, acceptor);
			return;
		} else if (assignment instanceof RuleCall) {
			_createProposals((RuleCall) assignment, context, acceptor);
			return;
		} else if (assignment != null) {
			_createProposals(assignment, context, acceptor);
			return;
		} else {
			throw new IllegalArgumentException(
					"Unhandled parameter types: " + Arrays.<Object>asList(assignment, context, acceptor).toString());
		}
	}
}
