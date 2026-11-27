/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import io.sapl.grammar.antlr.SAPLParser.BasicIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;
import io.sapl.grammar.antlr.SAPLParser.VarNameContext;
import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

/**
 * Provides rename functionality for SAPL variable definitions.
 * Scoping: set-level variables are visible to all policies.
 * Policy-level variables are visible to statements following their
 * definition within the same policy.
 */
class SAPLRenameProvider {

    private static final Range ZERO_RANGE = new Range(new Position(0, 0), new Position(0, 0));

    PrepareRenameResult prepareRename(ParsedDocument document, Position position) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return null;
        }
        val tree    = saplDocument.getSaplParseTree();
        val varName = findVarNameAtPosition(tree, position);
        if (varName != null) {
            return new PrepareRenameResult(rangeOf(varName), varName.getText());
        }
        val identRef = findVariableReferenceAtPosition(tree, position);
        if (identRef != null) {
            return new PrepareRenameResult(rangeOf(identRef), identRef.getText());
        }
        return null;
    }

    WorkspaceEdit provideRename(ParsedDocument document, Position position, String newName) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return null;
        }
        val tree       = saplDocument.getSaplParseTree();
        val targetName = findVariableNameAtPosition(tree, position);
        if (targetName == null) {
            return null;
        }
        val edits         = new ArrayList<TextEdit>();
        val policyElement = tree.policyElement();

        if (policyElement instanceof PolicySetElementContext policySetElement) {
            collectRenameEditsInPolicySet(policySetElement.policySet(), targetName, newName, position, edits);
        } else if (policyElement instanceof PolicyOnlyElementContext policyOnlyElement) {
            collectRenameEditsInPolicy(policyOnlyElement.policy(), targetName, newName, edits);
        }

        if (edits.isEmpty()) {
            return null;
        }
        val workspaceEdit = new WorkspaceEdit();
        workspaceEdit.setChanges(Map.of(document.getUri(), edits));
        return workspaceEdit;
    }

    private void collectRenameEditsInPolicySet(PolicySetContext policySet, String targetName, String newName,
            Position position, List<TextEdit> edits) {
        if (policySet == null) {
            return;
        }
        // A policy that re-declares the same name shadows the set-level variable.
        // If the cursor is inside such a policy, the rename targets the policy-level
        // var.
        for (val policy : policySet.policy()) {
            if (containsPosition(policy, position) && policyRedeclares(policy, targetName)) {
                collectRenameEditsInPolicy(policy, targetName, newName, edits);
                return;
            }
        }
        // Check if it's a set-level var
        for (val varDef : policySet.valueDefinition()) {
            if (varDef.name != null && varDef.name.getText().equals(targetName)) {
                edits.add(new TextEdit(rangeOf(varDef.name), newName));
                collectSetLevelReferences(policySet, targetName, newName, edits);
                return;
            }
        }
        // Otherwise it's a policy-level var
        for (val policy : policySet.policy()) {
            if (containsPosition(policy, position)) {
                collectRenameEditsInPolicy(policy, targetName, newName, edits);
                return;
            }
        }
    }

    private void collectSetLevelReferences(PolicySetContext policySet, String targetName, String newName,
            List<TextEdit> edits) {
        for (val varDef : policySet.valueDefinition()) {
            if (varDef.eval != null) {
                collectReferencesInTree(varDef.eval, targetName, edits, newName);
            }
        }
        // A policy that re-declares the same name shadows the set-level variable only
        // from its redefinition onward. References preceding it still bind to the set
        // var.
        for (val policy : policySet.policy()) {
            if (policyRedeclares(policy, targetName)) {
                collectSetLevelReferencesBeforeShadowing(policy, targetName, newName, edits);
            } else {
                collectReferencesInTree(policy, targetName, edits, newName);
            }
        }
    }

    private void collectSetLevelReferencesBeforeShadowing(PolicyContext policy, String targetName, String newName,
            List<TextEdit> edits) {
        for (val statement : policy.policyBody().statements) {
            if (statement instanceof ValueDefinitionStatementContext varDefStmt) {
                val varDef = varDefStmt.valueDefinition();
                if (varDef.name != null && varDef.name.getText().equals(targetName)) {
                    return;
                }
            }
            collectReferencesInTree(statement, targetName, edits, newName);
        }
    }

    private boolean policyRedeclares(PolicyContext policy, String targetName) {
        if (policy == null || policy.policyBody() == null) {
            return false;
        }
        for (val statement : policy.policyBody().statements) {
            if (statement instanceof ValueDefinitionStatementContext varDefStmt) {
                val varDef = varDefStmt.valueDefinition();
                if (varDef.name != null && varDef.name.getText().equals(targetName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void collectRenameEditsInPolicy(PolicyContext policy, String targetName, String newName,
            List<TextEdit> edits) {
        if (policy == null || policy.policyBody() == null) {
            return;
        }
        val body  = policy.policyBody();
        var found = false;
        for (val statement : body.statements) {
            if (statement instanceof ValueDefinitionStatementContext varDefStmt) {
                val varDef = varDefStmt.valueDefinition();
                if (varDef.name != null && varDef.name.getText().equals(targetName)) {
                    edits.add(new TextEdit(rangeOf(varDef.name), newName));
                    found = true;
                    continue;
                }
            }
            if (found) {
                collectReferencesInTree(statement, targetName, edits, newName);
            }
        }
        if (found) {
            collectReferencesInPolicyOutcome(policy, targetName, newName, edits);
        }
    }

    private void collectReferencesInPolicyOutcome(PolicyContext policy, String targetName, String newName,
            List<TextEdit> edits) {
        for (val obligation : policy.obligations) {
            collectReferencesInTree(obligation, targetName, edits, newName);
        }
        for (val advice : policy.adviceExpressions) {
            collectReferencesInTree(advice, targetName, edits, newName);
        }
        if (policy.transformation != null) {
            collectReferencesInTree(policy.transformation, targetName, edits, newName);
        }
    }

    private void collectReferencesInTree(ParseTree tree, String targetName, List<TextEdit> edits, String newName) {
        if (tree instanceof BasicIdentifierContext basicId && basicId.saplId() != null
                && basicId.saplId().getText().equals(targetName)) {
            edits.add(new TextEdit(rangeOf(basicId.saplId()), newName));
        }
        if (tree instanceof ParserRuleContext ctx) {
            for (var i = 0; i < ctx.getChildCount(); i++) {
                collectReferencesInTree(ctx.getChild(i), targetName, edits, newName);
            }
        }
    }

    private VarNameContext findVarNameAtPosition(SaplContext tree, Position position) {
        val varNames = new ArrayList<VarNameContext>();
        collectNodesOfType(tree, VarNameContext.class, varNames);
        for (val varName : varNames) {
            if (containsPosition(varName, position)) {
                return varName;
            }
        }
        return null;
    }

    private ParserRuleContext findVariableReferenceAtPosition(SaplContext tree, Position position) {
        val identifiers = new ArrayList<BasicIdentifierContext>();
        collectNodesOfType(tree, BasicIdentifierContext.class, identifiers);
        for (val ident : identifiers) {
            val saplId = ident.saplId();
            if (saplId != null && containsPosition(saplId, position)) {
                return saplId;
            }
        }
        return null;
    }

    private String findVariableNameAtPosition(SaplContext tree, Position position) {
        val varName = findVarNameAtPosition(tree, position);
        if (varName != null) {
            return varName.getText();
        }
        val identRef = findVariableReferenceAtPosition(tree, position);
        if (identRef != null) {
            return identRef.getText();
        }
        return null;
    }

    private <T extends ParserRuleContext> void collectNodesOfType(ParseTree node, Class<T> type, List<T> results) {
        if (type.isInstance(node)) {
            results.add(type.cast(node));
        }
        if (node instanceof ParserRuleContext ctx) {
            for (var i = 0; i < ctx.getChildCount(); i++) {
                collectNodesOfType(ctx.getChild(i), type, results);
            }
        }
    }

    private boolean containsPosition(ParserRuleContext ctx, Position position) {
        if (ctx.getStart() == null || ctx.getStop() == null) {
            return false;
        }
        val startLine = ctx.getStart().getLine() - 1;
        val startChar = ctx.getStart().getCharPositionInLine();
        val stopLine  = ctx.getStop().getLine() - 1;
        val stopChar  = ctx.getStop().getCharPositionInLine() + ctx.getStop().getText().length();
        val line      = position.getLine();
        val character = position.getCharacter();
        if (line < startLine || line > stopLine) {
            return false;
        }
        if (line == startLine && character < startChar) {
            return false;
        }
        return line != stopLine || character <= stopChar;
    }

    private Range rangeOf(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) {
            return ZERO_RANGE;
        }
        val start = new Position(ctx.getStart().getLine() - 1, ctx.getStart().getCharPositionInLine());
        val stop  = ctx.getStop();
        val end   = new Position(stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
        return new Range(start, end);
    }

}
