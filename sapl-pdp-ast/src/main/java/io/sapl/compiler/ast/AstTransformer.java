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
package io.sapl.compiler.ast;

import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.ast.*;
import io.sapl.compiler.SaplCompilerException;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.*;
import io.sapl.grammar.antlr.SAPLParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.compiler.util.SourceLocationUtil.fromContext;
import static io.sapl.compiler.util.StringsUtil.unquoteString;

/**
 * Transforms ANTLR parse tree contexts into AST nodes.
 * <p>
 * This transformer is intentionally "dumb" - it performs only structural
 * mapping without optimization or semantic analysis. All interesting logic
 * (constant folding, validation, etc.) happens on the resulting AST.
 */
public class AstTransformer extends SAPLParserBaseVisitor<AstNode> {

    private static final String ERROR_ATTRIBUTE_IN_FILTER_TARGET   = "Attribute finder steps not allowed in filter targets";
    private static final String ERROR_IMPORT_CONFLICT              = "Import conflict: '%s' already imported as '%s' from '%s'.";
    private static final String ERROR_INVALID_QUALIFIED_NAME       = "Invalid qualified name '%s': too many segments (max: library.function).";
    private static final String ERROR_UNKNOWN_COMBINING_ALGORITHM  = "Unknown combining algorithm.";
    private static final String ERROR_UNKNOWN_ENTITLEMENT          = "Unknown entitlement.";
    private static final String ERROR_UNKNOWN_FILTER_TYPE          = "Unknown filter type: %s";
    private static final String ERROR_UNKNOWN_PAIR_KEY_TYPE        = "Unknown pair key type.";
    private static final String ERROR_UNKNOWN_PATH_ELEMENT         = "Unknown step type in filter path: %s";
    private static final String ERROR_UNKNOWN_PATH_SUBSCRIPT       = "Unknown subscript type in filter path: %s";
    private static final String ERROR_UNKNOWN_RECURSIVE_KEY_STEP   = "Unknown recursive key step type";
    private static final String ERROR_UNKNOWN_STEP_TYPE            = "Unknown step type: %s";
    private static final String ERROR_UNKNOWN_SUBSCRIPTION_ELEMENT = "Unknown subscription element.";
    private static final String ERROR_UNKNOWN_SUBSCRIPT_TYPE       = "Unknown subscript type: %s";
    private static final String ERROR_UNRESOLVED_REFERENCE         = "Unresolved reference '%s': not imported and not fully qualified.";

    private Map<String, List<String>> importMap;

    @Override
    public SaplDocument visitSapl(SaplContext ctx) {
        var imports = ctx.importStatement().stream().map(this::visitImportStatement).toList();
        this.importMap = buildImportMap(imports);
        var schemas = ctx.schemaStatement().stream().map(this::visitSchemaStatement).toList();
        var element = (PolicyElement) visit(ctx.policyElement());
        return new SaplDocument(imports, schemas, element, fromContext(ctx));
    }

    private Map<String, List<String>> buildImportMap(List<Import> imports) {
        var map        = new HashMap<String, List<String>>();
        var importedBy = new HashMap<String, Import>();
        for (var imp : imports) {
            var shortName = imp.effectiveName();
            var existing  = importedBy.get(shortName);
            if (existing != null) {
                throw new SaplCompilerException(
                        ERROR_IMPORT_CONFLICT.formatted(shortName, existing.fullName(), imp.fullName()),
                        imp.location());
            }
            var fullPath = new ArrayList<>(imp.libraryPath());
            fullPath.add(imp.functionName());
            map.put(shortName, fullPath);
            importedBy.put(shortName, imp);
        }
        return map;
    }

    @Override
    public Import visitImportStatement(ImportStatementContext ctx) {
        var libraryPath  = ctx.libSteps.stream().map(this::idText).toList();
        var functionName = idText(ctx.functionName);
        var alias        = ctx.functionAlias != null ? idText(ctx.functionAlias) : null;
        return new Import(libraryPath, functionName, alias, fromContext(ctx));
    }

    @Override
    public SchemaStatement visitSchemaStatement(SchemaStatementContext ctx) {
        var element  = toSubscriptionElement(ctx.subscriptionElement);
        var enforced = ctx.enforced != null;
        var schema   = expr(ctx.schemaExpression);
        return new SchemaStatement(element, enforced, schema, fromContext(ctx));
    }

