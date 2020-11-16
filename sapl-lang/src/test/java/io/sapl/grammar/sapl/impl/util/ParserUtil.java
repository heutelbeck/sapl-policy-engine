package io.sapl.grammar.sapl.impl.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;

import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.services.SAPLGrammarAccess;

public class ParserUtil {
	private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

	public static FilterComponent filterComponent(String sapl) throws IOException {
		XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		XtextResource resource = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getFilterComponentRule());
		InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
		resource.load(in, resourceSet.getLoadOptions());
		return (FilterComponent) resource.getContents().get(0);
	}

	public static Expression expression(String sapl) throws IOException {
		XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		XtextResource resource = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getExpressionRule());
		InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
		resource.load(in, resourceSet.getLoadOptions());
		return (Expression) resource.getContents().get(0);
	}
}
