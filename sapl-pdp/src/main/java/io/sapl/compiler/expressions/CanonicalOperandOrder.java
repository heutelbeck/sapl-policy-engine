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
package io.sapl.compiler.expressions;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.AttributeUnionPath;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.ConditionPath;
import io.sapl.ast.ExpressionPath;
import io.sapl.ast.IndexPath;
import io.sapl.ast.IndexUnionPath;
import io.sapl.ast.KeyPath;
import io.sapl.ast.PathElement;
import io.sapl.ast.RecursiveIndexPath;
import io.sapl.ast.RecursiveKeyPath;
import io.sapl.ast.RecursiveWildcardPath;
import io.sapl.ast.SlicePath;
import io.sapl.ast.UnaryOperatorType;
import io.sapl.ast.WildcardPath;
import io.sapl.compiler.expressions.ArrayCompiler.PureArray;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryPurePure;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryPureValue;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryValuePure;
import io.sapl.compiler.expressions.ExpressionCompiler.IdentifierOp;
import io.sapl.compiler.expressions.ExpressionCompiler.RelativeLocationOp;
import io.sapl.compiler.expressions.ExpressionCompiler.RelativeValueOp;
import io.sapl.compiler.expressions.ExtendedFilterCompiler.ExtendedFilterPure;
import io.sapl.compiler.expressions.FilterCompiler.EachPure;
import io.sapl.compiler.expressions.FunctionCallCompiler.NoArgsFunction;
import io.sapl.compiler.expressions.FunctionCallCompiler.PureFunction;
import io.sapl.compiler.expressions.NaryBooleanCompiler.NaryBooleanPure;
import io.sapl.compiler.expressions.NaryOperatorCompiler.NaryPure;
import io.sapl.compiler.expressions.ObjectCompiler.PureObject;
import io.sapl.compiler.expressions.OperandStructure.StructuredOperator;
import io.sapl.compiler.expressions.RegexCompiler.RegexPrecompiledPure;
import io.sapl.compiler.expressions.RegexCompiler.RegexPure;
import io.sapl.compiler.expressions.StepCompiler.AttributeUnionStepPure;
import io.sapl.compiler.expressions.StepCompiler.ConditionStepConstBasePure;
import io.sapl.compiler.expressions.StepCompiler.ConditionStepPureConstCond;
import io.sapl.compiler.expressions.StepCompiler.ConditionStepPurePure;
import io.sapl.compiler.expressions.StepCompiler.ExpressionStepPure;
import io.sapl.compiler.expressions.StepCompiler.ExpressionStepPurePure;
import io.sapl.compiler.expressions.StepCompiler.IndexStepPure;
import io.sapl.compiler.expressions.StepCompiler.IndexUnionStepPure;
import io.sapl.compiler.expressions.StepCompiler.KeyStepPure;
import io.sapl.compiler.expressions.StepCompiler.RecursiveIndexStepPure;
import io.sapl.compiler.expressions.StepCompiler.RecursiveKeyStepPure;
import io.sapl.compiler.expressions.StepCompiler.RecursiveWildcardStepPure;
import io.sapl.compiler.expressions.StepCompiler.SliceStepPure;
import io.sapl.compiler.expressions.StepCompiler.WildcardStepPure;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.LazyAndPurePure;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.LazyOrPurePure;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.LazyValuePure;
import io.sapl.compiler.expressions.SubtemplateCompiler.SubtemplatePurePure;
import io.sapl.compiler.expressions.SubtemplateCompiler.SubtemplatePureValue;
import io.sapl.compiler.expressions.SubtemplateCompiler.SubtemplateValuePure;
import io.sapl.compiler.expressions.UnaryOperatorCompiler.UnaryPure;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The canonical order of the operands of the commutative operators
 * {@code ==}, {@code !=}, {@code *}, {@code &&}, {@code ||}, {@code &},
 * {@code |} and {@code ^}. Ordered canonically, {@code a == b} and
 * {@code b == a} compile to the same operator, so they are semantically equal,
 * hash alike and form one predicate in the policy index.
 * <p>
 * This engine orders operands by their structure. The strata keep their order of
 * evaluation: constants first, then pure operands, then streams. Constants are
 * ordered by their values, pure operands by the names of their kinds and then
 * by their parts. Streams keep the order written, and so do operands that
 * compare equal.
 * <p>
 * As operands are evaluated in this order, of several errors among the
 * operands the first in canonical order is the result.
 */