    @Override
    public AstNode visitPolicySetElement(PolicySetElementContext ctx) {
        return visit(ctx.policySet());
    }

    @Override
    public AstNode visitPolicyOnlyElement(PolicyOnlyElementContext ctx) {
        return visit(ctx.policy());
    }

    @Override
    public PolicySet visitPolicySet(PolicySetContext ctx) {
        var name      = unquoteString(ctx.saplName.getText());
        var algorithm = toCombiningAlgorithm(ctx.combiningAlgorithm());
        var target    = ctx.targetExpression != null ? expr(ctx.targetExpression) : null;
        var variables = ctx.valueDefinition().stream().map(this::visitValueDefinition).toList();
        var policies  = ctx.policy().stream().map(this::visitPolicy).toList();
        return new PolicySet(name, algorithm, target, variables, policies, fromContext(ctx));
    }

    @Override
    public Policy visitPolicy(PolicyContext ctx) {
        var name           = unquoteString(ctx.saplName.getText());
        var entitlement    = toEntitlement(ctx.entitlement());
        var target         = ctx.targetExpression != null ? expr(ctx.targetExpression) : null;
        var body           = ctx.policyBody() != null
                ? ctx.policyBody().statements.stream().map(s -> (Statement) visit(s)).toList()
                : List.<Statement>of();
        var obligations    = ctx.obligations.stream().map(this::expr).toList();
        var advice         = ctx.adviceExpressions.stream().map(this::expr).toList();
        var transformation = ctx.transformation != null ? expr(ctx.transformation) : null;
        return new Policy(name, entitlement, target, body, obligations, advice, transformation, fromContext(ctx));
    }

    @Override
    public AstNode visitValueDefinitionStatement(ValueDefinitionStatementContext ctx) {
        return visit(ctx.valueDefinition());
    }

    @Override
    public AstNode visitConditionStatement(ConditionStatementContext ctx) {
        return new Condition(expr(ctx.expression()), fromContext(ctx));
    }

    @Override
    public VarDef visitValueDefinition(ValueDefinitionContext ctx) {
        var name    = ctx.name.getText();
        var value   = expr(ctx.eval);
        var schemas = ctx.schemaVarExpression.stream().map(this::expr).toList();
        return new VarDef(name, value, schemas, fromContext(ctx));
    }

    @Override
    public AstNode visitLazyOr(LazyOrContext ctx) {
        var operands = ctx.lazyAnd();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var exprs = operands.stream().map(this::expr).toList();
        if (exprs.size() == 2) {
            return new BinaryOperator(BinaryOperatorType.OR, exprs.get(0), exprs.get(1), fromContext(ctx));
        }
        return new Disjunction(exprs, fromContext(ctx));
    }

    @Override
    public AstNode visitLazyAnd(LazyAndContext ctx) {
        var operands = ctx.eagerOr();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var exprs = operands.stream().map(this::expr).toList();
        if (exprs.size() == 2) {
            return new BinaryOperator(BinaryOperatorType.AND, exprs.get(0), exprs.get(1), fromContext(ctx));
        }
        return new Conjunction(exprs, fromContext(ctx));
    }

    @Override
    public AstNode visitEagerOr(EagerOrContext ctx) {
        // Eager OR (|) is treated as alias for lazy OR (||) since SAPL is side-effect
        // free
        var operands = ctx.exclusiveOr();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var exprs = operands.stream().map(this::expr).toList();
        if (exprs.size() == 2) {
            return new BinaryOperator(BinaryOperatorType.OR, exprs.get(0), exprs.get(1), fromContext(ctx));
        }
        return new Disjunction(exprs, fromContext(ctx));
    }

    @Override
    public AstNode visitExclusiveOr(ExclusiveOrContext ctx) {
        var operands = ctx.eagerAnd();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var exprs = operands.stream().map(this::expr).toList();
        if (exprs.size() == 2) {
            return new BinaryOperator(BinaryOperatorType.XOR, exprs.get(0), exprs.get(1), fromContext(ctx));
        }
        return new ExclusiveDisjunction(exprs, fromContext(ctx));
    }

