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

import io.sapl.compiler.expressions.BooleanGuard.BooleanResult;

import com.heutelbeck.rustregex4j.IsMatch;
import com.heutelbeck.rustregex4j.Regex;
import com.heutelbeck.rustregex4j.RegexError;
import com.heutelbeck.rustregex4j.RegexLimits;
import io.sapl.api.model.*;
import io.sapl.ast.BinaryOperator;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.util.LinearRegex;
import io.sapl.compiler.util.RegexRefusedException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiler for REGEX binary operations with pre-compilation optimization.
 * <p>
 * The pattern must match the whole text. Patterns are compiled by
 * {@link LinearRegex#compileWholeText(String)} and matched in linear time
 * without backtracking. A text the engine cannot search, because it exceeds
 * the text limit or holds an unpaired surrogate, yields an error.
 * <p>
 * When the regex pattern is a compile-time constant (literal string), the
 * pattern is pre-compiled for better runtime performance. When the pattern
 * is dynamic (depends on runtime values), compilation occurs at evaluation
 * time.
 */
@UtilityClass
public class RegexCompiler {

    private static final String ERROR_REGEX_INVALID        = "Invalid regular expression '%s': %s.";
    private static final String ERROR_REGEX_MATCH_FAILED   = "Matching the regular expression '%s' failed: %s.";
    private static final String ERROR_REGEX_MUST_BE_STRING = "Regular expression must be a string, but got: %s.";

    public static CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        val operands = CompilationChildren.pair(() -> ExpressionCompiler.compile(binaryOperation.left(), ctx),
                () -> preparePattern(binaryOperation, ctx));
        val left     = operands.first();
        val right    = operands.second().operand();
        val loc      = binaryOperation.location();

        if (right instanceof TextValue(String value)) {
            val compiled = operands.second().pattern();
            if (left instanceof ErrorValue) {
                return left;
            }
            if (left instanceof Value lv) {
                return matchRegex(lv, compiled, loc);
            }
            if (left instanceof StreamOperator ls) {
                return new RegexPrecompiledStream(ls, compiled, loc);
            }
            return new RegexPrecompiledPure((PureOperator) left, value, compiled, loc);
        }

        if (left instanceof ErrorValue) {
            return left;
        }
        if (right instanceof ErrorValue) {
            return right;
        }

        // Runtime path: pattern is not a literal. Reject non-Pure/non-Stream.
        if (!(right instanceof PureOperator) && !(right instanceof StreamOperator)) {
            return Value.errorAt(loc, ERROR_REGEX_MUST_BE_STRING, right);
        }
        if (left instanceof StreamOperator || right instanceof StreamOperator) {
            return new RegexStream(left, right, ctx.regexLimits(), loc);
        }
        return new RegexPure(left, right, ctx.regexLimits(), loc);
    }

    private record PreparedPattern(CompiledExpression operand, Regex pattern) {}

    private static PreparedPattern preparePattern(BinaryOperator operation, CompilationContext ctx) {
        val operand = ExpressionCompiler.compile(operation.right(), ctx);
        if (operand instanceof TextValue(String value)) {
            return switch (LinearRegex.compileWholeText(value, ctx.regexLimits())) {
            case Regex regex      -> new PreparedPattern(operand, regex);
            case RegexError error -> {
                val description = LinearRegex.describe(error);
                throw new SaplCompilerException(ERROR_REGEX_INVALID.formatted(value, description),
                        new RegexRefusedException(value, description), operation);
            }
            };
        }
        return new PreparedPattern(operand, null);
    }

    static Value matchRegex(Value input, Regex pattern, SourceLocation loc) {
        if (!(input instanceof TextValue(String value))) {
            return Value.FALSE; // Non-text doesn't match
        }
        return switch (pattern.isMatch(value)) {
        case IsMatch(var matches) -> matches ? Value.TRUE : Value.FALSE;
        case RegexError error     ->
            Value.errorAt(loc, ERROR_REGEX_MATCH_FAILED, pattern.pattern(), LinearRegex.describe(error));
        };
    }

    static Value matchRegex(Value input, Value pattern, RegexLimits limits, SourceLocation loc) {
        if (!(pattern instanceof TextValue(String patternText))) {
            return Value.errorAt(loc, ERROR_REGEX_MUST_BE_STRING, pattern);
        }
        if (!(input instanceof TextValue)) {
            return Value.FALSE; // Non-text doesn't match
        }
        return switch (LinearRegex.compileWholeText(patternText, limits)) {
        case Regex regex      -> matchRegex(input, regex, loc);
        case RegexError error -> Value.errorAt(loc, ERROR_REGEX_INVALID, patternText, LinearRegex.describe(error));
        };
    }

    /**
     * Pre-compiled regex against a {@link PureOperator} input. The
     * {@link Regex} cached at compile time bypasses per-evaluation
     * pattern compilation.
     */
    public record RegexPrecompiledPure(PureOperator input, String patternSource, Regex pattern, SourceLocation location)
            implements PureOperator, BooleanResult {
        private static final long KIND = SemanticHashing.kindHash(RegexPrecompiledPure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val v = input.evaluate(ctx);
            if (v instanceof ErrorValue) {
                return v;
            }
            return matchRegex(v, pattern, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return input.isDependingOnSubscription();
        }

        @Override
        public boolean isRelativeExpression() {
            return input.isRelativeExpression();
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(KIND, input.semanticHash(), SemanticHashing.textHash(patternSource));
        }
    }

    /**
     * Pre-compiled regex against a {@link StreamOperator} input.
     */
    record RegexPrecompiledStream(StreamOperator input, Regex pattern, SourceLocation location)
            implements StreamOperator, BooleanResult {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(1);
            val v    = evalChild(input, ctx, deps);
            if (v == null || v instanceof ErrorValue) {
                return new ExpressionResult(v, deps);
            }
            return new ExpressionResult(matchRegex(v, pattern, location), deps);
        }
    }

    /**
     * Runtime-compiled regex; both {@code input} and {@code pattern} are
     * {@link Value} or {@link PureOperator}. The {@link StreamOperator}
     * branch is unreachable by construction and folds to an
     * {@link ErrorValue} defensively.
     */
    record RegexPure(CompiledExpression input, CompiledExpression pattern, RegexLimits limits, SourceLocation location)
            implements PureOperator, BooleanResult {
        private static final long KIND = SemanticHashing.kindHash(RegexPure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val i = switch (input) {
            case Value v                -> v;
            case PureOperator p         -> p.evaluate(ctx);
            case StreamOperator ignored -> Value.errorAt(location, ERROR_REGEX_MUST_BE_STRING, input);
            };
            if (i instanceof ErrorValue) {
                return i;
            }
            val p = switch (pattern) {
            case Value v                -> v;
            case PureOperator po        -> po.evaluate(ctx);
            case StreamOperator ignored -> Value.errorAt(location, ERROR_REGEX_MUST_BE_STRING, pattern);
            };
            if (p instanceof ErrorValue) {
                return p;
            }
            return matchRegex(i, p, limits, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return (input instanceof PureOperator ip && ip.isDependingOnSubscription())
                    || (pattern instanceof PureOperator pp && pp.isDependingOnSubscription());
        }

        @Override
        public boolean isRelativeExpression() {
            return (input instanceof PureOperator ip && ip.isRelativeExpression())
                    || (pattern instanceof PureOperator pp && pp.isRelativeExpression());
        }

        @Override
        public long semanticHash() {
            val ih = input instanceof Value v ? SemanticHashing.valueHash(v) : ((PureOperator) input).semanticHash();
            val ph = pattern instanceof Value v ? SemanticHashing.valueHash(v)
                    : ((PureOperator) pattern).semanticHash();
            return SemanticHashing.ordered(KIND, ih, ph);
        }
    }

    /**
     * Runtime-compiled regex with at least one of {@code input} or
     * {@code pattern} being a {@link StreamOperator}. Delegates to the
     * binary-op eager evaluator which walks both children to accumulate
     * the maximum subscription set.
     */
    record RegexStream(
            CompiledExpression input,
            CompiledExpression pattern,
            RegexLimits limits,
            SourceLocation location) implements StreamOperator, BooleanResult {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            final BinaryOperation matcher = (text, regex, loc) -> matchRegex(text, regex, limits, loc);
            return matcher.evalEager(input, pattern, location, ctx);
        }
    }

}
