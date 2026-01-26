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
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MethodSignatureProcessorTest {

    // ========================================================================
    // Static/Instance Method Handling
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("staticInstanceMethodCases")
    void whenMethodWithInstanceConfiguration_thenHandledCorrectly(String description, Object instance,
            String methodName, Class<?>[] paramTypes, boolean shouldSucceed, String expectedNameSuffix)
            throws Exception {
        val method = StormbringerLibrary.class.getMethod(methodName, paramTypes);

        if (shouldSucceed) {
            val spec = MethodSignatureProcessor.functionSpecification(instance, "chaos", method);
            assertThat(spec).isNotNull();
            assertThat(spec.functionName()).isEqualTo("chaos." + expectedNameSuffix);
        } else {
            assertThatThrownBy(() -> MethodSignatureProcessor.functionSpecification(instance, "chaos", method))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be static");
        }
    }

    static Stream<Arguments> staticInstanceMethodCases() {
        val library = new StormbringerLibrary();
        return Stream.of(
                arguments("static method with null instance accepted", null, "conjureSword",
                        new Class<?>[] { TextValue.class }, true, "conjureSword"),
                arguments("instance method with null instance rejected", null, "drainSoul",
                        new Class<?>[] { TextValue.class }, false, null),
                arguments("instance method with instance accepted", library, "drainSoul",
                        new Class<?>[] { TextValue.class }, true, "drainSoul"),
                arguments("static method with instance accepted", library, "conjureSword",
                        new Class<?>[] { TextValue.class }, true, "conjureSword"));
    }

    @Test
    void whenMixedStaticAndInstanceMethods_thenBothExecuteCorrectly() throws Exception {
        val library        = new StormbringerLibrary();
        val staticMethod   = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);
        val instanceMethod = StormbringerLibrary.class.getMethod("drainSoul", TextValue.class);

        val staticSpec   = MethodSignatureProcessor.functionSpecification(library, "chaos", staticMethod);
        val instanceSpec = MethodSignatureProcessor.functionSpecification(library, "chaos", instanceMethod);

        assertThat(staticSpec).isNotNull();
        assertThat(instanceSpec).isNotNull();

        val staticResult   = staticSpec.function().apply(invocation("chaos.conjureSword", Value.of("Elric")));
        val instanceResult = instanceSpec.function().apply(invocation("chaos.drainSoul", Value.of("Victim")));

        assertThat(staticResult).isEqualTo(Value.of("Stormbringer bound to Elric"));
        assertThat(instanceResult).isEqualTo(Value.of("Soul of Victim drained"));
    }

    // ========================================================================
    // Function Name Resolution
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("functionNameResolutionCases")
    void whenResolvingFunctionName_thenCorrectNameUsed(String description, String methodName, Class<?>[] paramTypes,
            String expectedFunctionName) throws Exception {
        val method = StormbringerLibrary.class.getMethod(methodName, paramTypes);

        val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

        assertThat(spec).isNotNull();
        assertThat(spec.functionName()).isEqualTo(expectedFunctionName);
    }

    static Stream<Arguments> functionNameResolutionCases() {
        return Stream.of(
                arguments("annotation name provided uses annotation name", "summonArioch",
                        new Class<?>[] { TextValue.class }, "chaos.summonChaosLord"),
                arguments("empty annotation name uses method name", "conjureSword", new Class<?>[] { TextValue.class },
                        "chaos.conjureSword"));
    }

    @Test
    void whenMethodWithoutAnnotation_thenReturnsNull() throws Exception {
        val method = StormbringerLibrary.class.getMethod("notAFunction");

        val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

        assertThat(spec).isNull();
    }

    // ========================================================================
    // Signature Validation
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("validSignatureCases")
    void whenValidSignature_thenAccepted(String description, Class<?> libraryClass, String methodName,
            Class<?>[] paramTypes, String libraryName, int expectedArgCount, boolean hasVarArgs) throws Exception {
        val method = libraryClass.getMethod(methodName, paramTypes);

        val spec = MethodSignatureProcessor.functionSpecification(null, libraryName, method);

        assertThat(spec).isNotNull();
        assertThat(spec.numberOfArguments()).isEqualTo(expectedArgCount);
        assertThat(spec.hasVariableNumberOfArguments()).isEqualTo(hasVarArgs);
    }

    static Stream<Arguments> validSignatureCases() {
        return Stream.of(
                arguments("single Value parameter", StormbringerLibrary.class, "conjureSword",
                        new Class<?>[] { TextValue.class }, "chaos", 1, false),
                arguments("multiple Value parameters", ElementalLibrary.class, "bindElemental",
                        new Class<?>[] { TextValue.class, TextValue.class, NumberValue.class }, "sorcery", 3, false),
                arguments("varargs as last parameter", DreamingCityLibrary.class, "awakeTower",
                        new Class<?>[] { TextValue.class, TextValue[].class }, "imrryr", 1, true),
                arguments("Value return type", StormbringerLibrary.class, "conjureSword",
                        new Class<?>[] { TextValue.class }, "chaos", 1, false),
                arguments("Value subtype return type", DragonLibrary.class, "summonDragons",
                        new Class<?>[] { TextValue.class }, "imrryr", 1, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSignatureCases")
    void whenInvalidSignature_thenRejected(String description, Class<?> libraryClass, String methodName,
            Class<?>[] paramTypes, Class<? extends Exception> expectedException, String expectedMessage)
            throws Exception {
        val method = libraryClass.getMethod(methodName, paramTypes);

        assertThatThrownBy(() -> MethodSignatureProcessor.functionSpecification(null, "broken", method))
                .isInstanceOf(expectedException).hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> invalidSignatureCases() {
        return Stream.of(
                arguments("non-Value return type rejected", BrokenLibrary.class, "invalidReturn",
                        new Class<?>[] { TextValue.class }, IllegalArgumentException.class, "must return Value"),
                arguments("non-Value parameter rejected", BrokenLibrary.class, "invalidParam",
                        new Class<?>[] { String.class }, IllegalStateException.class, "must only have Value"),
                arguments("varargs not as last parameter rejected", BrokenLibrary.class, "varArgsNotLast",
                        new Class<?>[] { TextValue[].class, TextValue.class }, IllegalStateException.class, ""));
    }

    // ========================================================================
    // Method Execution
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("methodExecutionCases")
    void whenInvokingMethod_thenExecutesCorrectly(String description, MethodReference methodRef, Object instance,
            List<Value> arguments, String expectedResultContains) throws Exception {
        val method = methodRef.resolve();
        val spec   = MethodSignatureProcessor.functionSpecification(instance, methodRef.libraryName(), method);
        assertThat(spec).isNotNull();

        val result = spec.function().apply(invocation(spec.functionName(), arguments));

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).contains(expectedResultContains);
    }

    static Stream<Arguments> methodExecutionCases() {
        return Stream.of(
                arguments("static method execution",
                        methodRef(StormbringerLibrary.class, "conjureSword", "chaos", TextValue.class), null,
                        List.of(Value.of("Elric")), "Stormbringer bound to Elric"),
                arguments("instance method execution",
                        methodRef(StormbringerLibrary.class, "drainSoul", "chaos", TextValue.class),
                        new StormbringerLibrary(), List.of(Value.of("victim")), "drained"),
                arguments("multi-parameter method execution",
                        methodRef(ElementalLibrary.class, "bindElemental", "sorcery", TextValue.class, TextValue.class,
                                NumberValue.class),
                        null, List.of(Value.of("Elric"), Value.of("fire"), Value.of(9)), "Elric binds fire"),
                arguments("ArrayValue parameter execution",
                        methodRef(ChaosLibrary.class, "summonLords", "chaos", ArrayValue.class), null,
                        List.of(Value.ofArray(Value.of("Arioch"), Value.of("Xiombarg"), Value.of("Mabelode"))),
                        "Summoned 3 Chaos Lords"),
                arguments("ObjectValue parameter execution",
                        methodRef(RuneLibrary.class, "inscribeRune", "sorcery", ObjectValue.class), null,
                        List.of(Value.ofObject(Map.of("symbol", Value.of("Actorios"), "power", Value.of(8)))),
                        "Actorios"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("varArgsExecutionCases")
    void whenInvokingVarArgsMethod_thenExecutesCorrectly(String description, List<Value> arguments,
            String expectedResult) throws Exception {
        val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
        val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);

        val result = spec.function().apply(invocation("imrryr.awakeTower", arguments));

        assertThat(result).isEqualTo(Value.of(expectedResult));
    }

    static Stream<Arguments> varArgsExecutionCases() {
        return Stream.of(
                arguments("zero varargs", List.of(Value.of("Elric")), "Elric awakens 0 towers in the Dreaming City"),
                arguments("single vararg", List.of(Value.of("Elric"), Value.of("Bronze")),
                        "Elric awakens 1 towers in the Dreaming City"),
                arguments("multiple varargs",
                        List.of(Value.of("Elric"), Value.of("Bronze"), Value.of("Jade"), Value.of("Silver")),
                        "Elric awakens 3 towers in the Dreaming City"));
    }

    // ========================================================================
    // Argument Validation Errors
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentValidationErrorCases")
    void whenInvalidArguments_thenReturnsError(String description, MethodReference methodRef, List<Value> arguments,
            String expectedErrorContains) throws Exception {
        val method = methodRef.resolve();
        val spec   = MethodSignatureProcessor.functionSpecification(null, methodRef.libraryName(), method);

        val result = spec.function().apply(invocation(spec.functionName(), arguments));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(expectedErrorContains);
    }

    static Stream<Arguments> argumentValidationErrorCases() {
        return Stream.of(
                // Argument count errors
                arguments("too few arguments for fixed params",
                        methodRef(ElementalLibrary.class, "bindElemental", "sorcery", TextValue.class, TextValue.class,
                                NumberValue.class),
                        List.of(Value.of("Elric")), "requires exactly 3 arguments"),
                arguments("too many arguments for fixed params",
                        methodRef(StormbringerLibrary.class, "conjureSword", "chaos", TextValue.class),
                        List.of(Value.of("Elric"), Value.of("Extra")), "requires exactly 1 arguments"),
                arguments("too few arguments for varargs method",
                        methodRef(DreamingCityLibrary.class, "awakeTower", "imrryr", TextValue.class,
                                TextValue[].class),
                        List.of(), "requires at least 1 arguments"),

                // Argument type errors
                arguments("wrong type in fixed parameter",
                        methodRef(StormbringerLibrary.class, "conjureSword", "chaos", TextValue.class),
                        List.of(Value.of(666)), "expected TextValue"),
                arguments("wrong type in first vararg",
                        methodRef(DreamingCityLibrary.class, "awakeTower", "imrryr", TextValue.class,
                                TextValue[].class),
                        List.of(Value.of("Elric"), Value.of(true)), "varargs argument 0"),
                arguments("wrong type in second vararg",
                        methodRef(DreamingCityLibrary.class, "awakeTower", "imrryr", TextValue.class,
                                TextValue[].class),
                        List.of(Value.of("Elric"), Value.of("Bronze"), Value.of(42)), "varargs argument 1"));
    }

    // ========================================================================
    // Exception Handling
    // ========================================================================

    @Test
    void whenMethodThrowsException_thenCapturedAsError() throws Exception {
        val method = StormbringerLibrary.class.getMethod("throwsException", TextValue.class);
        val spec   = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

        val result = spec.function().apply(invocation("chaos.throwsException", Value.of("test")));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("execution failed").contains("Stormbringer rebels");
    }

    // ========================================================================
    // Helper Methods and Records
    // ========================================================================

    private static FunctionInvocation invocation(String name, Value... args) {
        return new FunctionInvocation(name, List.of(args));
    }

    private static FunctionInvocation invocation(String name, List<Value> args) {
        return new FunctionInvocation(name, args);
    }

    private static MethodReference methodRef(Class<?> clazz, String methodName, String libraryName,
            Class<?>... paramTypes) {
        return new MethodReference(clazz, methodName, libraryName, paramTypes);
    }

    private record MethodReference(Class<?> clazz, String methodName, String libraryName, Class<?>[] paramTypes) {
        Method resolve() throws NoSuchMethodException {
            return clazz.getMethod(methodName, paramTypes);
        }
    }

    // ========================================================================
    // Test Library Classes (Elric Universe Themes)
    // ========================================================================

    static class StormbringerLibrary {

        @Function
        public static Value conjureSword(TextValue wielder) {
            return Value.of("Stormbringer bound to " + wielder.value());
        }

        @Function(name = "summonChaosLord")
        public static Value summonArioch(TextValue summoner) {
            return Value.of("Arioch answers " + summoner.value());
        }

        @Function
        public Value drainSoul(TextValue victim) {
            return Value.of("Soul of " + victim.value() + " drained");
        }

        @Function
        public static Value throwsException(TextValue trigger) {
            throw new RuntimeException("Stormbringer rebels against its master " + trigger);
        }

        public static void notAFunction() {
            // Not annotated
        }
    }

    static class DragonLibrary {

        @Function
        public static Value summonDragons(TextValue caller) {
            return Value.of("Dragons of Imrryr answer " + caller.value());
        }
    }

    static class ElementalLibrary {

        @Function
        public static Value bindElemental(TextValue sorcerer, TextValue element, NumberValue power) {
            return Value.of(
                    "%s binds %s elemental with power %s".formatted(sorcerer.value(), element.value(), power.value()));
        }
    }

    static class DreamingCityLibrary {

        @Function
        public static Value awakeTower(TextValue awakener, TextValue... towers) {
            return Value.of("%s awakens %d towers in the Dreaming City".formatted(awakener.value(), towers.length));
        }
    }

    static class ChaosLibrary {

        @Function
        public static Value summonLords(ArrayValue lords) {
            return Value.of("Summoned %d Chaos Lords from the Higher Planes".formatted(lords.size()));
        }
    }

    static class RuneLibrary {

        @Function
        public static Value inscribeRune(ObjectValue rune) {
            val symbol = rune.get("symbol");
            val power  = rune.get("power");
            assert power != null;
            assert symbol != null;
            return Value.of("Rune %s inscribed with power %s".formatted(((TextValue) symbol).value(),
                    ((NumberValue) power).value()));
        }
    }

    static class BrokenLibrary {

        @Function
        public static String invalidReturn(TextValue input) {
            return "broken " + input;
        }

        @Function
        public static Value invalidParam(String notAValue) {
            return Value.of("broken " + notAValue);
        }

        @Function
        public static Value varArgsNotLast(TextValue[] varargs, TextValue fixed) {
            return Value.of("broken" + Arrays.toString(varargs) + fixed);
        }
    }
}
