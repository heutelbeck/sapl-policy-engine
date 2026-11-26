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

import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.BoundFunction;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.NoMatchingFunctionException;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.functions.DefaultFunctionBroker;

import lombok.val;

@DisplayName("Compile-time function binding")
class FunctionBindingCompilerTests {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "missing.call()", "missing.call(subject)", "missing.call(<test.input>)", "missing.call(1)",
            "missing.call(1 / 0)", "false && missing.call(subject)", "subject |- missing.call",
            "[subject] |- each missing.call", "[] |- each missing.call", "(1 / 0) |- missing.call",
            "subject |- { @.room : missing.call }", "subject |- { each @.room : missing.call }" })
    @DisplayName("an unavailable function fails compilation wherever its call is compiled")
    void whenFunctionIsMissingThenCompilationFailsAtItsCall(String source) {
        val broker = new DefaultFunctionBroker();

        assertThatThrownBy(() -> compileExpression(source, broker)).isInstanceOfSatisfying(SaplCompilerException.class,
                failure -> assertThat(failure.getLocation()).isNotNull()).hasMessageContaining("missing.call");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "binding.gap(subject)", "binding.gap(1)", "binding.gap(<test.input>)" })
    @DisplayName("a registered name with only zero- and two-argument overloads rejects one argument")
    void whenNoOverloadHasTheCallArityThenCompilationFails(String source) {
        val broker = new DefaultFunctionBroker();
        broker.load(new BindingLibrary());

        assertThatThrownBy(() -> compileExpression(source, broker)).isInstanceOfSatisfying(SaplCompilerException.class,
                failure -> assertThat(failure.getLocation()).isNotNull()).hasMessageContaining("binding.gap");
    }

    @Test
    @DisplayName("equally matching variadic overloads fail at their call before evaluation")
    void whenVariadicOverloadsAreAmbiguousThenCompilationFails() {
        val broker = new DefaultFunctionBroker();
        broker.load(new BindingLibrary());

        assertThatThrownBy(() -> compileExpression("binding.choice(subject, action)", broker))
                .isInstanceOfSatisfying(SaplCompilerException.class,
                        failure -> assertThat(failure.getLocation()).isNotNull())
                .hasMessageContaining("binding.choice");
    }

    @Test
    @DisplayName("the unavailable call keeps the source and document of its own policy")
    void whenCompilingAnotherDocumentThenTheFailurePointsToThatDocumentsCall() {
        val context = new CompilationContext(new DefaultFunctionBroker());
        DocumentCompiler.compileDocument("policy \"first\" permit", context);
        val source = "policy \"second\" permit missing.call(subject);";

        assertThatThrownBy(() -> DocumentCompiler.compileDocument(source, context)).isInstanceOfSatisfying(
                SaplCompilerException.class,
                failure -> assertThat(failure.getLocation()).isNotNull().satisfies(location -> {
                    assertThat(location.documentName()).isEqualTo("second");
                    assertThat(location.documentSource()).isEqualTo(source);
                    assertThat(location.start()).isEqualTo(source.indexOf("missing.call"));
                }));
    }

    @Test
    @DisplayName("the parent name failure precedes a separately diagnosed child name failure")
    void whenParentAndArgumentFunctionsAreMissingThenBothCallSitesAreReported() {
        val broker = new DefaultFunctionBroker();

        assertThatThrownBy(() -> compileExpression("missing.outer(missing.inner(subject))", broker))
                .isInstanceOfSatisfying(SaplCompilerException.class,
                        failure -> assertThat(failure.diagnostics()).hasSize(2).satisfiesExactly(
                                parent -> assertThat(parent.getMessage()).contains("missing.outer"),
                                child -> assertThat(child.getMessage()).contains("missing.inner")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "binding.gap()", "binding.gap(1, 2)", "binding.choice()", "binding.choice(1)" })
    @DisplayName("available exact overloads retain ordinary constant folding")
    void whenOverloadHasAnExactArityThenTheCallStillFolds(String source) {
        val broker = new DefaultFunctionBroker();
        broker.load(new BindingLibrary());

        assertThat(compileExpression(source, broker)).isInstanceOf(Value.class);
    }

    @Test
    @DisplayName("a host-supplied call object does not change existing constant-fold identity")
    void whenHostUsesACallObjectThenRepeatedConstantsStillFoldOnce() {
        val broker = new FixedFunctionBroker();

        assertThat(compileExpression("[host.constant(1), host.constant(1)]", broker))
                .isEqualTo(Value.ofArray(Value.of("same"), Value.of("same")));
        assertThat(broker.bodies).hasValue(1);
    }

    private static final class FixedFunctionBroker implements FunctionBroker {
        private final AtomicInteger bodies = new AtomicInteger();

        @Override
        public BoundFunction bindFunction(String functionName, int argumentCount) {
            if (!"host.constant".equals(functionName) || argumentCount != 1) {
                throw new NoMatchingFunctionException("No matching host function.");
            }
            return new BoundFunction() {
                @Override
                public Value evaluate(FunctionInvocation invocation) {
                    bodies.incrementAndGet();
                    return Value.of("same");
                }
            };
        }

        @Override
        public Value evaluateFunction(FunctionInvocation invocation) {
            try {
                return bindFunction(invocation.functionName(), invocation.argumentCount()).evaluate(invocation);
            } catch (IllegalArgumentException failure) {
                return Value.error(failure.getMessage());
            }
        }

        @Override
        public void load(Object libraryInstance) {
            // This host declares its functions directly.
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of();
        }
    }

    @FunctionLibrary(name = "binding", description = "Functions with explicit fixed and variadic arities.")
    public static final class BindingLibrary {

        @Function(name = "gap", docs = "Returns the zero-argument alternative.")
        public static Value noArguments() {
            return Value.of(0);
        }

        @Function(name = "gap", docs = "Returns the two-argument alternative.")
        public static Value twoArguments(Value first, Value second) {
            return Value.ofArray(first, second);
        }

        @Function(name = "choice", docs = "Accepts zero or more values.")
        public static Value anyArguments(Value... values) {
            return Value.of(values.length);
        }

        @Function(name = "choice", docs = "Accepts one or more values.")
        public static Value oneOrMoreArguments(Value first, Value... values) {
            return Value.ofArray(first, Value.of(values.length));
        }
    }
}
