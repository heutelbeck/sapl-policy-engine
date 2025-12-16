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
package io.sapl.grammar.antlr.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import io.sapl.grammar.antlr.SAPLParser.AttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentHeadAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.LazyAndContext;
import io.sapl.grammar.antlr.SAPLParser.LazyOrContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;
import io.sapl.grammar.antlr.SAPLParserBaseListener;

/**
 * Semantic validator for SAPL documents.
 * Validates constraints that cannot be expressed in the grammar alone.
 */
public class SAPLValidator {

    public static final String VALIDATION_ERROR_LAZY_AND_NOT_ALLOWED_IN_TARGET  = "Lazy AND (&&) is not allowed in target expressions. Use eager AND (&) instead.";
    public static final String VALIDATION_ERROR_LAZY_OR_NOT_ALLOWED_IN_TARGET   = "Lazy OR (||) is not allowed in target expressions. Use eager OR (|) instead.";
    public static final String VALIDATION_ERROR_ATTRIBUTE_NOT_ALLOWED_IN_TARGET = "Attribute access is forbidden in target expressions.";
    public static final String VALIDATION_ERROR_ATTRIBUTE_NOT_ALLOWED_IN_SCHEMA = "Attribute access is forbidden in schema expressions.";

    /**
     * Validates a SAPL document parse tree.
     *
     * @param sapl the root context of the parsed SAPL document
     * @return list of validation errors (empty if document is valid)
     */
    public List<ValidationError> validate(SaplContext sapl) {
        if (sapl == null) {
            return List.of();
        }

        var errors = new ArrayList<ValidationError>();

        for (var schema : sapl.schemaStatement()) {
            validateNoAttributes(schema.schemaExpression, VALIDATION_ERROR_ATTRIBUTE_NOT_ALLOWED_IN_SCHEMA,
                    errors::add);
        }

        switch (sapl.policyElement()) {
        case PolicyOnlyElementContext element -> validatePolicy(element.policy(), errors::add);
        case PolicySetElementContext element  -> validatePolicySet(element.policySet(), errors::add);
        case null, default                    -> { /* empty document */ }
        }

        return errors;
    }

    private void validatePolicy(PolicyContext policy, Consumer<ValidationError> errorConsumer) {
        if (policy.targetExpression != null) {
            validateTargetExpression(policy.targetExpression, errorConsumer);
        }

        var body = policy.policyBody();
        if (body != null) {
            for (var statement : body.statements) {
                if (statement instanceof ValueDefinitionStatementContext valueStatement) {
                    validateValueDefinition(valueStatement.valueDefinition(), errorConsumer);
                }
            }
        }
    }

    private void validatePolicySet(PolicySetContext policySet, Consumer<ValidationError> errorConsumer) {
        if (policySet.targetExpression != null) {
            validateTargetExpression(policySet.targetExpression, errorConsumer);
        }

        policySet.valueDefinition().forEach(valueDef -> validateValueDefinition(valueDef, errorConsumer));
        policySet.policy().forEach(policy -> validatePolicy(policy, errorConsumer));
    }

    private void validateValueDefinition(ValueDefinitionContext valueDefinition,
            Consumer<ValidationError> errorConsumer) {
        for (var schemaExpression : valueDefinition.schemaVarExpression) {
            validateNoAttributes(schemaExpression, VALIDATION_ERROR_ATTRIBUTE_NOT_ALLOWED_IN_SCHEMA, errorConsumer);
        }
    }

    private void validateTargetExpression(ParserRuleContext expression, Consumer<ValidationError> errorConsumer) {
        validateNoLazyOperators(expression, errorConsumer);
        validateNoAttributes(expression, VALIDATION_ERROR_ATTRIBUTE_NOT_ALLOWED_IN_TARGET, errorConsumer);
    }

    private void validateNoLazyOperators(ParserRuleContext expression, Consumer<ValidationError> errorConsumer) {
        ParseTreeWalker.DEFAULT.walk(new SAPLParserBaseListener() {
            @Override
            public void enterLazyOr(LazyOrContext context) {
                var orTokens = context.OR();
                if (!orTokens.isEmpty()) {
                    errorConsumer.accept(ValidationError.fromToken(VALIDATION_ERROR_LAZY_OR_NOT_ALLOWED_IN_TARGET,
                            orTokens.getFirst().getSymbol()));
                }
            }

            @Override
            public void enterLazyAnd(LazyAndContext context) {
                var andTokens = context.AND();
                if (!andTokens.isEmpty()) {
                    errorConsumer.accept(ValidationError.fromToken(VALIDATION_ERROR_LAZY_AND_NOT_ALLOWED_IN_TARGET,
                            andTokens.getFirst().getSymbol()));
                }
            }
        }, expression);
    }

    private void validateNoAttributes(ParserRuleContext expression, String errorMessage,
            Consumer<ValidationError> errorConsumer) {
        if (expression == null) {
            return;
        }

        ParseTreeWalker.DEFAULT.walk(new SAPLParserBaseListener() {
            @Override
            public void enterBasicEnvironmentAttribute(BasicEnvironmentAttributeContext context) {
                errorConsumer.accept(ValidationError.fromToken(errorMessage, context.LT().getSymbol()));
            }

            @Override
            public void enterBasicEnvironmentHeadAttribute(BasicEnvironmentHeadAttributeContext context) {
                errorConsumer.accept(ValidationError.fromToken(errorMessage, context.PIPE_LT().getSymbol()));
            }

            @Override
            public void enterAttributeFinderStep(AttributeFinderStepContext context) {
                errorConsumer.accept(ValidationError.fromToken(errorMessage, context.LT().getSymbol()));
            }

            @Override
            public void enterHeadAttributeFinderStep(HeadAttributeFinderStepContext context) {
                errorConsumer.accept(ValidationError.fromToken(errorMessage, context.PIPE_LT().getSymbol()));
            }
        }, expression);
    }

}