@UtilityClass
class CanonicalOperandOrder {

    private static final int CONSTANTS = 0;
    private static final int PURES     = 1;
    private static final int STREAMS   = 2;

    private static final int ABSENT_PART       = 0;
    private static final int INTEGER_PART      = 1;
    private static final int TEXT_PART         = 2;
    private static final int PATH_ELEMENT_PART = 3;
    private static final int OPERAND_PART      = 4;
    private static final int OTHER_PART        = 5;

    private static final String KIND_CONDITION_STEP  = "step:condition";
    private static final String KIND_EXPRESSION_STEP = "step:expression";
    private static final String KIND_REGEX           = "binary:=~";
    private static final String KIND_SUBTEMPLATE     = "subtemplate";

    private static final Comparator<CompiledExpression> ORDER = CanonicalOperandOrder::compareOperands;

    private static final Map<BinaryOperatorType, String> BINARY_KINDS = binaryKinds();

    /**
     * @param operand an operand
     * @param other another operand
     * @return true if the operand comes before the other in canonical order
     */
    static boolean precedes(CompiledExpression operand, CompiledExpression other) {
        return ORDER.compare(operand, other) < 0;
    }

    /**
     * @param operands the operands in the order written
     * @param <T> the type of the operands
     * @return a new list of the operands in canonical order
     */
    static <T extends CompiledExpression> List<T> sorted(List<T> operands) {
        val sorted = new ArrayList<>(operands);
        sorted.sort(ORDER);
        return sorted;
    }

    private static int compareOperands(CompiledExpression operand, CompiledExpression other) {
        val first     = asOperand(operand);
        val second    = asOperand(other);
        val byStratum = Integer.compare(stratum(first), stratum(second));
        if (byStratum != 0) {
            return byStratum;
        }
        if (first instanceof Value value && second instanceof Value otherValue) {
            return compareValues(value, otherValue);
        }
        if (first instanceof PureOperator pure && second instanceof PureOperator otherPure) {
            return compareStructures(structureOf(pure), structureOf(otherPure));
        }
        return 0;
    }

    /**
     * A conjunction or disjunction holds a constant error as an operator. It is
     * ordered as the constant it stands for.
     */
    private static CompiledExpression asOperand(CompiledExpression operand) {
        return operand instanceof ConstantErrorPredicate predicate ? predicate.error() : operand;
    }

    private static int stratum(CompiledExpression operand) {
        return switch (operand) {
        case Value ignored          -> CONSTANTS;
        case PureOperator ignored   -> PURES;
        case StreamOperator ignored -> STREAMS;
        };
    }

    private static int compareStructures(OperandStructure structure, OperandStructure other) {
        val byKind = compareTexts(structure.kind(), other.kind());
        if (byKind != 0) {
            return byKind;
        }
        return compareLists(structure.parts(), other.parts(), CanonicalOperandOrder::compareParts);
    }

    private static int compareParts(@Nullable Object part, @Nullable Object other) {
        val bySort = Integer.compare(sortOf(part), sortOf(other));
        if (bySort != 0) {
            return bySort;
        }
        return switch (part) {
        case Integer integer              -> Integer.compare(integer, (Integer) other);
        case String text                  -> compareTexts(text, (String) other);
        case OperandStructure pathElement -> compareStructures(pathElement, (OperandStructure) other);
        case CompiledExpression operand   -> compareOperands(operand, (CompiledExpression) other);
        case null, default                -> 0;
        };
    }

