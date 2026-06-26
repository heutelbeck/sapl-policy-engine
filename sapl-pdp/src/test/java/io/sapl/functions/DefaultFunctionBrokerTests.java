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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionSpecification;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("DefaultFunctionBroker")
class DefaultFunctionBrokerTests {

    private DefaultFunctionBroker broker;

    @BeforeEach
    void setUp() {
        broker = new DefaultFunctionBroker();
    }

    @Test
    void whenLoadWithNullThenThrowsException() {
        assertThatThrownBy(() -> broker.load(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Library instance must not be null.");
    }

    @Test
    void whenEvaluateFunctionWithNullThenThrowsException() {
        assertThatThrownBy(() -> broker.evaluateFunction(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Function invocation must not be null.");
    }

    @Test
    void whenLoadLibraryWithoutAnnotationThenThrowsException() {
        class NotAnnotated {
            @SuppressWarnings("unused")
            public Value someMethod() {
                return Value.of("Nyarlathotep");
            }
        }
        val unannotated = new NotAnnotated();

        assertThatThrownBy(() -> broker.load(unannotated)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Provided class has no @FunctionLibrary annotation.");
    }

    @Test
    void whenLoadLibraryWithBlankNameThenUsesClassName() {
        broker.load(new LibraryWithoutName());

        val invocation = new FunctionInvocation("LibraryWithoutName.invoke", List.of());
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    @Test
    void whenLibraryHasMixedMethodsThenAllRegistered() {
        broker.load(new MixedMethodLibrary());

        val staticInvocation = new FunctionInvocation("nyarlathotep.summonMessenger", List.of());
        val staticResult     = broker.evaluateFunction(staticInvocation);

        assertThat(staticResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("Nyarlathotep approaches");

        val instanceInvocation = new FunctionInvocation("nyarlathotep.revealForm", List.of());
        val instanceResult     = broker.evaluateFunction(instanceInvocation);

        assertThat(instanceResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("Haunter of the Dark");

        val transformInvocation = new FunctionInvocation("nyarlathotep.transform", List.of(Value.of("Faceless God")));
        val transformResult     = broker.evaluateFunction(transformInvocation);

        assertThat(transformResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("Transformed to Faceless God");
    }

    @Test
    void whenLoadMultipleLibrariesThenAllSuccessful() {
        broker.load(new CthulhuLibrary());
        broker.load(new AzathothLibrary());

        val cthulhuInvocation  = new FunctionInvocation("cthulhu.summonEntity", List.of());
        val azathothInvocation = new FunctionInvocation("azathoth.chaos", List.of());

        assertThat(broker.evaluateFunction(cthulhuInvocation)).isNotInstanceOf(ErrorValue.class);
        assertThat(broker.evaluateFunction(azathothInvocation)).isNotInstanceOf(ErrorValue.class);
    }

    @Test
    void whenLoadingDuplicateFunctionSignatureThenThrowsException() {
        broker.load(new CthulhuLibrary());
        val duplicate = new DuplicateCthulhuLibrary();

        assertThatThrownBy(() -> broker.load(duplicate)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Function collision error for 'cthulhu.summonEntity'");
    }

    @Test
    void whenLoadingOverloadedFunctionsWithDifferentArityThenSucceeds() {
        broker.load(new YogSothothLibrary());

        val noArgsInvocation  = new FunctionInvocation("yog.gate", List.of());
        val oneArgInvocation  = new FunctionInvocation("yog.gate", List.of(Value.of("silver")));
        val twoArgsInvocation = new FunctionInvocation("yog.gate", List.of(Value.of("silver"), Value.of("key")));

        val noArgsResult  = broker.evaluateFunction(noArgsInvocation);
        val oneArgResult  = broker.evaluateFunction(oneArgInvocation);
        val twoArgsResult = broker.evaluateFunction(twoArgsInvocation);

        assertThat(noArgsResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("no gate");
        assertThat(oneArgResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("one gate");
        assertThat(twoArgsResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("two gate");
    }

    @Test
    void whenEvaluateNonExistentFunctionThenReturnsError() {
        val invocation = new FunctionInvocation("nonexistent.function", List.of());
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("No matching function found");
    }

    @Test
    void whenBestMatchingFunctionThenSelected() {
        broker.load(new ShubNiggurathLibrary());

        val stringInvocation = new FunctionInvocation("shub.transform", List.of(Value.of("goat")));
        val numberInvocation = new FunctionInvocation("shub.transform", List.of(Value.of(1000)));

        val stringResult = broker.evaluateFunction(stringInvocation);
        val numberResult = broker.evaluateFunction(numberInvocation);

        assertThat(stringResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("string transform");
        assertThat(numberResult).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo("number transform");
    }

    @Test
    void whenFunctionAddedOnceThenEvaluatesConsistently() {
        broker.load(new CthulhuLibrary());

        val invocation = new FunctionInvocation("cthulhu.summonEntity", List.of());
        val result1    = broker.evaluateFunction(invocation);
        val result2    = broker.evaluateFunction(invocation);

        assertThat(result1).isNotInstanceOf(ErrorValue.class).isInstanceOf(TextValue.class)
                .extracting(v -> ((TextValue) v).value()).isEqualTo("Rlyeh rises");
        assertThat(result2).isNotInstanceOf(ErrorValue.class).isInstanceOf(TextValue.class)
                .extracting(v -> ((TextValue) v).value()).isEqualTo("Rlyeh rises");
    }

    @Test
    void whenConcurrentFunctionEvaluationThenThreadSafe() throws Exception {
        broker.load(new CthulhuLibrary());

        val threadCount  = 1000;
        val latch        = new CountDownLatch(threadCount);
        val errors       = Collections.synchronizedList(new ArrayList<Throwable>());
        val successCount = new AtomicInteger(0);

        try (var executor = Executors.newFixedThreadPool(50)) {
            for (var i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        val invocation = new FunctionInvocation("cthulhu.summonEntity", List.of());
                        val result     = broker.evaluateFunction(invocation);

                        if (!(result instanceof ErrorValue)) {
                            successCount.incrementAndGet();
                        } else {
                            errors.add(
                                    new AssertionError("Function returned error: " + ((ErrorValue) result).message()));
                        }
                    } catch (Throwable throwable) {
                        errors.add(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> latch.getCount() == 0);

            assertThat(errors).isEmpty();
            assertThat(successCount.get()).isEqualTo(threadCount);
        }
    }

    @Test
    void whenConcurrentLibraryLoadingThenThreadSafe() throws Exception {
        val threadCount = 10;
        val latch       = new CountDownLatch(threadCount);
        val errors      = Collections.synchronizedList(new ArrayList<Throwable>());

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            IntStream.range(0, threadCount).forEach(i -> {
                executor.submit(() -> {
                    try {
                        if (i % 2 == 0) {
                            broker.load(new CthulhuLibrary());
                        } else {
                            broker.load(new AzathothLibrary());
                        }
                    } catch (IllegalStateException | IllegalArgumentException exception) {
                        if (!exception.getMessage().contains("collision")) {
                            errors.add(exception);
                        }
                    } catch (Throwable throwable) {
                        errors.add(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
            });

            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> latch.getCount() == 0);

            assertThat(errors).isEmpty();
        }
    }

    @Test
    void whenConcurrentLoadAndEvaluateThenThreadSafe() throws Exception {
        broker.load(new CthulhuLibrary());

        val operations = 1000;
        val latch      = new CountDownLatch(operations);
        val errors     = Collections.synchronizedList(new ArrayList<Throwable>());

        try (var executor = Executors.newFixedThreadPool(50)) {
            IntStream.range(0, operations).forEach(i -> {
                executor.submit(() -> {
                    try {
                        if (i % 100 == 0) {
                            broker.load(new AzathothLibrary());
                        } else {
                            val invocation = new FunctionInvocation("cthulhu.summonEntity", List.of());
                            val result     = broker.evaluateFunction(invocation);
                            if (result instanceof ErrorValue error) {
                                errors.add(new AssertionError("Evaluation failed: " + error.message()));
                            }
                        }
                    } catch (IllegalStateException | IllegalArgumentException exception) {
                        if (!exception.getMessage().contains("collision")) {
                            errors.add(exception);
                        }
                    } catch (Throwable throwable) {
                        errors.add(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
            });

            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> latch.getCount() == 0);

            assertThat(errors).isEmpty();
        }
    }

    @Test
    @DisplayName("concurrent overload loading does not corrupt the spec list read on the eval path")
    void whenConcurrentOverloadLoadAndEvaluateThenNoCorruption() throws Exception {
        val name   = "d7.fn";
        val errors = Collections.synchronizedList(new ArrayList<Throwable>());
        val done   = new AtomicBoolean(false);

        for (int arity = 0; arity < 4; arity++) {
            broker.loadFunction(overload(name, arity));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            executor.submit(() -> {
                try {
                    for (int arity = 4; arity < 2000; arity++) {
                        broker.loadFunction(overload(name, arity));
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.set(true);
                }
            });
            for (int reader = 0; reader < 6; reader++) {
                executor.submit(() -> {
                    while (!done.get()) {
                        try {
                            // Distinct args miss the cache, so invokeFunction iterates the live list.
                            broker.evaluateFunction(
                                    new FunctionInvocation(name, List.of(Value.of(Long.toString(System.nanoTime())))));
                        } catch (Throwable t) {
                            errors.add(t);
                        }
                    }
                });
            }
        }

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("ambiguous best match between a fixed-arity and a varargs spec returns an error instead of silently picking one")
    void whenTwoSpecsTieAtHighestMatchThenReturnsError() {
        val fixed   = new FunctionSpecification("ambiguous", "fn", List.of(TextValue.class), null,
                invocation -> Value.of("fixed"));
        val varargs = new FunctionSpecification("ambiguous", "fn", List.of(TextValue.class), TextValue.class,
                invocation -> Value.of("varargs"));

        broker.loadFunction(fixed);
        broker.loadFunction(varargs);

        val invocation = new FunctionInvocation("ambiguous.fn", List.of(Value.of("argument")));
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("ambiguous.fn");
    }

    private static FunctionSpecification overload(String fullName, int arity) {
        val dot       = fullName.indexOf('.');
        val namespace = fullName.substring(0, dot);
        val fn        = fullName.substring(dot + 1);
        val params    = new ArrayList<Class<? extends Value>>();
        for (int i = 0; i < arity; i++) {
            params.add(TextValue.class);
        }
        return new FunctionSpecification(namespace, fn, params, null, invocation -> Value.UNDEFINED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInvocationsWithExpectedResults")
    void whenEvaluatingFunctionsThenCorrectResults(String functionName, List<Value> params, String expectedResult) {
        broker.load(new CthulhuLibrary());
        broker.load(new YogSothothLibrary());

        val invocation = new FunctionInvocation(functionName, params);
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value())
                .isEqualTo(expectedResult);
    }

    static Stream<Arguments> provideInvocationsWithExpectedResults() {
        return Stream.of(arguments("cthulhu.summonEntity", List.of(), "Rlyeh rises"),
                arguments("cthulhu.awakeDreamer", List.of(Value.of("Hastur")), "Hastur awakens"),
                arguments("yog.gate", List.of(), "no gate"),
                arguments("yog.gate", List.of(Value.of("silver")), "one gate"),
                arguments("yog.gate", List.of(Value.of("silver"), Value.of("key")), "two gate"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInvalidInvocations")
    void whenEvaluatingInvalidInvocationsThenReturnsError(String functionName, List<Value> params) {
        broker.load(new CthulhuLibrary());

        val invocation = new FunctionInvocation(functionName, params);
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> provideInvalidInvocations() {
        return Stream.of(arguments("nonexistent.function", List.of()),
                arguments("cthulhu.summonEntity", List.of(Value.of("unexpected"))),
                arguments("cthulhu.awakeDreamer", List.of()), arguments("wrong.library", List.of()));
    }

    @FunctionLibrary(name = "cthulhu", description = "Functions for summoning Great Old Ones from Rlyeh")
    public static class CthulhuLibrary {

        @Function(docs = "Summons Cthulhu from the depths of Rlyeh")
        public static Value summonEntity() {
            return Value.of("Rlyeh rises");
        }

        @Function(docs = "Awakens a dreamer in Rlyeh")
        public static Value awakeDreamer(Value name) {
            if (name instanceof TextValue text) {
                return Value.of(text.value() + " awakens");
            }
            return Value.error("Name must be text.");
        }

        @Function(docs = "Summons multiple entities")
        public static Value summonMany(Value... entities) {
            return Value.of(entities.length + " entities summoned");
        }
    }

    @FunctionLibrary(name = "azathoth", description = "Functions invoking the Nuclear Chaos")
    public static class AzathothLibrary {

        @Function(docs = "Invokes the blind idiot god")
        public static Value chaos() {
            return Value.of("The daemon sultan stirs");
        }
    }

    @FunctionLibrary(name = "yog", description = "Functions for opening gates through space and time")
    public static class YogSothothLibrary {

        @Function(docs = "Opens gate with no parameters")
        public static Value gate() {
            return Value.of("no gate");
        }

        @Function(docs = "Opens gate with one key")
        public static Value gate(Value key) {
            return Value.of("one gate");
        }

        @Function(docs = "Opens gate with two keys")
        public static Value gate(Value key1, Value key2) {
            return Value.of("two gate");
        }
    }

    @FunctionLibrary(name = "shub", description = "The Black Goat of the Woods with a Thousand Young")
    public static class ShubNiggurathLibrary {

        @Function(docs = "Transforms string entities")
        public static Value transform(Value entity) {
            if (entity instanceof TextValue) {
                return Value.of("string transform");
            }
            return Value.of("number transform");
        }
    }

    @FunctionLibrary(name = "cthulhu", description = "Duplicate library for collision testing")
    public static class DuplicateCthulhuLibrary {

        @Function(docs = "Duplicate function for testing collision detection")
        public static Value summonEntity() {
            return Value.of("This should never execute");
        }
    }

    @FunctionLibrary
    public static class LibraryWithoutName {

        @Function
        public static Value invoke() {
            return Value.of("nameless invocation");
        }
    }

    @FunctionLibrary(name = "nyarlathotep", description = "The Crawling Chaos - mixed static and instance methods")
    public static class MixedMethodLibrary {

        private final String form;

        public MixedMethodLibrary() {
            this.form = "Haunter of the Dark";
        }

        @Function(docs = "Static method - summons the messenger")
        public static Value summonMessenger() {
            return Value.of("Nyarlathotep approaches");
        }

        @Function(docs = "Instance method - reveals current form")
        public Value revealForm() {
            return Value.of(form);
        }

        @Function(docs = "Instance method - transforms with given name")
        public Value transform(Value newForm) {
            if (newForm instanceof TextValue text) {
                return Value.of("Transformed to " + text.value());
            }
            return Value.error("Form must be text.");
        }
    }

}
