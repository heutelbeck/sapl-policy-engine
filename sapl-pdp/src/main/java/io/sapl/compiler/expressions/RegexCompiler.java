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

import io.sapl.api.model.*;
import io.sapl.ast.BinaryOperator;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiler for REGEX binary operations with pre-compilation optimization.
 * <p>
 * When the regex pattern is a compile-time constant (literal string), the
 * pattern is pre-compiled for better runtime performance. When the pattern
 * is dynamic (depends on runtime values), compilation occurs at evaluation
 * time.
 */
@UtilityClass
public class RegexCompiler {

    private static final String          ERROR_REGEX_INVALID        = "Invalid regular expression '%s': %s.";
    private static final String          ERROR_REGEX_MUST_BE_STRING = "Regular expression must be a string, but got: %s.";
    private static final BinaryOperation MATCHER                    = RegexCompiler::matchRegex;

    public static CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        val left  = ExpressionCompiler.compile(binaryOperation.left(), ctx);
        val right = ExpressionCompiler.compile(binaryOperation.right(), ctx);
        val loc   = binaryOperation.location();

        if (left instanceof ErrorValue) {
            return left;
        }
        if (right instanceof ErrorValue) {
            return right;
        }

        // Pre-compile regex when pattern is a literal string.
        if (right instanceof TextValue(String value)) {
            Predicate<String> matcher;
            try {
                matcher = Pattern.compile(value).asMatchPredicate();
            } catch (PatternSyntaxException e) {
                throw new SaplCompilerException(ERROR_REGEX_INVALID.formatted(value, e.getMessage()), e,
                        binaryOperation);
            }
            if (left instanceof Value lv) {
                return matchRegex(lv, matcher);
            }
            if (left instanceof StreamOperator ls) {
                return new RegexPrecompiledStream(ls, matcher, loc);
            }
            return new RegexPrecompiledPure((PureOperator) left, value, matcher, loc);
        }

        // Runtime path: pattern is not a literal. Reject non-Pure/non-Stream.
        if (!(right instanceof PureOperator) && !(right instanceof StreamOperator)) {
            return Value.errorAt(loc, ERROR_REGEX_MUST_BE_STRING, right);
        }
        if (left instanceof StreamOperator || right instanceof StreamOperator) {
            return new RegexStream(left, right, loc);
        }
        return new RegexPure(left, right, loc);
    }

    static Value matchRegex(Value input, Predicate<String> matcher) {
        if (!(input instanceof TextValue(String value))) {
            return Value.FALSE; // Non-text doesn't match
        }
        return matcher.test(value) ? Value.TRUE : Value.FALSE;
    }

    static Value matchRegex(Value input, Value pattern, SourceLocation loc) {
        if (!(pattern instanceof TextValue(String patternText))) {
            return Value.errorAt(loc, ERROR_REGEX_MUST_BE_STRING, pattern);
        }
        if (!(input instanceof TextValue(String inputText))) {
            return Value.FALSE; // Non-text doesn't match
        }
        try {
            return Pattern.matches(patternText, inputText) ? Value.TRUE : Value.FALSE;
        } catch (PatternSyntaxException e) {
            return Value.errorAt(loc, ERROR_REGEX_INVALID, patternText, e.getMessage());
        }
    }

    /**
     * Pre-compiled regex against a {@link PureOperator} input. The
     * {@link Predicate} cached at compile time bypasses per-evaluation
     * pattern compilation.
     */
    record RegexPrecompiledPure(
            PureOperator input,
            String patternSource,
            Predicate<String> matcher,
            SourceLocation location) implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(RegexPrecompiledPure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val v = input.evaluate(ctx);
            if (v instanceof ErrorValue) {
                return v;
            }
            return matchRegex(v, matcher);
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
            return SemanticHashing.ordered(KIND, input.semanticHash(), patternSource.hashCode());
        }
    }

    /**
     * Pre-compiled regex against a {@link StreamOperator} input.
     */
    record RegexPrecompiledStream(StreamOperator input, Predicate<String> matcher, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(1);
            val v    = evalChild(input, ctx, deps);
            if (v == null || v instanceof ErrorValue) {
                return new ExpressionResult(v, deps);
            }
            return new ExpressionResult(matchRegex(v, matcher), deps);
        }
    }

    /**
     * Runtime-compiled regex; both {@code input} and {@code pattern} are
     * {@link Value} or {@link PureOperator}. The {@link StreamOperator}
     * branch is unreachable by construction and folds to an
     * {@link ErrorValue} defensively.
     */
    record RegexPure(CompiledExpression input, CompiledExpression pattern, SourceLocation location)
            implements PureOperator {
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
            return matchRegex(i, p, location);
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
            val ih = input instanceof Value v ? (long) v.hashCode() : ((PureOperator) input).semanticHash();
            val ph = pattern instanceof Value v ? (long) v.hashCode() : ((PureOperator) pattern).semanticHash();
            return SemanticHashing.ordered(KIND, ih, ph);
        }
    }

    /**
     * Runtime-compiled regex with at least one of {@code input} or
     * {@code pattern} being a {@link StreamOperator}. Delegates to the
     * binary-op eager evaluator which walks both children to accumulate
     * the maximum subscription set.
     */
    record RegexStream(CompiledExpression input, CompiledExpression pattern, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return MATCHER.evalEager(input, pattern, location, ctx);
        }
    }

}