    private static int sortOf(@Nullable Object part) {
        return switch (part) {
        case null                       -> ABSENT_PART;
        case Integer ignored            -> INTEGER_PART;
        case String ignored             -> TEXT_PART;
        case OperandStructure ignored   -> PATH_ELEMENT_PART;
        case CompiledExpression ignored -> OPERAND_PART;
        default                         -> OTHER_PART;
        };
    }

    private static int compareValues(Value value, Value other) {
        val byType = Integer.compare(typeRank(value), typeRank(other));
        if (byType != 0) {
            return byType;
        }
        return switch (value) {
        case NumberValue(var number) -> number.compareTo(((NumberValue) other).value());
        case TextValue(var text)     -> compareTexts(text, ((TextValue) other).value());
        case ArrayValue array        -> compareLists(array, (ArrayValue) other, CanonicalOperandOrder::compareValues);
        case ObjectValue object      -> compareLists(sortedEntries(object), sortedEntries((ObjectValue) other),
                CanonicalOperandOrder::compareEntries);
        case ErrorValue error        -> compareTexts(error.message(), ((ErrorValue) other).message());
        default                      -> 0;
        };
    }

    private static int typeRank(Value value) {
        return switch (value) {
        case UndefinedValue ignored   -> 0;
        case NullValue ignored        -> 1;
        case BooleanValue(var isTrue) -> isTrue ? 3 : 2;
        case NumberValue ignored      -> 4;
        case TextValue ignored        -> 5;
        case ArrayValue ignored       -> 6;
        case ObjectValue ignored      -> 7;
        case ErrorValue ignored       -> 8;
        };
    }

    private static List<Map.Entry<String, Value>> sortedEntries(ObjectValue object) {
        val entries = new ArrayList<>(object.entrySet());
        entries.sort((entry, other) -> compareTexts(entry.getKey(), other.getKey()));
        return entries;
    }

    private static int compareEntries(Map.Entry<String, Value> entry, Map.Entry<String, Value> other) {
        val byKey = compareTexts(entry.getKey(), other.getKey());
        return byKey != 0 ? byKey : compareValues(entry.getValue(), other.getValue());
    }

    /**
     * Texts compare by their code points, which is the order of their UTF-8
     * bytes. The order of their UTF-16 units differs for characters beyond the
     * Basic Multilingual Plane.
     */
    private static int compareTexts(String text, String other) {
        return Arrays.compare(text.codePoints().toArray(), other.codePoints().toArray());
    }

    private static <T> int compareLists(List<? extends T> list, List<? extends T> other,
            Comparator<? super T> comparator) {
        val common = Math.min(list.size(), other.size());
        for (var i = 0; i < common; i++) {
            val byElement = comparator.compare(list.get(i), other.get(i));
            if (byElement != 0) {
                return byElement;
            }
        }
        return Integer.compare(list.size(), other.size());
    }

