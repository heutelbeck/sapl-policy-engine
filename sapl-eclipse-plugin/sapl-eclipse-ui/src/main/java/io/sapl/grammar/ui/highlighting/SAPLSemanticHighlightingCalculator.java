/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.grammar.ui.highlighting;

import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.EnumLiteralDeclaration;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultHighlightingConfiguration;
import org.eclipse.xtext.ui.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.ui.editor.syntaxcoloring.ISemanticHighlightingCalculator;

import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.FunctionIdentifier;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.ValueDefinition;

/**
 * Provides semantic highlighting for SAPL documents in Eclipse.
 * Matches the highlighting behavior of the LSP semantic tokens.
 */
public class SAPLSemanticHighlightingCalculator implements ISemanticHighlightingCalculator {

    private static final Set<String> KEYWORDS = Set.of("import", "as", "schema", "enforced", "set", "for", "policy",
            "where", "var", "obligation", "advice", "transform", "in", "each", "true", "false", "null", "undefined");

    private static final Set<String> ENTITLEMENTS_AND_ALGORITHMS = Set.of("permit", "deny", "deny-overrides",
            "permit-overrides", "first-applicable", "only-one-applicable", "deny-unless-permit", "permit-unless-deny");

    private static final Set<String> OPERATORS = Set.of("||", "&&", "|", "^", "&", "==", "!=", "=~", "<", "<=", ">",
            ">=", "+", "-", "*", "/", "%", "!", "|-", "::", "@", ".", "..", ":", ",", "|<", "(", ")", "[", "]", "{",
            "}");

    @Override
    public void provideHighlightingFor(XtextResource resource, IHighlightedPositionAcceptor acceptor) {
        if (resource == null || resource.getParseResult() == null) {
            return;
        }

        var rootNode = resource.getParseResult().getRootNode();
        if (rootNode == null) {
            return;
        }

        // Highlight leaf nodes (keywords, operators, strings)
        for (var leafNode : rootNode.getLeafNodes()) {
            highlightLeafNode(leafNode, acceptor);
        }

        // Highlight semantic elements from AST
        var rootElement = resource.getParseResult().getRootASTElement();
        if (rootElement != null) {
            highlightSemanticElements(rootElement, acceptor);
        }
    }

    private void highlightLeafNode(ILeafNode leafNode, IHighlightedPositionAcceptor acceptor) {
        var grammarElement = leafNode.getGrammarElement();
        if (grammarElement == null) {
            return;
        }

        var text = leafNode.getText();

        // Hidden tokens (comments) - handled by default configuration
        if (leafNode.isHidden()) {
            return;
        }

        // Entitlements and combining algorithms (permit, deny, first-applicable, etc.)
        if (ENTITLEMENTS_AND_ALGORITHMS.contains(text)) {
            acceptor.addPosition(leafNode.getOffset(), leafNode.getLength(),
                    SAPLHighlightingConfiguration.ENTITLEMENT_ID);
            return;
        }

        // Keywords
        if (grammarElement instanceof Keyword || grammarElement instanceof EnumLiteralDeclaration) {
            if (KEYWORDS.contains(text)) {
                acceptor.addPosition(leafNode.getOffset(), leafNode.getLength(),
                        DefaultHighlightingConfiguration.KEYWORD_ID);
            } else if (OPERATORS.contains(text)) {
                acceptor.addPosition(leafNode.getOffset(), leafNode.getLength(),
                        SAPLHighlightingConfiguration.OPERATOR_ID);
            }
        }

        // Strings
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            acceptor.addPosition(leafNode.getOffset(), leafNode.getLength(),
                    DefaultHighlightingConfiguration.STRING_ID);
        }
    }

    private void highlightSemanticElements(EObject element, IHighlightedPositionAcceptor acceptor) {
        highlightElement(element, acceptor);
        for (var child : element.eContents()) {
            highlightSemanticElements(child, acceptor);
        }
    }

    private void highlightElement(EObject element, IHighlightedPositionAcceptor acceptor) {
        if (element instanceof PolicySet) {
            highlightFeature(acceptor, element, SaplPackage.Literals.POLICY_ELEMENT__SAPL_NAME,
                    SAPLHighlightingConfiguration.POLICY_NAME_ID);
        } else if (element instanceof Policy) {
            highlightFeature(acceptor, element, SaplPackage.Literals.POLICY_ELEMENT__SAPL_NAME,
                    SAPLHighlightingConfiguration.POLICY_NAME_ID);
        } else if (element instanceof ValueDefinition) {
            highlightFeature(acceptor, element, SaplPackage.Literals.VALUE_DEFINITION__NAME,
                    SAPLHighlightingConfiguration.VARIABLE_ID);
        } else if (element instanceof BasicFunction basicFunction) {
            highlightFunctionIdentifier(acceptor, basicFunction.getIdentifier(),
                    SAPLHighlightingConfiguration.FUNCTION_ID);
        } else if (element instanceof AttributeFinderStep step) {
            highlightFunctionIdentifier(acceptor, step.getIdentifier(), SAPLHighlightingConfiguration.ATTRIBUTE_ID);
        } else if (element instanceof HeadAttributeFinderStep step) {
            highlightFunctionIdentifier(acceptor, step.getIdentifier(), SAPLHighlightingConfiguration.ATTRIBUTE_ID);
        } else if (element instanceof BasicEnvironmentAttribute attr) {
            highlightFunctionIdentifier(acceptor, attr.getIdentifier(), SAPLHighlightingConfiguration.ATTRIBUTE_ID);
        } else if (element instanceof BasicEnvironmentHeadAttribute attr) {
            highlightFunctionIdentifier(acceptor, attr.getIdentifier(), SAPLHighlightingConfiguration.ATTRIBUTE_ID);
        } else if (element instanceof BasicIdentifier basicIdentifier) {
            highlightBasicIdentifier(acceptor, basicIdentifier);
        } else if (element instanceof NumberLiteral) {
            highlightNode(acceptor, element, DefaultHighlightingConfiguration.NUMBER_ID);
        }
    }

    private void highlightFunctionIdentifier(IHighlightedPositionAcceptor acceptor, FunctionIdentifier identifier,
            String style) {
        if (identifier != null) {
            highlightNode(acceptor, identifier, style);
        }
    }

    private void highlightBasicIdentifier(IHighlightedPositionAcceptor acceptor, BasicIdentifier identifier) {
        var name = identifier.getIdentifier();
        if ("subject".equals(name) || "action".equals(name) || "resource".equals(name) || "environment".equals(name)) {
            var nodes = NodeModelUtils.findNodesForFeature(identifier,
                    SaplPackage.Literals.BASIC_IDENTIFIER__IDENTIFIER);
            for (var node : nodes) {
                acceptor.addPosition(node.getOffset(), node.getLength(), SAPLHighlightingConfiguration.AUTHZ_VAR_ID);
            }
        }
    }

    private void highlightNode(IHighlightedPositionAcceptor acceptor, EObject element, String style) {
        var node = NodeModelUtils.findActualNodeFor(element);
        if (node != null) {
            acceptor.addPosition(node.getOffset(), node.getLength(), style);
        }
    }

    private void highlightFeature(IHighlightedPositionAcceptor acceptor, EObject element, EStructuralFeature feature,
            String style) {
        var nodes = NodeModelUtils.findNodesForFeature(element, feature);
        for (var node : nodes) {
            acceptor.addPosition(node.getOffset(), node.getLength(), style);
        }
    }

}
