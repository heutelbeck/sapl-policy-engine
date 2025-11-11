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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.interpreter.InitializationException;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFunctionBrokerTests {

    private DefaultFunctionBroker broker;

    @BeforeEach
    void setUp() {
        broker = new DefaultFunctionBroker();
    }

    @Test
    void loadStaticFunctionLibraryWithNullThrowsException() {
        assertThatThrownBy(() -> broker.loadStaticFunctionLibrary(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Library class must not be null.");
    }

    @Test
    void loadFunctionInstanceLibraryWithNullThrowsException() {
        assertThatThrownBy(() -> broker.loadFunctionInstanceLibrary(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Library instance must not be null.");
    }

    @Test
    void evaluateFunctionWithNullThrowsException() {
        assertThatThrownBy(() -> broker.evaluateFunction(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Function invocation must not be null.");
    }

    @Test
    void loadLibraryWithoutAnnotationThrowsException() {
        class NotAnnotated {
            public Value someMethod() {
                return Value.of("Nyarlathotep");
            }
        }

        assertThatThrownBy(() -> broker.loadStaticFunctionLibrary(NotAnnotated.class))
                .isInstanceOf(InitializationException.class)
                .hasMessage("Provided class has no @FunctionLibrary annotation.");
    }

    @Test
    void instanceMethodsWorkWithInstanceLibrary() throws InitializationException {
        broker.loadFunctionInstanceLibrary(new MixedMethodLibrary());

        val staticInvocation = new FunctionInvocation("nyarlathotep.summonMessenger", List.of());
        val staticResult     = broker.evaluateFunction(staticInvocation);

        assertThat(staticResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) staticResult).value()).isEqualTo("Nyarlathotep approaches");

        val instanceInvocation = new FunctionInvocation("nyarlathotep.revealForm", List.of());
        val instanceResult     = broker.evaluateFunction(instanceInvocation);

        assertThat(instanceResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) instanceResult).value()).isEqualTo("Haunter of the Dark");

        val transformInvocation = new FunctionInvocation("nyarlathotep.transform", List.of(Value.of("Faceless God")));
        val transformResult     = broker.evaluateFunction(transformInvocation);

        assertThat(transformResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) transformResult).value()).isEqualTo("Transformed to Faceless God");
    }

    @Test
    void instanceMethodsNotAvailableInStaticLibrary() {
        assertThatThrownBy(() -> broker.loadStaticFunctionLibrary(MixedMethodLibrary.class))
                .isInstanceOf(InitializationException.class)
                .hasMessageContaining("must be static when no library instance is provided");
    }

    @Test
    void staticMethodsFromMixedLibraryWorkInInstanceMode() throws InitializationException {
        broker.loadFunctionInstanceLibrary(new MixedMethodLibrary());

        val staticInvocation = new FunctionInvocation("nyarlathotep.summonMessenger", List.of());
        val staticResult     = broker.evaluateFunction(staticInvocation);

        assertThat(staticResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) staticResult).value()).isEqualTo("Nyarlathotep approaches");
    }

    @Test
    void loadLibraryWithBlankNameUsesClassName() throws InitializationException {
        broker.loadStaticFunctionLibrary(LibraryWithoutName.class);

        val invocation = new FunctionInvocation("LibraryWithoutName.invoke", List.of());
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    @Test
    void loadMultipleLibrariesSuccessfully() throws InitializationException {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);
        broker.loadStaticFunctionLibrary(AzathothLibrary.class);

        val cthulhuInvocation  = new FunctionInvocation("cthulhu.summonEntity", List.of());
        val azathothInvocation = new FunctionInvocation("azathoth.chaos", List.of());

        assertThat(broker.evaluateFunction(cthulhuInvocation)).isNotInstanceOf(ErrorValue.class);
        assertThat(broker.evaluateFunction(azathothInvocation)).isNotInstanceOf(ErrorValue.class);
    }

    @Test
    void loadingDuplicateFunctionSignatureThrowsException() throws InitializationException {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);

        assertThatThrownBy(() -> broker.loadStaticFunctionLibrary(DuplicateCthulhuLibrary.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Function collision error for 'cthulhu.summonEntity'");
    }

    @Test
    void loadingOverloadedFunctionsWithDifferentAritySucceeds() throws InitializationException {
        broker.loadStaticFunctionLibrary(YogSothothLibrary.class);

        val noArgsInvocation  = new FunctionInvocation("yog.gate", List.of());
        val oneArgInvocation  = new FunctionInvocation("yog.gate", List.of(Value.of("silver")));
        val twoArgsInvocation = new FunctionInvocation("yog.gate", List.of(Value.of("silver"), Value.of("key")));

        val noArgsResult  = broker.evaluateFunction(noArgsInvocation);
        val oneArgResult  = broker.evaluateFunction(oneArgInvocation);
        val twoArgsResult = broker.evaluateFunction(twoArgsInvocation);

        assertThat(noArgsResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) noArgsResult).value()).isEqualTo("no gate");
        assertThat(oneArgResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) oneArgResult).value()).isEqualTo("one gate");
        assertThat(twoArgsResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) twoArgsResult).value()).isEqualTo("two gate");
    }

    @Test
    void evaluateNonExistentFunctionReturnsError() {
        val invocation = new FunctionInvocation("nonexistent.function", List.of());
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("No matching function found");
    }

    @Test
    void bestMatchingFunctionIsSelected() throws InitializationException {
        broker.loadStaticFunctionLibrary(ShubNiggurathLibrary.class);

        val stringInvocation = new FunctionInvocation("shub.transform", List.of(Value.of("goat")));
        val numberInvocation = new FunctionInvocation("shub.transform", List.of(Value.of(1000)));

        val stringResult = broker.evaluateFunction(stringInvocation);
        val numberResult = broker.evaluateFunction(numberInvocation);

        assertThat(stringResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) stringResult).value()).isEqualTo("string transform");
        assertThat(numberResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) numberResult).value()).isEqualTo("number transform");
    }

    @Test
    void functionIsAddedExactlyOnce() throws InitializationException {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);

        val invocation = new FunctionInvocation("cthulhu.summonEntity", List.of());
        val result1    = broker.evaluateFunction(invocation);
        val result2    = broker.evaluateFunction(invocation);

        assertThat(result1).isNotInstanceOf(ErrorValue.class);
        assertThat(result2).isNotInstanceOf(ErrorValue.class);
        assertThat(result1).isInstanceOf(TextValue.class);
        assertThat(result2).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result1).value()).isEqualTo("Rlyeh rises");
        assertThat(((TextValue) result2).value()).isEqualTo("Rlyeh rises");
    }

    @Test
    void concurrentFunctionEvaluationIsSafe() throws Exception {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);

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
    void concurrentLibraryLoadingIsSafe() throws Exception {
        val threadCount = 10;
        val latch       = new CountDownLatch(threadCount);
        val errors      = Collections.synchronizedList(new ArrayList<Throwable>());

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            IntStream.range(0, threadCount).forEach(i -> {
                executor.submit(() -> {
                    try {
                        if (i % 2 == 0) {
                            broker.loadStaticFunctionLibrary(CthulhuLibrary.class);
                        } else {
                            broker.loadStaticFunctionLibrary(AzathothLibrary.class);
                        }
                    } catch (InitializationException | IllegalArgumentException exception) {
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
    void concurrentLoadAndEvaluateIsSafe() throws Exception {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);

        val operations = 1000;
        val latch      = new CountDownLatch(operations);
        val errors     = Collections.synchronizedList(new ArrayList<Throwable>());

        try (var executor = Executors.newFixedThreadPool(50)) {
            IntStream.range(0, operations).forEach(i -> {
                executor.submit(() -> {
                    try {
                        if (i % 100 == 0) {
                            broker.loadStaticFunctionLibrary(AzathothLibrary.class);
                        } else {
                            val invocation = new FunctionInvocation("cthulhu.summonEntity", List.of());
                            val result     = broker.evaluateFunction(invocation);
                            if (result instanceof ErrorValue error) {
                                errors.add(new AssertionError("Evaluation failed: " + error.message()));
                            }
                        }
                    } catch (InitializationException | IllegalArgumentException exception) {
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

    @ParameterizedTest
    @MethodSource("provideInvocationsWithExpectedResults")
    void evaluateFunctionsCorrectly(String functionName, List<Value> params, String expectedResult)
            throws InitializationException {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);
        broker.loadStaticFunctionLibrary(YogSothothLibrary.class);

        val invocation = new FunctionInvocation(functionName, params);
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo(expectedResult);
    }

    static Stream<Arguments> provideInvocationsWithExpectedResults() {
        return Stream.of(Arguments.of("cthulhu.summonEntity", List.of(), "Rlyeh rises"),
                Arguments.of("cthulhu.awakeDreamer", List.of(Value.of("Hastur")), "Hastur awakens"),
                Arguments.of("yog.gate", List.of(), "no gate"),
                Arguments.of("yog.gate", List.of(Value.of("silver")), "one gate"),
                Arguments.of("yog.gate", List.of(Value.of("silver"), Value.of("key")), "two gate"));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidInvocations")
    void evaluateInvalidInvocationsReturnsError(String functionName, List<Value> params)
            throws InitializationException {
        broker.loadStaticFunctionLibrary(CthulhuLibrary.class);

        val invocation = new FunctionInvocation(functionName, params);
        val result     = broker.evaluateFunction(invocation);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> provideInvalidInvocations() {
        return Stream.of(Arguments.of("nonexistent.function", List.of()),
                Arguments.of("cthulhu.summonEntity", List.of(Value.of("unexpected"))),
                Arguments.of("cthulhu.awakeDreamer", List.of()), Arguments.of("wrong.library", List.of()));
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
