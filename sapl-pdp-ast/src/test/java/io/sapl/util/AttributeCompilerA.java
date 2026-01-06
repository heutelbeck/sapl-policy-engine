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
package io.sapl.util;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.ast.AttributeStep;
import io.sapl.ast.EnvironmentAttribute;
import io.sapl.ast.Expression;
import io.sapl.compiler.AttributeOptionsCompiler;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.ExpressionCompiler;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.AttributeOptionsCompiler.*;

/**
 * Evaluates attribute finder expressions (PIPs).
 * <p>
 * Handles both environment attributes ({@code <time.now>}) and entity
 * attributes ({@code subject.<pip.role>}). Returns
 * {@link StreamOperator}
 * wrapping the attribute stream from the broker.
 */
@UtilityClass
public class AttributeCompilerA {

    private static final String ERROR_BROKER_NOT_CONFIGURED = "AttributeBroker not configured in evaluation context.";

    public static CompiledExpression compileEnvironmentAttribute(EnvironmentAttribute attr, CompilationContext ctx) {
        return compileEnvironmentAttribute(attr.name().full(), attr.arguments(), attr.options(), attr.head(),
                attr.location(), ctx);
    }

    public static CompiledExpression compileAttributeStep(AttributeStep attr, CompilationContext ctx) {
        return compileAttribute(attr.base(), attr.name().full(), attr.arguments(), attr.options(), attr.head(),
                attr.location(), ctx);
    }

    private static CompiledExpression compileEnvironmentAttribute(String name, @NonNull List<Expression> arguments,
            Expression options, boolean head, @NonNull SourceLocation location, CompilationContext ctx) {
        return compileAttribute(null, name, arguments, options, head, location, ctx);
    }

    record PureAtIndex(PureOperator pureOperator, int index) {}

    record TracedValueAtIndex(TracedValue pureOperator, int index) {}

    private static CompiledExpression compileAttribute(Expression entity, String full,
            @NonNull List<Expression> arguments, Expression options, boolean head, @NonNull SourceLocation location,
            CompilationContext ctx) {
        val compiledOptions = AttributeOptionsCompiler.compileOptions(options, location, ctx);
        if (compiledOptions instanceof ErrorValue) {
            return compiledOptions;
        }

        val argumentValues    = new Value[arguments.size() + (entity != null ? 1 : 0)];
        val compiledArguments = new ArrayList<CompiledExpression>(arguments.size());
        val indexedPure       = new ArrayList<PureAtIndex>(arguments.size());
        val indexedStreams    = new ArrayList<Flux<TracedValueAtIndex>>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            val compiledArgument = ExpressionCompiler.compile(arguments.get(i), ctx);
            if (compiledArgument instanceof ErrorValue) {
                return compiledArgument;
            }
            int finalI = i;
            switch (compiledArgument) {
            case Value value       -> argumentValues[i] = value;
            case PureOperator po   -> indexedPure.add(new PureAtIndex(po, i));
            case StreamOperator so -> indexedStreams.add(so.stream().map(tv -> new TracedValueAtIndex(tv, finalI)));
            }
            compiledArguments.add(compiledArgument);
        }

        return null;
    }

    public record AttributeOperator(
            Value[] argumentValues,
            List<PureAtIndex> pureArguments,
            List<Flux<TracedValueAtIndex>> indexedStreamArguments,
            CompiledExpression options,
            boolean isEnvironmentAttribute,
            boolean isAttributeHead) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {

            return null;
        }
    }

    /**
     * Evaluates argument expressions. Returns List<Value> on success, ErrorValue on
     * failure.
     * Undefined arguments are dropped.
     */
    private static Object evaluateArguments(List<Expression> argumentExpressions, CompilationContext ctx) {
        var arguments = new ArrayList<Value>(argumentExpressions.size());
        for (var argExpr : argumentExpressions) {
            var result = ExpressionCompiler.compile(argExpr, ctx);
            if (result instanceof StreamOperator)
                return Value.error("Stream expressions not supported as attribute arguments.");
            if (result instanceof ErrorValue error)
                return error;
            if (!(result instanceof UndefinedValue))
                arguments.add((Value) result);
        }
        return arguments;
    }

    /**
     * Invokes the attribute broker and returns the stream.
     */
    private static StreamOperator invokeAttribute(String attributeName, Value entity, List<Value> arguments,
            boolean head, Value options, SourceLocation location, EvaluationContext ctx) {
        var configurationId = ctx.configurationId() != null ? ctx.configurationId() : "default";
        var timeout         = Duration.ofMillis(longOption(options, OPTION_INITIAL_TIMEOUT, DEFAULT_TIMEOUT_MS));
        var pollInterval    = Duration.ofMillis(longOption(options, OPTION_POLL_INTERVAL, DEFAULT_POLL_INTERVAL_MS));
        var backoff         = Duration.ofMillis(longOption(options, OPTION_BACKOFF, DEFAULT_BACKOFF_MS));
        var retries         = longOption(options, OPTION_RETRIES, DEFAULT_RETRIES);
        var fresh           = freshOption(options);

        var invocation = new AttributeFinderInvocation(configurationId, attributeName, entity, arguments,
                ctx.variables(), timeout, pollInterval, backoff, retries, fresh);

        var broker = ctx.attributeBroker();
        if (broker == null)
            return errorStream(invocation, location, Value.error(ERROR_BROKER_NOT_CONFIGURED));

        var stream = broker.attributeStream(invocation).map(value -> {
            var attributeRecord = new AttributeRecord(invocation, value, Instant.now(), location);
            return new TracedValue(value, List.of(attributeRecord));
        });

        if (head)
            stream = stream.take(1);

        return null; // new StreamOperator(stream);
    }

    private static StreamOperator errorStream(AttributeFinderInvocation invocation, SourceLocation location,
            ErrorValue error) {
        return null;
    }

    private static long longOption(Value options, String key, long defaultValue) {
        if (!(options instanceof ObjectValue obj))
            return defaultValue;
        var value = obj.get(key);
        if (value instanceof NumberValue(BigDecimal value1))
            return value1.longValue();
        return defaultValue;
    }

    private static boolean freshOption(Value options) {
        if (!(options instanceof ObjectValue obj))
            return false;
        var value = obj.get(AttributeOptionsCompiler.OPTION_FRESH);
        if (value instanceof BooleanValue(boolean value1))
            return value1;
        return false;
    }

    private static List<AttributeRecord> mergeRecords(List<AttributeRecord> first, List<AttributeRecord> second) {
        if (first.isEmpty())
            return second;
        if (second.isEmpty())
            return first;
        var merged = new ArrayList<AttributeRecord>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

}
