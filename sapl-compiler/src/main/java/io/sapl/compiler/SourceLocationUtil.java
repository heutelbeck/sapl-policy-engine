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
package io.sapl.compiler;

import io.sapl.api.model.SourceLocation;
import io.sapl.grammar.sapl.SAPL;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

/**
 * Utility class for extracting source location information from Xtext AST
 * nodes.
 */
@UtilityClass
public class SourceLocationUtil {

    /**
     * Extracts the source location from an EMF/Xtext AST node.
     *
     * @param astNode the AST node to extract location from (may be null)
     * @return the source location, or null if the node is null or has no location
     * information
     */
    public static SourceLocation fromAstNode(EObject astNode) {
        if (astNode == null) {
            return null;
        }
        var node = NodeModelUtils.getNode(astNode);
        if (node == null) {
            return null;
        }
        var    start          = node.getOffset();
        var    end            = node.getEndOffset();
        var    line           = node.getStartLine();
        var    root           = node.getRootNode();
        var    documentSource = root.getText();
        String documentName   = null;
        if (root.getSemanticElement() instanceof SAPL sapl) {
            documentName = sapl.getPolicyElement().getSaplName();
        }
        return new SourceLocation(documentName, documentSource, start, end, line);
    }
}
