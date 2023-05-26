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
package io.sapl.grammar.validation;

import org.antlr.runtime.EarlyExitException;
import org.antlr.runtime.MismatchedTokenException;
import org.antlr.runtime.NoViableAltException;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.parser.antlr.SyntaxErrorMessageProvider;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.ValueDefinition;

public class SAPLSyntaxErrorMessageProvider extends SyntaxErrorMessageProvider {

	public static final String VAR_ID = "var";

	public static final String SEMICOLON_ID = ";";

	public static final String INCOMPLETE_DOCUMENT = "Incomplete document";

	public static final String INCOMPLETE_IMPORT = "Incomplete import statement, expected library or function name";

	public static final String INCOMPLETE_IMPORT_ALIAS_SET_POLICY = "Expected library alias, import, set or policy";

	public static final String INCOMPLETE_SET_NAME = "Incomplete set, expected a set name, e.g. \\\"set name\\\"";

	public static final String INCOMPLETE_SET_ENTITLEMENT = "Incomplete set, expected an entitlement, e.g. deny-unless-permit or permit-unless-deny";

	public static final String INCOMPLETE_POLICY_NAME = "Incomplete policy, expected a policy name, e.g. \"policy name\"";

	public static final String INCOMPLETE_POLICY_ENTITLEMENT = "Incomplete policy, expected an entitlement, e.g. deny or permit";

	public static final String INCOMPLETE_VARIABLE_NAME = "Incomplete variable definition, expected a variable name";

	public static final String INCOMPLETE_VARIABLE_VALUE = "Incomplete variable definition, expected an assignment, e.g. ' = VALUE;'";

	public static final String INCOMPLETE_VARIABLE_CLOSE = "Incomplete variable definition, expected ';'";

	@Override
	public SyntaxErrorMessage getSyntaxErrorMessage(IParserErrorContext context) {

		RecognitionException exception = context.getRecognitionException();

		SyntaxErrorMessage message = null;
		if (exception instanceof MismatchedTokenException) {
			message = handleMismatchedTokenException(context, (MismatchedTokenException) exception);
		} else if (exception instanceof NoViableAltException) {
			message = handleNoViableAltException(context, (NoViableAltException) exception);
		} else if (exception instanceof EarlyExitException) {
			message = handleEarlyExitException(context, (EarlyExitException) exception);
		}

		if (message != null) {
			return message;
		}
		return super.getSyntaxErrorMessage(context);
	}

	public SyntaxErrorMessage handleMismatchedTokenException(IParserErrorContext context,
			MismatchedTokenException exception) {
		EObject currentContext = context.getCurrentContext();
		INode   node           = context.getCurrentNode();
		String  tokenText      = NodeModelUtils.getTokenText(node).toLowerCase();

		if (currentContext instanceof PolicySet) {
			return new SyntaxErrorMessage(INCOMPLETE_SET_NAME, Diagnostic.SYNTAX_DIAGNOSTIC);
		} else if (currentContext instanceof Policy) {
			if (VAR_ID.equals(tokenText)) {
				return new SyntaxErrorMessage(INCOMPLETE_VARIABLE_NAME, Diagnostic.SYNTAX_DIAGNOSTIC);
			} else if (exception.token == Token.EOF_TOKEN) {
				return new SyntaxErrorMessage(INCOMPLETE_POLICY_NAME, Diagnostic.SYNTAX_DIAGNOSTIC);
			}
		} else if (currentContext instanceof PolicyBody) {
			if (tokenText.contains(VAR_ID) && !tokenText.contains(SEMICOLON_ID)) {
				return new SyntaxErrorMessage(INCOMPLETE_VARIABLE_CLOSE, Diagnostic.SYNTAX_DIAGNOSTIC);
			} else {
				return new SyntaxErrorMessage(INCOMPLETE_DOCUMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
			}
		} else if (currentContext instanceof ValueDefinition) {
			return new SyntaxErrorMessage(INCOMPLETE_VARIABLE_VALUE, Diagnostic.SYNTAX_DIAGNOSTIC);
		}

		if (exception.token == Token.EOF_TOKEN) {
			return new SyntaxErrorMessage(INCOMPLETE_DOCUMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
		}
		return null;
	}

	public SyntaxErrorMessage handleNoViableAltException(IParserErrorContext context, NoViableAltException exception) {
		EObject currentContext = context.getCurrentContext();
		INode   node           = context.getCurrentNode();

		if (currentContext instanceof SAPL) {
			return new SyntaxErrorMessage(INCOMPLETE_IMPORT_ALIAS_SET_POLICY, Diagnostic.SYNTAX_DIAGNOSTIC);
		} else if (currentContext instanceof PolicySet) {
			return new SyntaxErrorMessage(INCOMPLETE_SET_ENTITLEMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
		} else if (currentContext instanceof Policy) {
			return new SyntaxErrorMessage(INCOMPLETE_POLICY_ENTITLEMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
		} else if (currentContext instanceof ValueDefinition) {
			return new SyntaxErrorMessage(INCOMPLETE_VARIABLE_VALUE, Diagnostic.SYNTAX_DIAGNOSTIC);
		}

		EObject grammarElement = node.getGrammarElement();
		if (grammarElement instanceof RuleCall) {
			RuleCall ruleCall  = (RuleCall) grammarElement;
			EObject  container = ruleCall.eContainer();
			if (container instanceof Assignment) {
				Assignment assignment = (Assignment) container;
				String     feature    = assignment.getFeature();
				if ("imports".equals(feature)) {
					return new SyntaxErrorMessage(INCOMPLETE_IMPORT, Diagnostic.SYNTAX_DIAGNOSTIC);
				}
			}
		}

		if (exception.token == Token.EOF_TOKEN) {
			return new SyntaxErrorMessage(INCOMPLETE_DOCUMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
		}
		return null;
	}

	public SyntaxErrorMessage handleEarlyExitException(IParserErrorContext context, EarlyExitException exception) {
		EObject currentContext = context.getCurrentContext();

		if (currentContext instanceof PolicySet) {
			return new SyntaxErrorMessage(INCOMPLETE_DOCUMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
		}

		if (exception.token == Token.EOF_TOKEN) {
			return new SyntaxErrorMessage(INCOMPLETE_DOCUMENT, Diagnostic.SYNTAX_DIAGNOSTIC);
		}

		return null;
	}

}
