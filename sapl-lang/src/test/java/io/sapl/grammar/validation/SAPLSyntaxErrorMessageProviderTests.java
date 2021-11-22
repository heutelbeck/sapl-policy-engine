/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.validation;

import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.xbase.lib.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.inject.Inject;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.tests.SAPLInjectorProvider;

@ExtendWith(InjectionExtension.class)
@InjectWith(SAPLInjectorProvider.class)
public class SAPLSyntaxErrorMessageProviderTests {

	@Inject
	@Extension
	private ParseHelper<SAPL> parseHelper;

	@Inject
	@Extension
	private ValidationTestHelper validator;

	@Test
	public void emptyDocument_ReturnsIncompleteDocument() throws Exception {
		String testPolicy = "";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT);
	}

	@Test
	public void incompleteSet_ReturnsHintToProvideSetName() throws Exception {
		String testPolicy = "set ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_SET_NAME);
	}

	@Test
	public void incompleteSet_ReturnsHintToProvideEntitlement() throws Exception {
		String testPolicy = "set \"setname\" ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_SET_ENTITLEMENT);
	}

	@Test
	public void completeSet_ReturnsIncompleteDocument() throws Exception {
		String testPolicy = "set \"setname\" deny-unless-permit";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getDenyUnlessPermitCombiningAlgorithm(),
				Diagnostic.SYNTAX_DIAGNOSTIC, SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT);
	}

	@Test
	public void incompleteImport_ReturnsHintToProvideFunctionOrLibrary() throws Exception {
		String testPolicy = "import ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_IMPORT);
	}

	@Test
	public void incompleteImport_LibraryImportReturnsHintToProvideAliasSetOrPolicy() throws Exception {
		String testPolicy = "import clock as abc";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getLibraryImport(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_IMPORT_ALIAS_SET_POLICY);
	}

	@Test
	public void incompletePolicy_ReturnsHintToProvidePolicyName() throws Exception {
		String testPolicy = "policy ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_POLICY_NAME);
	}

	@Test
	public void incompletePolicy_Trimmed_ReturnsHintToProvideEntitlement() throws Exception {
		String testPolicy = "policy \"test\"";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getPolicy(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_POLICY_ENTITLEMENT);
	}

	@Test
	public void incompletePolicy_WithWhitespace_ReturnsHintToProvideEntitlement() throws Exception {
		String testPolicy = "policy \"test\" ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_POLICY_ENTITLEMENT);
	}

	@Test
	public void incompletePolicyBody_ReturnsDefaultMessage() throws Exception {
		String testPolicy = "policy \"\" deny where";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getPolicy(), Diagnostic.SYNTAX_DIAGNOSTIC,
				"required (...)+ loop did not match anything at input '<EOF>'");
	}

	@Test
	public void incompletePolicyBody_WithIncompleteStatement_ReturnsIncompleteDocumentMessage() throws Exception {
		String testPolicy = "policy \"\" deny where var abc = 5; sub";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getBasicIdentifier(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT);
	}

	@Test
	public void incompleteVariable_Trimmed_ReturnsHintToProvideVariableName() throws Exception {
		String testPolicy = "policy \"\" deny where var";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getPolicyBody(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_NAME);
	}

	@Test
	public void incompleteVariable_WithWhitespace_ReturnsHintToProvideVariableName() throws Exception {
		String testPolicy = "policy \"\" deny where var ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_NAME);
	}

	@Test
	public void incompleteVariable_Trimmed_ReturnsHintToProvideAssignmentSign() throws Exception {
		String testPolicy = "policy \"\" deny where var abc";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getValueDefinition(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_VALUE);
	}

	@Test
	public void incompleteVariable_WithWhitespace_ReturnsHintToProvideAssignmentSign() throws Exception {
		String testPolicy = "policy \"\" deny where var abc ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_VALUE);
	}

	@Test
	public void incompleteVariable_Trimmed_ReturnsHintToAssignValue() throws Exception {
		String testPolicy = "policy \"\" deny where var abc =";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getValueDefinition(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_VALUE);
	}

	@Test
	public void incompleteVariable_WithWhitespace_ReturnsHintToAssignValue() throws Exception {
		String testPolicy = "policy \"\" deny where var abc = ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_VALUE);
	}

	@Test
	public void incompleteVariable_Trimmed_ReturnsHintToCloseVariable() throws Exception {
		String testPolicy = "policy \"\" deny where var abc = 5";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getNumberLiteral(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_CLOSE);
	}

	@Test
	public void incompleteVariable_WithWhitespace_ReturnsHintToCloseVariable() throws Exception {
		String testPolicy = "policy \"\" deny where var abc = 5 ";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				SAPLSyntaxErrorMessageProvider.INCOMPLETE_VARIABLE_CLOSE);
	}
}