    @Override
    public AstNode visitEagerAnd(EagerAndContext ctx) {
        // Eager AND (&) is treated as alias for lazy AND (&&) since SAPL is side-effect
        // free
        var operands = ctx.equality();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var exprs = operands.stream().map(this::expr).toList();
        if (exprs.size() == 2) {
            return new BinaryOperator(BinaryOperatorType.AND, exprs.get(0), exprs.get(1), fromContext(ctx));
        }
        return new Conjunction(exprs, fromContext(ctx));
    }

    @Override
    public AstNode visitEquality(EqualityContext ctx) {
        var operands = ctx.comparison();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var left  = expr(operands.getFirst());
        var right = expr(operands.get(1));
        var op    = toEqualityOp(ctx);
        return new BinaryOperator(op, left, right, fromContext(ctx));
    }

    private BinaryOperatorType toEqualityOp(EqualityContext ctx) {
        if (ctx.EQ() != null)
            return BinaryOperatorType.EQ;
        if (ctx.NEQ() != null)
            return BinaryOperatorType.NE;
        return BinaryOperatorType.REGEX;
    }

    @Override
    public AstNode visitComparison(ComparisonContext ctx) {
        var operands = ctx.addition();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var left  = expr(operands.getFirst());
        var right = expr(operands.get(1));
        var op    = toComparisonOp(ctx);
        return new BinaryOperator(op, left, right, fromContext(ctx));
    }

    private BinaryOperatorType toComparisonOp(ComparisonContext ctx) {
        if (ctx.LT() != null)
            return BinaryOperatorType.LT;
        if (ctx.LE() != null)
            return BinaryOperatorType.LE;
        if (ctx.GT() != null)
            return BinaryOperatorType.GT;
        if (ctx.GE() != null)
            return BinaryOperatorType.GE;
        return BinaryOperatorType.IN;
    }

