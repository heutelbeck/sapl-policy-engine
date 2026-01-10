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
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiler for REGEX binary operations with pre-compilation optimization.
 * <p>
 * When the regex pattern is a compile-time constant (literal string), the
 * pattern is pre-compiled for better runtime performance. When the pattern
 * is dynamic (depends on runtime values), compilation occurs at evaluation
 * time.
 */
public class RegexCompiler {

    private static final String ERROR_REGEX_MUST_BE_STRING = "Regular expression must be a string, but got: %s.";
    private static final String ERROR_REGEX_INVALID        = "Invalid regular expression '%s': %s.";

    public CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        val left  = ExpressionCompiler.compile(binaryOperation.left(), ctx);
        val right = ExpressionCompiler.compile(binaryOperation.right(), ctx);
        val loc   = binaryOperation.location();

        if (left instanceof ErrorValue) {
            return left;
        }
        if (right instanceof ErrorValue) {
            return right;
        }

        // Pre-compile regex when pattern is a literal string
        if (right instanceof TextValue(String value)) {
            Predicate<String> matcher;
            try {
                matcher = Pattern.compile(value).asMatchPredicate();
            } catch (PatternSyntaxException e) {
                throw new SaplCompilerException(ERROR_REGEX_INVALID.formatted(value, e.getMessage()), e,
                        binaryOperation);
            }
            return switch (left) {
            case Value lv         -> matchRegex(lv, matcher);
            case PureOperator lp  -> new RegexPrecompiledPure(lp, matcher, loc);
            case StreamOperator s -> new RegexPrecompiledStream(s, matcher, loc);
            };
        }

        // Runtime regex compilation when pattern is not a literal
        if (!(right instanceof PureOperator) && !(right instanceof StreamOperator)) {
            return Value.errorAt(loc, ERROR_REGEX_MUST_BE_STRING, right);
        }

        return switch (left) {
        case Value lv         -> switch (right) {
                          case PureOperator rp       -> new RegexValuePure(lv, rp, loc);
                          case StreamOperator rs     -> new RegexValueStream(lv, rs, loc);
                          default                    -> throw new IllegalStateException();
                          };
        case PureOperator lp  -> switch (right) {
                          case PureOperator rp       -> new RegexPurePure(lp, rp, loc);
                          case StreamOperator rs     -> new RegexPureStream(lp, rs, loc);
                          default                    -> throw new IllegalStateException();
                          };
        case StreamOperator s -> switch (right) {
                          case PureOperator rp       -> new RegexStreamPure(s, rp, loc);
                          case StreamOperator rs     -> new RegexStreamStream(s, rs, loc);
                          default                    -> throw new IllegalStateException();
                          };
        };
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

    public record RegexPrecompiledPure(PureOperator input, Predicate<String> matcher, SourceLocation location)
            implements PureOperator {
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
    }

    public record RegexPrecompiledStream(StreamOperator input, Predicate<String> matcher, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return input.stream().map(tv -> {
                val v = tv.value();
                if (v instanceof ErrorValue) {
                    return tv;
                }
                return new TracedValue(matchRegex(v, matcher), tv.contributingAttributes());
            });
        }
    }

    public record RegexValuePure(Value input, PureOperator pattern, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val p = pattern.evaluate(ctx);
            if (p instanceof ErrorValue) {
                return p;
            }
            return matchRegex(input, p, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return pattern.isDependingOnSubscription();
        }
    }

    public record RegexPurePure(PureOperator input, PureOperator pattern, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val i = input.evaluate(ctx);
            if (i instanceof ErrorValue) {
                return i;
            }
            val p = pattern.evaluate(ctx);
            if (p instanceof ErrorValue) {
                return p;
            }
            return matchRegex(i, p, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return input.isDependingOnSubscription() || pattern.isDependingOnSubscription();
        }
    }

    public record RegexValueStream(Value input, StreamOperator pattern, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return pattern.stream().map(tv -> {
                val p = tv.value();
                if (p instanceof ErrorValue) {
                    return tv;
                }
                return new TracedValue(matchRegex(input, p, location), tv.contributingAttributes());
            });
        }
    }

    public record RegexPureStream(PureOperator input, StreamOperator pattern, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val i = input.evaluate(ctx.get(EvaluationContext.class));
                if (i instanceof ErrorValue) {
                    return Flux.just(new TracedValue(i, List.of()));
                }
                return pattern.stream().map(tv -> {
                    val p = tv.value();
                    if (p instanceof ErrorValue) {
                        return tv;
                    }
                    return new TracedValue(matchRegex(i, p, location), tv.contributingAttributes());
                });
            });
        }
    }

    public record RegexStreamPure(StreamOperator input, PureOperator pattern, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val p = pattern.evaluate(ctx.get(EvaluationContext.class));
                if (p instanceof ErrorValue) {
                    return Flux.just(new TracedValue(p, List.of()));
                }
                return input.stream().map(tv -> {
                    val i = tv.value();
                    if (i instanceof ErrorValue) {
                        return tv;
                    }
                    return new TracedValue(matchRegex(i, p, location), tv.contributingAttributes());
                });
            });
        }
    }

    public record RegexStreamStream(StreamOperator input, StreamOperator pattern, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.combineLatest(input.stream(), pattern.stream(), (ti, tp) -> {
                var combined = new ArrayList<>(ti.contributingAttributes());
                combined.addAll(tp.contributingAttributes());
                val i = ti.value();
                if (i instanceof ErrorValue) {
                    return new TracedValue(i, combined);
                }
                val p = tp.value();
                if (p instanceof ErrorValue) {
                    return new TracedValue(p, combined);
                }
                return new TracedValue(matchRegex(i, p, location), combined);
            });
        }
    }

}
