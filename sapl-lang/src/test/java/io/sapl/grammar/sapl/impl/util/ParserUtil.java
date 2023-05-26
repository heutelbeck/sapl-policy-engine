/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.sapl.grammar.sapl.Entitlement;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.services.SAPLGrammarAccess;

public class ParserUtil {

	private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();
	private static final boolean  DEBUG    = false;

	public static FilterComponent filterComponent(String sapl) throws IOException {
		var resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		var resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getFilterComponentRule());
		InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
		resource.load(in, resourceSet.getLoadOptions());
		var filterComponent = (FilterComponent) resource.getContents().get(0);
		if (DEBUG)
			EObjectUtil.dump(filterComponent);
		return filterComponent;
	}

	public static Expression expression(String sapl) throws IOException {
		var resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		var resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getExpressionRule());
		InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
		resource.load(in, resourceSet.getLoadOptions());
		var expression = (Expression) resource.getContents().get(0);
		if (DEBUG)
			EObjectUtil.dump(expression);
		return expression;
	}

	public static Statement statement(String sapl) throws IOException {
		var resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		var resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getStatementRule());
		InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
		resource.load(in, resourceSet.getLoadOptions());
		var statement = (Statement) resource.getContents().get(0);
		if (DEBUG)
			EObjectUtil.dump(statement);
		return statement;
	}

	public static Entitlement entitlement(String sapl) throws IOException {
		var resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		var resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getEntitlementRule());
		InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
		resource.load(in, resourceSet.getLoadOptions());
		var entitlement = (Entitlement) resource.getContents().get(0);
		if (DEBUG)
			EObjectUtil.dump(entitlement);
		return entitlement;
	}

}