    @Override
    public AstNode visitAddition(AdditionContext ctx) {
        var operands = ctx.multiplication();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var operators = new ArrayList<BinaryOperatorType>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            var child = ctx.getChild(i);
            if (child instanceof org.antlr.v4.runtime.tree.TerminalNode terminal) {
                operators.add(terminal.getSymbol().getType() == SAPLParser.PLUS ? BinaryOperatorType.ADD
                        : BinaryOperatorType.SUB);
            }
        }
        // Only use Sum for homogeneous addition chains (all +)
        if (operands.size() >= 3 && operators.stream().allMatch(op -> op == BinaryOperatorType.ADD)) {
            var exprs = operands.stream().map(this::expr).toList();
            return new Sum(exprs, fromContext(ctx));
        }
        return buildLeftAssociativeBinaryWithOps(operands, operators, ctx);
    }

    @Override
    public AstNode visitMultiplication(MultiplicationContext ctx) {
        var operands = ctx.unaryExpression();
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }
        var operators = new ArrayList<BinaryOperatorType>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            var child = ctx.getChild(i);
            if (child instanceof org.antlr.v4.runtime.tree.TerminalNode terminal) {
                operators.add(toMultiplicationOp(terminal.getSymbol().getType()));
            }
        }
        // Only use Product for homogeneous multiplication chains (all *)
        if (operands.size() >= 3 && operators.stream().allMatch(op -> op == BinaryOperatorType.MUL)) {
            var exprs = operands.stream().map(this::expr).toList();
            return new Product(exprs, fromContext(ctx));
        }
        return buildLeftAssociativeBinaryWithOps(operands, operators, ctx);
    }

    private BinaryOperatorType toMultiplicationOp(int tokenType) {
        if (tokenType == SAPLParser.STAR)
            return BinaryOperatorType.MUL;
        if (tokenType == SAPLParser.SLASH)
            return BinaryOperatorType.DIV;
        return BinaryOperatorType.MOD;
    }

    @Override
    public AstNode visitNotExpression(NotExpressionContext ctx) {
        var operand = expr(ctx.unaryExpression());
        return new UnaryOperator(UnaryOperatorType.NOT, operand, fromContext(ctx));
    }

    @Override
    public AstNode visitUnaryMinusExpression(UnaryMinusExpressionContext ctx) {
        var operand = expr(ctx.unaryExpression());
        return new UnaryOperator(UnaryOperatorType.NEGATE, operand, fromContext(ctx));
    }

    @Override
    public AstNode visitUnaryPlusExpression(UnaryPlusExpressionContext ctx) {
        var operand = expr(ctx.unaryExpression());
        return new UnaryOperator(UnaryOperatorType.PLUS, operand, fromContext(ctx));
    }

    @Override
    public AstNode visitBasicExpr(BasicExprContext ctx) {
        return visit(ctx.basicExpression());
    }

    @Override
    public AstNode visitBasicExpression(BasicExpressionContext ctx) {
        var base = (Expression) visit(ctx.basic());
        if (ctx.filterComponent() != null) {
            return buildFilterExpression(base, ctx.filterComponent(), ctx);
        }
        if (ctx.basicExpression() != null) {
            var template = (Expression) visit(ctx.basicExpression());
            return new BinaryOperator(BinaryOperatorType.SUBTEMPLATE, base, template, fromContext(ctx));
        }
        return base;
    }

    private Expression buildFilterExpression(Expression base, FilterComponentContext filterCtx, ParserRuleContext ctx) {
        return switch (filterCtx) {
        case FilterSimpleContext simple   -> buildSimpleFilter(base, simple, ctx);
        case FilterExtendedContext extend -> buildExtendedFilter(base, extend);
        default                           -> throw new SaplCompilerException(
                ERROR_UNKNOWN_FILTER_TYPE.formatted(filterCtx.getClass().getSimpleName()), fromContext(ctx));
        };
    }

    private Expression buildSimpleFilter(Expression base, FilterSimpleContext ctx, ParserRuleContext fullCtx) {
        var each      = ctx.each != null;
        var name      = toQualifiedName(ctx.functionIdentifier());
        var arguments = ctx.arguments() != null ? ctx.arguments().args.stream().map(this::expr).toList()
                : List.<Expression>of();
        var location  = fromContext(fullCtx);
        return new SimpleFilter(base, name, arguments, each, location);
    }

    private Expression buildExtendedFilter(Expression base, FilterExtendedContext ctx) {
        Expression result = base;
        for (FilterStatementContext stmtCtx : ctx.filterStatement()) {
            var each      = stmtCtx.each != null;
            var target    = stmtCtx.target != null ? buildFilterPath(stmtCtx.target) : null;
            var name      = toQualifiedName(stmtCtx.functionIdentifier());
            var arguments = stmtCtx.arguments() != null ? stmtCtx.arguments().args.stream().map(this::expr).toList()
                    : List.<Expression>of();
            var location  = fromContext(stmtCtx);

            if (target == null || target.isWholeValue()) {
                result = new SimpleFilter(result, name, arguments, each, location);
            } else {
                result = new ExtendedFilter(result, target, name, arguments, each, location);
            }
        }
        return result;
    }

    @Override
    public AstNode visitGroupBasic(GroupBasicContext ctx) {
        var group      = ctx.basicGroup();
        var expression = expr(group.expression());
        var base       = new Parenthesized(expression, fromContext(ctx));
        return wrapWithSteps(base, group.step());
    }

    @Override
    public AstNode visitValueBasic(ValueBasicContext ctx) {
        var valueCtx = ctx.basicValue();
        var base     = (Expression) visit(valueCtx.value());
        return wrapWithSteps(base, valueCtx.step());
    }

    @Override
    public AstNode visitFunctionBasic(FunctionBasicContext ctx) {
        var funcCtx   = ctx.basicFunction();
        var name      = toQualifiedName(funcCtx.functionIdentifier());
        var arguments = funcCtx.arguments().args.stream().map(this::expr).toList();
        var base      = new FunctionCall(name, arguments, fromContext(ctx));
        return wrapWithSteps(base, funcCtx.step());
    }

    @Override
    public AstNode visitIdentifierBasic(IdentifierBasicContext ctx) {
        var idCtx = ctx.basicIdentifier();
        var name  = idText(idCtx.saplId());
        var base  = new Identifier(name, fromContext(ctx));
        return wrapWithSteps(base, idCtx.step());
    }

    @Override
    public AstNode visitRelativeBasic(RelativeBasicContext ctx) {
        var relCtx = ctx.basicRelative();
        var base   = new RelativeReference(RelativeType.VALUE, fromContext(ctx));
        return wrapWithSteps(base, relCtx.step());
    }

    @Override
    public AstNode visitRelativeLocationBasic(RelativeLocationBasicContext ctx) {
        var relCtx = ctx.basicRelativeLocation();
        var base   = new RelativeReference(RelativeType.LOCATION, fromContext(ctx));
        return wrapWithSteps(base, relCtx.step());
    }

    @Override
    public AstNode visitEnvAttributeBasic(EnvAttributeBasicContext ctx) {
        var attrCtx = ctx.basicEnvironmentAttribute();
        return buildAttributeAccess(attrCtx.functionIdentifier(), attrCtx.arguments(), attrCtx.attributeFinderOptions,
                false, attrCtx.step(), ctx);
    }

    @Override
    public AstNode visitEnvHeadAttributeBasic(EnvHeadAttributeBasicContext ctx) {
        var attrCtx = ctx.basicEnvironmentHeadAttribute();
        return buildAttributeAccess(attrCtx.functionIdentifier(), attrCtx.arguments(), attrCtx.attributeFinderOptions,
                true, attrCtx.step(), ctx);
    }

    @Override
    public AstNode visitObjectValue(ObjectValueContext ctx) {
        return visit(ctx.object());
    }

    @Override
    public AstNode visitArrayValue(ArrayValueContext ctx) {
        return visit(ctx.array());
    }

    @Override
    public AstNode visitNumberValue(NumberValueContext ctx) {
        return visit(ctx.numberLiteral());
    }

    @Override
    public AstNode visitStringValue(StringValueContext ctx) {
        return visit(ctx.stringLiteral());
    }

    @Override
    public AstNode visitBooleanValue(BooleanValueContext ctx) {
        return visit(ctx.booleanLiteral());
    }

    @Override
    public AstNode visitNullValue(NullValueContext ctx) {
        return visit(ctx.nullLiteral());
    }

    @Override
    public AstNode visitUndefinedValue(UndefinedValueContext ctx) {
        return visit(ctx.undefinedLiteral());
    }

    @Override
    public AstNode visitObject(ObjectContext ctx) {
        var entries = ctx.pair().stream()
                .map(p -> new ObjectEntry(pairKeyText(p.pairKey()), expr(p.pairValue), fromContext(p))).toList();
        return new ObjectExpression(entries, fromContext(ctx));
    }

    @Override
    public AstNode visitArray(ArrayContext ctx) {
        var elements = ctx.items.stream().map(this::expr).toList();
        return new ArrayExpression(elements, fromContext(ctx));
    }

    @Override
    public AstNode visitNumberLiteral(NumberLiteralContext ctx) {
        var value = new BigDecimal(ctx.NUMBER().getText());
        return new Literal(Value.of(value), fromContext(ctx));
    }

    @Override
    public AstNode visitStringLiteral(StringLiteralContext ctx) {
        var value = unquoteString(ctx.STRING().getText());
        return new Literal(Value.of(value), fromContext(ctx));
    }

    @Override
    public AstNode visitTrueLiteral(TrueLiteralContext ctx) {
        return new Literal(Value.TRUE, fromContext(ctx));
    }

    @Override
    public AstNode visitFalseLiteral(FalseLiteralContext ctx) {
        return new Literal(Value.FALSE, fromContext(ctx));
    }

    @Override
    public AstNode visitNullLiteral(NullLiteralContext ctx) {
        return new Literal(Value.NULL, fromContext(ctx));
    }

    @Override
    public AstNode visitUndefinedLiteral(UndefinedLiteralContext ctx) {
        return new Literal(Value.UNDEFINED, fromContext(ctx));
    }

    private FilterPath buildFilterPath(BasicRelativeContext ctx) {
        var elements = ctx.step().stream().map(this::buildPathElement).toList();
        return new FilterPath(elements, fromContext(ctx));
    }

    private PathElement buildPathElement(StepContext ctx) {
        var loc = fromContext(ctx);
        return switch (ctx) {
        case KeyDotStepContext c                  -> new KeyPath(idText(c.keyStep().saplId()), loc);
        case EscapedKeyDotStepContext c           ->
            new KeyPath(unquoteString(c.escapedKeyStep().STRING().getText()), loc);
        case WildcardDotStepContext c             -> new WildcardPath(loc);
        case RecursiveKeyDotDotStepContext c      -> buildRecursiveKeyPath(c.recursiveKeyStep(), loc);
        case RecursiveWildcardDotDotStepContext c -> new RecursiveWildcardPath(loc);
        case RecursiveIndexDotDotStepContext c    ->
            new RecursiveIndexPath(parseSignedNumber(c.recursiveIndexStep().signedNumber()), loc);
        case BracketStepContext c                 -> buildSubscriptPath(c.subscript());
        case AttributeFinderDotStepContext c      ->
            throw new SaplCompilerException(ERROR_ATTRIBUTE_IN_FILTER_TARGET, loc);
        case HeadAttributeFinderDotStepContext c  ->
            throw new SaplCompilerException(ERROR_ATTRIBUTE_IN_FILTER_TARGET, loc);
        default                                   ->
            throw new SaplCompilerException(ERROR_UNKNOWN_PATH_ELEMENT.formatted(ctx.getClass().getSimpleName()), loc);
        };
    }

    private PathElement buildSubscriptPath(SubscriptContext ctx) {
        var loc = fromContext(ctx);
        return switch (ctx) {
        case EscapedKeySubscriptContext c     -> new KeyPath(unquoteString(c.escapedKeyStep().STRING().getText()), loc);
        case WildcardSubscriptContext c       -> new WildcardPath(loc);
        case IndexSubscriptContext c          -> new IndexPath(parseSignedNumber(c.indexStep().signedNumber()), loc);
        case SlicingSubscriptContext c        -> {
            var slice = c.arraySlicingStep();
            var from  = slice.index != null ? parseSignedNumber(slice.index) : null;
            var to    = slice.to != null ? parseSignedNumber(slice.to) : null;
            var step  = slice.stepValue != null ? parseSignedNumber(slice.stepValue) : null;
            yield new SlicePath(from, to, step, loc);
        }
        case ExpressionSubscriptContext c     -> new ExpressionPath(expr(c.expressionStep().expression()), loc);
        case ConditionSubscriptContext c      -> new ConditionPath(expr(c.conditionStep().expression()), loc);
        case IndexUnionSubscriptContext c     ->
            new IndexUnionPath(c.indexUnionStep().indices.stream().map(this::parseSignedNumber).toList(), loc);
        case AttributeUnionSubscriptContext c -> new AttributeUnionPath(
                c.attributeUnionStep().attributes.stream().map(t -> unquoteString(t.getText())).toList(), loc);
        default                               -> throw new SaplCompilerException(
                ERROR_UNKNOWN_PATH_SUBSCRIPT.formatted(ctx.getClass().getSimpleName()), loc);
        };
    }

    private RecursiveKeyPath buildRecursiveKeyPath(RecursiveKeyStepContext ctx, SourceLocation loc) {
        return switch (ctx) {
        case RecursiveIdKeyStepContext c     -> new RecursiveKeyPath(idText(c.saplId()), loc);
        case RecursiveStringKeyStepContext c -> new RecursiveKeyPath(unquoteString(c.STRING().getText()), loc);
        default                              -> throw new SaplCompilerException(ERROR_UNKNOWN_RECURSIVE_KEY_STEP, loc);
        };
    }

    private Expression expr(ParserRuleContext ctx) {
        return (Expression) visit(ctx);
    }

    private String idText(SaplIdContext ctx) {
        return ctx.getText();
    }

    private String pairKeyText(PairKeyContext ctx) {
        return switch (ctx) {
        case StringPairKeyContext s -> unquoteString(s.STRING().getText());
        case IdPairKeyContext i     -> idText(i.saplId());
        default                     -> throw new SaplCompilerException(ERROR_UNKNOWN_PAIR_KEY_TYPE, fromContext(ctx));
        };
    }

    private int parseSignedNumber(SignedNumberContext ctx) {
        var text = ctx.getText();
        return Integer.parseInt(text);
    }

    private QualifiedName toQualifiedName(FunctionIdentifierContext ctx) {
        var parts = ctx.idFragment.stream().map(this::idText).toList();
        var loc   = fromContext(ctx);
        if (parts.size() > 2) {
            throw new SaplCompilerException(ERROR_INVALID_QUALIFIED_NAME.formatted(String.join(".", parts)), loc);
        }
        if (parts.size() == 2) {
            return new QualifiedName(parts);
        }
        // Single-part name: resolve via imports
        var resolved = importMap.get(parts.getFirst());
        if (resolved != null) {
            return new QualifiedName(resolved);
        }
        throw new SaplCompilerException(ERROR_UNRESOLVED_REFERENCE.formatted(parts.getFirst()), loc);
    }

    private SubscriptionElement toSubscriptionElement(ReservedIdContext ctx) {
        return switch (ctx) {
        case SubjectIdContext ignored     -> SubscriptionElement.SUBJECT;
        case ActionIdContext ignored      -> SubscriptionElement.ACTION;
        case ResourceIdContext ignored    -> SubscriptionElement.RESOURCE;
        case EnvironmentIdContext ignored -> SubscriptionElement.ENVIRONMENT;
        default                           ->
            throw new SaplCompilerException(ERROR_UNKNOWN_SUBSCRIPTION_ELEMENT, fromContext(ctx));
        };
    }

    private Entitlement toEntitlement(EntitlementContext ctx) {
        return switch (ctx) {
        case PermitEntitlementContext ignored -> Entitlement.PERMIT;
        case DenyEntitlementContext ignored   -> Entitlement.DENY;
        default                               ->
            throw new SaplCompilerException(ERROR_UNKNOWN_ENTITLEMENT, fromContext(ctx));
        };
    }

    private CombiningAlgorithm toCombiningAlgorithm(CombiningAlgorithmContext ctx) {
        return switch (ctx) {
        case DenyOverridesAlgorithmContext ignored     -> CombiningAlgorithm.DENY_OVERRIDES;
        case PermitOverridesAlgorithmContext ignored   -> CombiningAlgorithm.PERMIT_OVERRIDES;
        case FirstApplicableAlgorithmContext ignored   -> CombiningAlgorithm.FIRST_APPLICABLE;
        case OnlyOneApplicableAlgorithmContext ignored -> CombiningAlgorithm.ONLY_ONE_APPLICABLE;
        case DenyUnlessPermitAlgorithmContext ignored  -> CombiningAlgorithm.DENY_UNLESS_PERMIT;
        case PermitUnlessDenyAlgorithmContext ignored  -> CombiningAlgorithm.PERMIT_UNLESS_DENY;
        default                                        ->
            throw new SaplCompilerException(ERROR_UNKNOWN_COMBINING_ALGORITHM, fromContext(ctx));
        };
    }

    private <T extends ParserRuleContext> Expression buildLeftAssociativeBinaryWithOps(List<T> operands,
            List<BinaryOperatorType> operators, ParserRuleContext ctx) {
        var result = expr(operands.getFirst());
        for (int i = 1; i < operands.size(); i++) {
            var right = expr(operands.get(i));
            result = new BinaryOperator(operators.get(i - 1), result, right, fromContext(ctx));
        }
        return result;
    }

    private Expression buildAttributeAccess(FunctionIdentifierContext nameCtx, ArgumentsContext argsCtx,
            ExpressionContext optionsCtx, boolean head, List<StepContext> stepCtxs, ParserRuleContext ctx) {
        var name      = toQualifiedName(nameCtx);
        var arguments = argsCtx != null ? argsCtx.args.stream().map(this::expr).toList() : List.<Expression>of();
        var options   = optionsCtx != null ? expr(optionsCtx) : null;
        var base      = new EnvironmentAttribute(name, arguments, options, head, fromContext(ctx));
        return wrapWithSteps(base, stepCtxs);
    }

    private Expression wrapWithSteps(Expression base, List<StepContext> stepCtxs) {
        Expression result = base;
        for (StepContext stepCtx : stepCtxs) {
            result = buildStep(result, stepCtx);
        }
        return result;
    }

    private Step buildStep(Expression base, StepContext ctx) {
        var loc = fromContext(ctx);
        return switch (ctx) {
        case KeyDotStepContext c                  -> new KeyStep(base, idText(c.keyStep().saplId()), loc);
        case EscapedKeyDotStepContext c           ->
            new KeyStep(base, unquoteString(c.escapedKeyStep().STRING().getText()), loc);
        case WildcardDotStepContext c             -> new WildcardStep(base, loc);
        case AttributeFinderDotStepContext c      -> {
            var stepCtx = c.attributeFinderStep();
            yield buildAttributeFinderStep(base, stepCtx.functionIdentifier(), stepCtx.arguments(),
                    stepCtx.attributeFinderOptions, false, ctx);
        }
        case HeadAttributeFinderDotStepContext c  -> {
            var stepCtx = c.headAttributeFinderStep();
            yield buildAttributeFinderStep(base, stepCtx.functionIdentifier(), stepCtx.arguments(),
                    stepCtx.attributeFinderOptions, true, ctx);
        }
        case RecursiveKeyDotDotStepContext c      -> buildRecursiveKeyStep(base, c.recursiveKeyStep(), loc);
        case RecursiveWildcardDotDotStepContext c -> new RecursiveWildcardStep(base, loc);
        case RecursiveIndexDotDotStepContext c    ->
            new RecursiveIndexStep(base, parseSignedNumber(c.recursiveIndexStep().signedNumber()), loc);
        case BracketStepContext c                 -> buildSubscriptStep(base, c.subscript());
        default                                   ->
            throw new SaplCompilerException(ERROR_UNKNOWN_STEP_TYPE.formatted(ctx.getClass().getSimpleName()), loc);
        };
    }

    private Step buildSubscriptStep(Expression base, SubscriptContext ctx) {
        var loc = fromContext(ctx);
        return switch (ctx) {
        case EscapedKeySubscriptContext c     ->
            new KeyStep(base, unquoteString(c.escapedKeyStep().STRING().getText()), loc);
        case WildcardSubscriptContext c       -> new WildcardStep(base, loc);
        case IndexSubscriptContext c          ->
            new IndexStep(base, parseSignedNumber(c.indexStep().signedNumber()), loc);
        case SlicingSubscriptContext c        -> {
            var slice = c.arraySlicingStep();
            var from  = slice.index != null ? parseSignedNumber(slice.index) : null;
            var to    = slice.to != null ? parseSignedNumber(slice.to) : null;
            var step  = slice.stepValue != null ? parseSignedNumber(slice.stepValue) : null;
            yield new SliceStep(base, from, to, step, loc);
        }
        case ExpressionSubscriptContext c     -> new ExpressionStep(base, expr(c.expressionStep().expression()), loc);
        case ConditionSubscriptContext c      -> new ConditionStep(base, expr(c.conditionStep().expression()), loc);
        case IndexUnionSubscriptContext c     ->
            new IndexUnionStep(base, c.indexUnionStep().indices.stream().map(this::parseSignedNumber).toList(), loc);
        case AttributeUnionSubscriptContext c -> new AttributeUnionStep(base,
                c.attributeUnionStep().attributes.stream().map(t -> unquoteString(t.getText())).toList(), loc);
        default                               -> throw new SaplCompilerException(
                ERROR_UNKNOWN_SUBSCRIPT_TYPE.formatted(ctx.getClass().getSimpleName()), loc);
        };
    }

    private RecursiveKeyStep buildRecursiveKeyStep(Expression base, RecursiveKeyStepContext ctx, SourceLocation loc) {
        return switch (ctx) {
        case RecursiveIdKeyStepContext c     -> new RecursiveKeyStep(base, idText(c.saplId()), loc);
        case RecursiveStringKeyStepContext c -> new RecursiveKeyStep(base, unquoteString(c.STRING().getText()), loc);
        default                              -> throw new SaplCompilerException(ERROR_UNKNOWN_RECURSIVE_KEY_STEP, loc);
        };
    }

    private AttributeStep buildAttributeFinderStep(Expression base, FunctionIdentifierContext nameCtx,
            ArgumentsContext argsCtx, ExpressionContext optionsCtx, boolean head, ParserRuleContext ctx) {
        var name      = toQualifiedName(nameCtx);
        var arguments = argsCtx != null ? argsCtx.args.stream().map(this::expr).toList() : List.<Expression>of();
        var options   = optionsCtx != null ? expr(optionsCtx) : null;
        return new AttributeStep(base, name, arguments, options, head, fromContext(ctx));
    }

}