    private static OperandStructure structureOf(PureOperator operator) {
        return switch (operator) {
        case IdentifierOp identifier          -> OperandStructure.of("element:" + identifier.name());
        case RelativeValueOp ignored          -> OperandStructure.of("relative:value");
        case RelativeLocationOp ignored       -> OperandStructure.of("relative:location");
        case PureArray array                  -> new OperandStructure("array", List.copyOf(array.elements()));
        case PureObject object                -> objectStructure(object);
        case UnaryPure unary                  -> OperandStructure.of(unaryKind(unary.opType()), unary.operand());
        case BinaryPurePure binary            ->
            OperandStructure.of(BINARY_KINDS.get(binary.opType()), binary.lp(), binary.rp());
        case BinaryValuePure binary           ->
            OperandStructure.of(BINARY_KINDS.get(binary.opType()), binary.lv(), binary.rp());
        case BinaryPureValue binary           ->
            OperandStructure.of(BINARY_KINDS.get(binary.opType()), binary.lp(), binary.rv());
        case NaryPure nary                    ->
            new OperandStructure(BINARY_KINDS.get(nary.opType()), withLeading(nary.valueResult(), nary.pures()));
        case LazyValuePure lazy               -> OperandStructure.of("boolean", lazy.p());
        case LazyAndPurePure and              -> OperandStructure.of("and", and.p1(), and.p2());
        case LazyOrPurePure or                -> OperandStructure.of("or", or.p1(), or.p2());
        case NaryBooleanPure junction         -> junctionStructure(junction);
        case RegexPrecompiledPure regex       ->
            OperandStructure.of(KIND_REGEX, regex.input(), Value.of(regex.patternSource()));
        case RegexPure regex                  -> OperandStructure.of(KIND_REGEX, regex.input(), regex.pattern());
        case SubtemplateValuePure subtemplate ->
            OperandStructure.of(KIND_SUBTEMPLATE, subtemplate.parent(), subtemplate.template());
        case SubtemplatePureValue subtemplate ->
            OperandStructure.of(KIND_SUBTEMPLATE, subtemplate.parent(), subtemplate.template());
        case SubtemplatePurePure subtemplate  ->
            OperandStructure.of(KIND_SUBTEMPLATE, subtemplate.parent(), subtemplate.template());
        case KeyStepPure step                 -> OperandStructure.of("step:key", step.base(), step.key());
        case IndexStepPure step               -> OperandStructure.of("step:index", step.base(), step.index());
        case WildcardStepPure step            -> OperandStructure.of("step:wildcard", step.base());
        case SliceStepPure step               ->
            OperandStructure.of("step:slice", step.base(), step.from(), step.to(), step.step());
        case ExpressionStepPure step          -> OperandStructure.of(KIND_EXPRESSION_STEP,
                step.baseValue() != null ? step.baseValue() : step.baseOp(), step.expr());
        case ExpressionStepPurePure step      -> OperandStructure.of(KIND_EXPRESSION_STEP, step.base(), step.expr());
        case ConditionStepConstBasePure step  ->
            OperandStructure.of(KIND_CONDITION_STEP, step.base(), step.condition());
        case ConditionStepPureConstCond step  ->
            OperandStructure.of(KIND_CONDITION_STEP, step.base(), step.condition());
        case ConditionStepPurePure step       ->
            OperandStructure.of(KIND_CONDITION_STEP, step.base(), step.condition());
        case RecursiveKeyStepPure step        -> OperandStructure.of("step:recursive-key", step.base(), step.key());
        case RecursiveIndexStepPure step      -> OperandStructure.of("step:recursive-index", step.base(), step.index());
        case RecursiveWildcardStepPure step   -> OperandStructure.of("step:recursive-wildcard", step.base());
        case IndexUnionStepPure step          ->
            new OperandStructure("step:index-union", withLeading(step.base(), step.indices()));
        case AttributeUnionStepPure step      ->
            new OperandStructure("step:key-union", withLeading(step.base(), step.attributes()));
        case NoArgsFunction function          -> OperandStructure.of("call", function.functionName());
        case PureFunction function            ->
            new OperandStructure("call", withLeading(function.functionName(), function.arguments()));
        case EachPure each                    -> OperandStructure.of("filter:each", each.base(), each.filter());
        case ExtendedFilterPure filter        -> extendedFilterStructure(filter);
        case StructuredOperator structured    -> structured.structure();
        // An operator of a kind the specification does not name is ordered by the
        // name of its class and keeps the order written among its kind.
        default -> OperandStructure.of(operator.getClass().getName());
        };
    }

