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

@SuppressWarnings("all")
public class SAPLContentProposalProvider extends IdeContentProposalProvider {
  private List<String> unwantedProposals = new ArrayList<String>(
    Arrays.<String>asList("@", "!", "(", ")", "[", "]", "{", "}", "null", "undefined", "&", "&&", 
      "*", "+", "-", ".", "..", "/", "|", "||"));
  
  @Override
  protected void _createProposals(final RuleCall ruleCall, final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
    System.out.println("createProposals rule call");
    super._createProposals(ruleCall, context, acceptor);
  }
  
  @Override
  protected void _createProposals(final Assignment assignment, final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
    System.out.println("createProposals assignment");
    super._createProposals(assignment, context, acceptor);
  }
  
  @Override
  protected boolean filterKeyword(final Keyword keyword, final ContentAssistContext context) {
    boolean _xblockexpression = false;
    {
      System.out.println("filterKeyword");
      String keyValue = keyword.getValue();
      boolean _contains = this.unwantedProposals.contains(keyValue);
      if (_contains) {
        System.out.println("Filtered!");
        return false;
      }
      _xblockexpression = super.filterKeyword(keyword, context);
    }
    return _xblockexpression;
  }
  
  @Override
  public void createProposals(final AbstractElement assignment, final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
    if (assignment instanceof Assignment) {
      _createProposals((Assignment)assignment, context, acceptor);
      return;
    } else if (assignment instanceof CrossReference) {
      _createProposals((CrossReference)assignment, context, acceptor);
      return;
    } else if (assignment instanceof Keyword) {
      _createProposals((Keyword)assignment, context, acceptor);
      return;
    } else if (assignment instanceof RuleCall) {
      _createProposals((RuleCall)assignment, context, acceptor);
      return;
    } else if (assignment != null) {
      _createProposals(assignment, context, acceptor);
      return;
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(assignment, context, acceptor).toString());
    }
  }
}
