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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.antlr.runtime.EarlyExitException;
import org.antlr.runtime.MismatchedTokenException;
import org.antlr.runtime.NoViableAltException;
import org.antlr.runtime.Token;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.parser.antlr.ISyntaxErrorMessageProvider.IParserErrorContext;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.xbase.lib.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import com.google.inject.Inject;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
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
	
	@Test
	public void getSyntaxErrorMessage_NoRecognizedExceptionTypeReturnsDefaultMessage() {
		String defaultMessage = "Test Error";
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		
		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getDefaultMessage()).thenReturn(defaultMessage);

		SyntaxErrorMessage message = provider.getSyntaxErrorMessage(context);
		assertEquals(defaultMessage, message.getMessage());
	}
	
	@Test
	public void handleMismatchedTokenException_ContextIsPolicy_NoMatchingOptionReturnsNull() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		MismatchedTokenException exception = new MismatchedTokenException();
		
		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		when(context.getCurrentContext()).thenReturn(Mockito.mock(Policy.class));
		when(context.getCurrentNode()).thenReturn(Mockito.mock(INode.class));
		
		SyntaxErrorMessage message = provider.handleMismatchedTokenException(context, exception);
		assertNull(message);
	}
	
	@Test
	public void handleMismatchedTokenException_ContextIsPolicyBody_TokenTextContainsOnlySemicolon_ReturnsIncompleteDocument() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		MismatchedTokenException exception = new MismatchedTokenException();

		INode node = Mockito.mock(INode.class);
		when(node.getText()).thenReturn("abc;");
		
		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		when(context.getCurrentContext()).thenReturn(Mockito.mock(PolicyBody.class));
		when(context.getCurrentNode()).thenReturn(node);
		
		SyntaxErrorMessage message = provider.handleMismatchedTokenException(context, exception);
		assertEquals(SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT, message.getMessage());
	}
	
	@Test
	public void handleMismatchedTokenException_ContextIsUnknown_ReturnsNull() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		MismatchedTokenException exception = new MismatchedTokenException();

		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		when(context.getCurrentContext()).thenReturn(Mockito.mock(SAPL.class));
		when(context.getCurrentNode()).thenReturn(Mockito.mock(INode.class));
		
		SyntaxErrorMessage message = provider.handleMismatchedTokenException(context, exception);
		assertNull(message);
	}
	
	@Test
	public void handleMismatchedTokenException_TokenIsEOF_ReturnsIncompleteDocument() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		MismatchedTokenException exception = new MismatchedTokenException();
		exception.token = Token.EOF_TOKEN;

		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		when(context.getCurrentContext()).thenReturn(Mockito.mock(SAPL.class));
		when(context.getCurrentNode()).thenReturn(Mockito.mock(INode.class));
		
		SyntaxErrorMessage message = provider.handleMismatchedTokenException(context, exception);
		assertEquals(SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT, message.getMessage());
	}
	
	@Test
	public void handleNoViableAltException_GrammarElementIsNotRuleCall_ReturnsNull() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		NoViableAltException exception = new NoViableAltException();
		
		INode node = Mockito.mock(INode.class);
		when(node.getGrammarElement()).thenReturn(Mockito.mock(EObject.class));

		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		when(context.getCurrentNode()).thenReturn(node);
		
		SyntaxErrorMessage message = provider.handleNoViableAltException(context, exception);
		assertNull(message);
	}
	
	@Test
	public void handleNoViableAltException_RuleCallContainerIsNotAssignment_ReturnsNull() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		NoViableAltException exception = new NoViableAltException();
		
		RuleCall ruleCall = Mockito.mock(RuleCall.class);
		when(ruleCall.eContainer()).thenReturn(Mockito.mock(EObject.class));
		
		INode node = Mockito.mock(INode.class);
		when(node.getGrammarElement()).thenReturn(ruleCall);

		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		when(context.getCurrentNode()).thenReturn(node);
		
		SyntaxErrorMessage message = provider.handleNoViableAltException(context, exception);
		assertNull(message);
	}
	
	@Test
	public void handleEarlyExitException_TokenIsEOF_ReturnsIncompleteDocument() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		EarlyExitException exception = new EarlyExitException();
		exception.token = Token.EOF_TOKEN;
		
		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		
		SyntaxErrorMessage message = provider.handleEarlyExitException(context, exception);
		assertEquals(SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT, message.getMessage());
	}
	
	@Test
	public void handleEarlyExitException_UnknownContext_ReturnsNull() {
		SAPLSyntaxErrorMessageProvider provider = new SAPLSyntaxErrorMessageProvider();
		EarlyExitException exception = new EarlyExitException();
		
		IParserErrorContext context = Mockito.mock(IParserErrorContext.class);
		when(context.getRecognitionException()).thenReturn(exception);
		
		SyntaxErrorMessage message = provider.handleEarlyExitException(context, exception);
		assertNull(message);
	}
}