    private static Map<BinaryOperatorType, String> binaryKinds() {
        val kinds = new EnumMap<BinaryOperatorType, String>(BinaryOperatorType.class);
        kinds.put(BinaryOperatorType.ADD, "binary:+");
        kinds.put(BinaryOperatorType.SUB, "binary:-");
        kinds.put(BinaryOperatorType.MUL, "binary:*");
        kinds.put(BinaryOperatorType.DIV, "binary:/");
        kinds.put(BinaryOperatorType.MOD, "binary:%");
        kinds.put(BinaryOperatorType.LT, "binary:<");
        kinds.put(BinaryOperatorType.LE, "binary:<=");
        kinds.put(BinaryOperatorType.GT, "binary:>");
        kinds.put(BinaryOperatorType.GE, "binary:>=");
        kinds.put(BinaryOperatorType.EQ, "binary:==");
        kinds.put(BinaryOperatorType.NE, "binary:!=");
        kinds.put(BinaryOperatorType.IN, "binary:in");
        kinds.put(BinaryOperatorType.ANY_IN, "binary:any in");
        kinds.put(BinaryOperatorType.ALL_IN, "binary:all in");
        kinds.put(BinaryOperatorType.HAS_ONE, "binary:has");
        kinds.put(BinaryOperatorType.HAS_ANY, "binary:has any");
        kinds.put(BinaryOperatorType.HAS_ALL, "binary:has all");
        kinds.put(BinaryOperatorType.XOR, "binary:^");
        kinds.put(BinaryOperatorType.REGEX, KIND_REGEX);
        return kinds;
    }

    private static String unaryKind(UnaryOperatorType type) {
        return switch (type) {
        case NOT    -> "unary:!";
        case NEGATE -> "unary:-";
        case PLUS   -> "unary:+";
        };
    }

    private static OperandStructure objectStructure(PureObject object) {
        val parts = new ArrayList<@Nullable Object>();
        for (var i = 0; i < object.keys().length; i++) {
            parts.add(object.keys()[i]);
            parts.add(object.entries().get(i));
        }
        return new OperandStructure("object", parts);
    }

    /**
     * A conjunction or disjunction holds the constant error it keeps in front of
     * its other operands. Left with one operand, it is that operand as a boolean.
     */
    private static OperandStructure junctionStructure(NaryBooleanPure junction) {
        val operands = withLeading(junction.pendingError(), junction.operands());
        if (operands.size() == 1) {
            return new OperandStructure("boolean", operands);
        }
        return new OperandStructure(Value.FALSE.equals(junction.shortCircuitValue()) ? "and" : "or", operands);
    }

    private static OperandStructure extendedFilterStructure(ExtendedFilterPure filter) {
        val parts = new ArrayList<@Nullable Object>(List.of(filter.base(), filter.filter()));
        for (val element : filter.path()) {
            parts.add(pathElementStructure(element, filter.pathAnalysis().compiledElements()));
        }
        return new OperandStructure("filter:extended", parts);
    }

    /**
     * An element of a filter path is ordered as the step of the same name without
     * its base.
     */
    private static OperandStructure pathElementStructure(PathElement element,
            Map<PathElement, CompiledExpression> compiledElements) {
        return switch (element) {
        case KeyPath(var key, var ignored)                      -> OperandStructure.of("step:key", key);
        case IndexPath(var index, var ignored)                  -> OperandStructure.of("step:index", index);
        case WildcardPath ignored                               -> OperandStructure.of("step:wildcard");
        case SlicePath(var from, var to, var step, var ignored) ->
            OperandStructure.of("step:slice", from, to, step == null ? Integer.valueOf(1) : step);
        case ExpressionPath ignored                             ->
            OperandStructure.of(KIND_EXPRESSION_STEP, compiledElements.get(element));
        case ConditionPath ignored                              ->
            OperandStructure.of(KIND_CONDITION_STEP, compiledElements.get(element));
        case RecursiveKeyPath(var key, var ignored)             -> OperandStructure.of("step:recursive-key", key);
        case RecursiveIndexPath(var index, var ignored)         -> OperandStructure.of("step:recursive-index", index);
        case RecursiveWildcardPath ignored                      -> OperandStructure.of("step:recursive-wildcard");
        case IndexUnionPath(var indices, var ignored)           ->
            new OperandStructure("step:index-union", List.copyOf(indices));
        case AttributeUnionPath(var keys, var ignored)          ->
            new OperandStructure("step:key-union", List.copyOf(keys));
        };
    }

    private static List<@Nullable Object> withLeading(@Nullable Object leading, List<?> rest) {
        val parts = new ArrayList<@Nullable Object>(rest.size() + 1);
        if (leading != null) {
            parts.add(leading);
        }
        parts.addAll(rest);
        return parts;
    }
}
