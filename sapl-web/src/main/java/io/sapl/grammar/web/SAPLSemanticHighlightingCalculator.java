package io.sapl.grammar.web;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.syntaxcoloring.DefaultSemanticHighlightingCalculator;
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.util.CancelIndicator;

/**
 * This class implements a stub to install SAPL-specific syntax highlighting
 * beyond the xtext default.
 */
public class SAPLSemanticHighlightingCalculator extends DefaultSemanticHighlightingCalculator {

	public SAPLSemanticHighlightingCalculator() {
	}

	@Override
	protected boolean highlightElement(EObject object, IHighlightedPositionAcceptor acceptor,
			CancelIndicator cancelIndicator) {
		// here we could add some additional highlighting by calling
		// acceptor.addPosition(start, length, style-class-Names.class..);
		return super.highlightElement(object, acceptor, cancelIndicator);
	}

}
