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
package io.sapl.grammar.ide.highlighting;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.Schema;
import io.sapl.grammar.sapl.ValueDefinition;

/**
 * Provides semantic highlighting for SAPL documents via LSP semantic tokens.
 */
public class SAPLSemanticHighlightingCalculator implements ISemanticHighlightingCalculator {

    @Override
    public void provideHighlightingFor(XtextResource resource, IHighlightedPositionAcceptor acceptor,
            CancelIndicator cancelIndicator) {
        if (resource == null || resource.getParseResult() == null) {
            return;
        }

        var rootElement = resource.getParseResult().getRootASTElement();
        if (rootElement == null) {
            return;
        }

        highlightRecursively(rootElement, acceptor, cancelIndicator);
    }

    private void highlightRecursively(EObject element, IHighlightedPositionAcceptor acceptor,
            CancelIndicator cancelIndicator) {
        if (cancelIndicator.isCanceled()) {
            return;
        }

        highlightElement(element, acceptor);

        for (var child : element.eContents()) {
            highlightRecursively(child, acceptor, cancelIndicator);
        }
    }

    private void highlightElement(EObject element, IHighlightedPositionAcceptor acceptor) {
        if (element instanceof PolicySet policySet) {
            highlightFeature(acceptor, policySet, SaplPackage.Literals.POLICY_ELEMENT__SAPL_NAME,
                    HighlightingStyles.POLICY_SET_NAME);
        } else if (element instanceof Policy policy) {
            highlightFeature(acceptor, policy, SaplPackage.Literals.POLICY_ELEMENT__SAPL_NAME,
                    HighlightingStyles.POLICY_NAME);
        } else if (element instanceof Import) {
            highlightNode(acceptor, element, HighlightingStyles.IMPORT);
        } else if (element instanceof Schema) {
            highlightNode(acceptor, element, HighlightingStyles.SCHEMA);
        } else if (element instanceof ValueDefinition valueDefinition) {
            highlightFeature(acceptor, valueDefinition, SaplPackage.Literals.VALUE_DEFINITION__NAME,
                    HighlightingStyles.VARIABLE);
        } else if (element instanceof BasicFunction) {
            highlightNode(acceptor, element, HighlightingStyles.FUNCTION);
        } else if (element instanceof AttributeFinderStep || element instanceof HeadAttributeFinderStep) {
            highlightNode(acceptor, element, HighlightingStyles.ATTRIBUTE);
        } else if (element instanceof BasicIdentifier basicIdentifier) {
            highlightIdentifier(acceptor, basicIdentifier);
        } else if (element instanceof PolicyBody) {
            // Policy body elements are handled by child traversal
        }
    }

    private void highlightIdentifier(IHighlightedPositionAcceptor acceptor, BasicIdentifier identifier) {
        var name = identifier.getIdentifier();
        if ("subject".equals(name) || "action".equals(name) || "resource".equals(name) || "environment".equals(name)) {
            highlightNode(acceptor, identifier, HighlightingStyles.AUTHORIZATION_SUBSCRIPTION);
        }
    }

    private void highlightFeature(IHighlightedPositionAcceptor acceptor, EObject element, EStructuralFeature feature,
            String style) {
        var nodes = NodeModelUtils.findNodesForFeature(element, feature);
        for (var node : nodes) {
            acceptor.addPosition(node.getOffset(), node.getLength(), style);
        }
    }

    private void highlightNode(IHighlightedPositionAcceptor acceptor, EObject element, String style) {
        INode node = NodeModelUtils.findActualNodeFor(element);
        if (node != null) {
            acceptor.addPosition(node.getOffset(), node.getLength(), style);
        }
    }

}
