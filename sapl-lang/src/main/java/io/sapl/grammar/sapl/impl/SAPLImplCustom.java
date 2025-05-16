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
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreUtil;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.grammar.sapl.BinaryOperator;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Schema;
import io.sapl.grammar.sapl.impl.util.MatchingUtil;
import io.sapl.interpreter.DocumentEvaluationResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class SAPLImplCustom extends SAPLImpl {

    @Override
    public Mono<Val> matches() {
        // this does not use the implicit expression to not disrupt hit recording with
        // the test tools

        if (this.schemas == null) {
            return this.policyElement.matches();
        } else {
            return Mono.zip(this.policyElement.matches(), schemasMatch()).map(this::and);
        }

    }

    private Mono<Val> schemasMatch() {
        return MatchingUtil.matches(getSchemaPredicateExpression(), this);
    }

    private Val and(Tuple2<Val, Val> matches) {
        final var elementMatch = matches.getT1();
        final var schemaMatch  = matches.getT2();
        Val       result;
        if (schemaMatch.isError()) {
            result = schemaMatch;
        } else if (elementMatch.isError()) {
            result = elementMatch;
        } else {
            result = Val.of(elementMatch.getBoolean() && schemaMatch.getBoolean()).withTrace(SAPL.class, true,
                    Map.of(Trace.TARGET_EXPRESSION_RESULT, elementMatch, Trace.SCHEMA_VALIDATION, schemaMatch));
        }
        return result;
    }

    @Override
    public Flux<DocumentEvaluationResult> evaluate() {
        return policyElement.evaluate();
    }

    @Override
    public String toString() {
        if (null == getPolicyElement()) {
            return getClass().getSimpleName();
        }
        return getPolicyElement().getSaplName();
    }

    /**
     * The implicit target expression is the explicit target expression of the
     * policy element combined with an expression for validating declared enforced
     * schemata on subscription elements.
     *
     * @returns a target expression including schema matchers.
     */
    @Override
    public Expression getImplicitTargetExpression() {
        final var explicitTargetExpression     = this.getPolicyElement().getTargetExpression();
        final var getSchemaPredicateExpression = getSchemaPredicateExpression();

        if (explicitTargetExpression == null && getSchemaPredicateExpression == null) {
            return null;
        }

        if (explicitTargetExpression == null) {
            return getSchemaPredicateExpression;
        }

        if (getSchemaPredicateExpression == null) {
            return explicitTargetExpression;
        }
        final var implicitTargetExpression = SaplFactory.eINSTANCE.createEagerAnd();
        implicitTargetExpression.setLeft(getSchemaPredicateExpression);
        implicitTargetExpression.setRight(explicitTargetExpression);
        return implicitTargetExpression;
    }

    public Expression getSchemaPredicateExpression() {
        if (schemas == null) {
            return null;
        }
        final var expressionsByKeyword = collectEnforcedSchemaExpressionsByKeyword(schemas);
        final var keywordPredicates    = new ArrayList<Expression>(4);
        for (var expressionByKeyword : expressionsByKeyword.entrySet()) {
            keywordPredicates.add(inBraces(concatenateExpressionsWithOperator(expressionByKeyword.getValue(),
                    SaplFactory.eINSTANCE::createEagerOr)));
        }
        if (keywordPredicates.isEmpty()) {
            final var value = SaplFactory.eINSTANCE.createBasicValue();
            value.setValue(SaplFactory.eINSTANCE.createTrueLiteral());
            return value;
        }
        return inBraces(concatenateExpressionsWithOperator(keywordPredicates, SaplFactory.eINSTANCE::createEagerAnd));
    }

    private static Map<String, List<Expression>> collectEnforcedSchemaExpressionsByKeyword(EList<Schema> schemas) {
        Map<String, List<Expression>> schemasByKeyword = new HashMap<>();
        for (var schema : schemas) {
            if (schema.isEnforced()) {
                final var expression = schemaPredicateExpression(schema);
                schemasByKeyword.computeIfAbsent(schema.getSubscriptionElement(), k -> new ArrayList<>(1))
                        .add(expression);
            }
        }
        return schemasByKeyword;
    }

    private static Expression inBraces(Expression expression) {
        final var group = SaplFactory.eINSTANCE.createBasicGroup();
        group.setExpression(expression);
        return group;
    }

    private static Expression concatenateExpressionsWithOperator(List<Expression> expressions,
            Supplier<BinaryOperator> operatorSupplier) {
        final var head = expressions.get(0);
        if (expressions.size() == 1)
            return head;

        final var operator = operatorSupplier.get();
        operator.setLeft(head);
        final var tail = expressions.subList(1, expressions.size());
        operator.setRight(concatenateExpressionsWithOperator(tail, operatorSupplier));
        return operator;
    }

    private static Expression schemaPredicateExpression(Schema schema) {
        final var function = SaplFactory.eINSTANCE.createBasicFunction();
        function.eSet(function.eClass().getEStructuralFeature("identifier"),
                SaplFactory.eINSTANCE.createFunctionIdentifier());
        final var fSteps = function.getIdentifier().getNameFragments();
        fSteps.add(SchemaValidationLibrary.NAME);
        fSteps.add("isCompliantWithExternalSchemas");

        final var identifier = SaplFactory.eINSTANCE.createBasicIdentifier();
        identifier.setIdentifier(schema.getSubscriptionElement());

        final var referenceToSchemasVariable = SaplFactory.eINSTANCE.createBasicIdentifier();
        referenceToSchemasVariable.setIdentifier("SCHEMAS");

        final var arguments = SaplFactory.eINSTANCE.createArguments();
        final var args      = arguments.getArgs();
        args.add(identifier);
        args.add(EcoreUtil.copy(schema.getSchemaExpression()));
        args.add(referenceToSchemasVariable);

        function.setArguments(arguments);
        return function;
    }

}
