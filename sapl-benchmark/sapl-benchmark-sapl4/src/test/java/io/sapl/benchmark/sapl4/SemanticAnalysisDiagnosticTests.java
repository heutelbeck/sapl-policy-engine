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
package io.sapl.benchmark.sapl4;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CompilerFlags;
import io.sapl.api.pdp.PdpData;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.index.smtdd.SemanticVariableOrder;
import io.sapl.compiler.index.smtdd.SmtddBuilder;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Semantic analysis diagnostic for hospital scenarios")
class SemanticAnalysisDiagnosticTests {

    private static final boolean PRINT_RESULTS = false;

    @ValueSource(ints = { 1, 5, 50, 300 })
    @DisplayName("analyze predicate grouping in hospital scenario")
    @ParameterizedTest(name = "hospital-{0}")
    void whenHospitalThenAnalyzeGrouping(int departments) {
        val scenario = ScenarioFactory.create("hospital-" + departments, 42L);
        val flags    = new CompilerFlags("NAIVE", false, 10_000, Value.EMPTY_OBJECT);

        // Build PDP to get the function/attribute brokers, then compile documents
        // ourselves
        val components = scenario.buildPdp(flags);
        val pdpData    = new PdpData(scenario.variables(), Value.EMPTY_OBJECT);
        val ctx        = new CompilationContext(pdpData, components.functionBroker(), components.attributeBroker());
        ctx.setCompilerFlags(flags);

        val policies  = scenario.policies().get();
        val documents = new ArrayList<CompiledDocument>();
        for (val source : policies) {
            documents.add(DocumentCompiler.compileDocument(source, ctx));
        }
        components.dispose();

        val allPredicatesPerFormula = new ArrayList<List<IndexPredicate>>();
        var formulaCount            = 0;
        var constantTrue            = 0;
        var constantFalse           = 0;
        var constantError           = 0;

        for (val document : documents) {
            val expression = document.isApplicable();
            if (expression instanceof BooleanValue(var b) && b) {
                constantTrue++;
            } else if (expression instanceof BooleanValue ignored) {
                constantFalse++;
            } else if (expression instanceof ErrorValue) {
                constantError++;
            } else if (expression instanceof PureOperator pureOp) {
                val predicates = new ArrayList<IndexPredicate>();
                collectPredicates(pureOp.booleanExpression(), predicates);
                allPredicatesPerFormula.add(predicates);
                formulaCount++;
            }
        }

        val result = SemanticVariableOrder.analyze(allPredicatesPerFormula);

        if (PRINT_RESULTS) {
            val totalGrouped = result.equalityGroups().stream()
                    .mapToInt(g -> g.getEqualsFormulas().size() + g.getExcludeFormulas().size()).sum();
            val totalPreds   = totalGrouped + result.remainingPredicates().size();
            System.out.printf("%nhospital-%d: %d docs, %d formulas, %d predicates -> %d eq groups + %d remaining%n",
                    departments, documents.size(), formulaCount, totalPreds, result.equalityGroups().size(),
                    result.remainingPredicates().size());
        }

        // Extract boolean expressions for BDD construction
        val booleanExpressions = new ArrayList<BooleanExpression>();
        for (val document : documents) {
            if (document.isApplicable() instanceof PureOperator pureOp) {
                booleanExpressions.add(pureOp.booleanExpression());
            }
        }

        // Build the SMTDD - validates it completes without blowup
        SmtddBuilder.build(result, booleanExpressions, allPredicatesPerFormula);
    }

    private static void collectPredicates(BooleanExpression expression, List<IndexPredicate> result) {
        switch (expression) {
        case Constant ignored    -> {}
        case Atom(var predicate) -> {
            if (!result.contains(predicate)) {
                result.add(predicate);
            }
        }
        case Not(var operand)    -> collectPredicates(operand, result);
        case Or(var operands)    -> operands.forEach(op -> collectPredicates(op, result));
        case And(var operands)   -> operands.forEach(op -> collectPredicates(op, result));
        }
    }

}
