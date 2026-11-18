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
package io.sapl.compiler.document;

import io.sapl.ast.Policy;
import io.sapl.ast.PolicySet;
import io.sapl.ast.SaplDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.PolicySetCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;

@UtilityClass
public class DocumentCompiler {
    private static final String ERROR_COMPILATION_TOO_DEEP = "Compilation of SAPL document failed: expression tree is too deep.";
    private static final String ERROR_PARSING_AST_NULL     = "Parsing of SAPL document failed: AST was null.";
    private static final String ERROR_PARSING_FAILED       = "Parsing of SAPL document failed: %s.";

    /**
     * Maximum size of a SAPL document the engine will compile. Shared with the
     * language server so the editor enforces the same bound as the compile path.
     */
    public static final int MAX_DOCUMENT_SIZE_BYTES = 2 * 1024 * 1024;

    /**
     * Maximum nesting depth of parentheses, brackets and braces in a SAPL
     * document. All three kinds count towards the same depth. A document nested
     * more deeply is rejected instead of being parsed. The bound keeps the
     * recursive passes over a document well within the stack of an ordinary
     * thread.
     */
    static final int MAX_NESTING_DEPTH = 32;

    /**
     * Parses and compiles a document.
     *
     * @param saplDocument the text of the document
     * @param ctx the context of the compilation
     * @return the compiled document
     * @throws SaplCompilerException if the document has a problem, with a
     * message that names each problem as {@link Document#errors()} does
     */
    public static CompiledDocument compileDocument(String saplDocument, CompilationContext ctx) {
        val transformer = new AstTransformer();
        transformer.setContext(ctx.getPdpId(), ctx.getConfigurationId());
        val parsedDocument = parseDocument(SyntaxAnalysis.of(saplDocument), transformer);
        if (parsedDocument.isInvalid()) {
            throw new SaplCompilerException(ERROR_PARSING_FAILED.formatted(parsedDocument.errors()));
        }
        val ast = parsedDocument.saplDocument();
        if (ast == null) {
            throw new SaplCompilerException(ERROR_PARSING_AST_NULL);
        }
        return compileAst(ast, ctx);
    }

    /**
     * Parses a document and checks its rules. A document with a problem is
     * returned as an invalid document that names its problems: this method does
     * not throw for any text.
     *
     * @param saplDefinition the text of the document
     * @return the document with its parse tree, its AST if it is valid, and its
     * problems
     */
    public Document parseDocument(String saplDefinition) {
        return parseDocument(SyntaxAnalysis.of(saplDefinition));
    }

    /**
     * Builds the AST of a document whose syntax and rules are analysed. The AST
     * is built only if the document has no problem.
     *
     * @param analysis the analysis of the document
     * @return the document with its parse tree, its AST if it is valid, and its
     * problems
     */
    public Document parseDocument(SyntaxAnalysis analysis) {
        return parseDocument(analysis, new AstTransformer());
    }

    // The transformer is fresh for every document. It carries per-document mutable
    // state (import map, schemas), so sharing one instance would let concurrent
    // compilations of different documents race and resolve names against another
    // document's imports.
    private Document parseDocument(SyntaxAnalysis analysis, AstTransformer transformer) {
        val parseTree = analysis.parseTree();
        val source    = analysis.source();
        if (parseTree == null || !analysis.diagnostics().isEmpty()) {
            return new Document(parseTree, null, source, analysis.diagnostics());
        }
        return SyntaxAnalysis.refusingUnforeseenFailures(source,
                () -> new Document(parseTree, transformer.visitSapl(parseTree), source, List.of()),
                problem -> new Document(parseTree, null, source, List.of(problem)));
    }

    private static CompiledDocument compileAst(SaplDocument saplDocument, CompilationContext ctx) {
        ctx.resetForNextDocument();
        try {
            return switch (saplDocument) {
            case Policy policy       -> PolicyCompiler.compilePolicy(policy, ctx);
            case PolicySet policySet -> PolicySetCompiler.compilePolicySet(policySet, ctx);
            };
        } catch (StackOverflowError e) {
            throw new SaplCompilerException(ERROR_COMPILATION_TOO_DEEP, e);
        }
    }
}
