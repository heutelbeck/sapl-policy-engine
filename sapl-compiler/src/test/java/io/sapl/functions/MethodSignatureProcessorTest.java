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
import io.sapl.api.model.*;
import io.sapl.interpreter.InitializationException;
import lombok.val;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MethodSignatureProcessorTest {

    @Nested
    class StaticMethodHandling {

        @Test
        void acceptsStaticMethodWithNullInstance() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

            assertThat(spec).isNotNull();
            assertThat(spec.functionName()).isEqualTo("chaos.conjureSword");
        }

        @Test
        void rejectsInstanceMethodWithNullInstance() throws Exception {
            val method = StormbringerLibrary.class.getMethod("drainSoul", TextValue.class);

            assertThatThrownBy(() -> MethodSignatureProcessor.functionSpecification(null, "chaos", method))
                    .isInstanceOf(InitializationException.class).hasMessageContaining("must be static");
        }

        @Test
        void acceptsInstanceMethodWithInstance() throws Exception {
            val library = new StormbringerLibrary();
            val method  = StormbringerLibrary.class.getMethod("drainSoul", TextValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(library, "chaos", method);

            assertThat(spec).isNotNull();
            assertThat(spec.functionName()).isEqualTo("chaos.drainSoul");
        }

        @Test
        void acceptsStaticMethodWithInstance() throws Exception {
            val library = new StormbringerLibrary();
            val method  = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(library, "chaos", method);

            assertThat(spec).isNotNull();
            assertThat(spec.functionName()).isEqualTo("chaos.conjureSword");
        }

        @Test
        void mixedStaticAndInstanceMethodsInInstanceLibrary() throws Exception {
            val library        = new StormbringerLibrary();
            val staticMethod   = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);
            val instanceMethod = StormbringerLibrary.class.getMethod("drainSoul", TextValue.class);

            val staticSpec   = MethodSignatureProcessor.functionSpecification(library, "chaos", staticMethod);
            val instanceSpec = MethodSignatureProcessor.functionSpecification(library, "chaos", instanceMethod);

            assertThat(staticSpec).isNotNull();
            assertThat(instanceSpec).isNotNull();

            val staticInvocation   = new FunctionInvocation("chaos.conjureSword", List.of(Value.of("Elric")));
            val instanceInvocation = new FunctionInvocation("chaos.drainSoul", List.of(Value.of("Victim")));

            val staticResult   = staticSpec.function().apply(staticInvocation);
            val instanceResult = instanceSpec.function().apply(instanceInvocation);

            assertThat(staticResult).isInstanceOf(TextValue.class);
            assertThat(((TextValue) staticResult).value()).isEqualTo("Stormbringer bound to Elric");
            assertThat(instanceResult).isInstanceOf(TextValue.class);
            assertThat(((TextValue) instanceResult).value()).isEqualTo("Soul of Victim drained");
        }
    }

    @Nested
    class AnnotationProcessing {

        @Test
        void returnsNullForMethodWithoutAnnotation() throws Exception {
            val method = StormbringerLibrary.class.getMethod("notAFunction");

            val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

            assertThat(spec).isNull();
        }

        @Test
        void usesAnnotationNameWhenProvided() throws Exception {
            val method = StormbringerLibrary.class.getMethod("summonArioch", TextValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

            assertThat(spec).isNotNull();
            assertThat(spec.functionName()).isEqualTo("chaos.summonChaosLord");
        }

        @Test
        void usesMethodNameWhenAnnotationNameEmpty() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

            assertThat(spec).isNotNull();
            assertThat(spec.functionName()).isEqualTo("chaos.conjureSword");
        }
    }

    @Nested
    class ReturnTypeValidation {

        @Test
        void acceptsValueReturnType() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);

            assertThatCode(() -> MethodSignatureProcessor.functionSpecification(null, "chaos", method))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsValueSubtypeReturnType() throws Exception {
            val method = DragonLibrary.class.getMethod("summonDragons", TextValue.class);

            assertThatCode(() -> MethodSignatureProcessor.functionSpecification(null, "imrryr", method))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsNonValueReturnType() throws Exception {
            val method = BrokenLibrary.class.getMethod("invalidReturn", TextValue.class);

            assertThatThrownBy(() -> MethodSignatureProcessor.functionSpecification(null, "broken", method))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must return Value");
        }
    }

    @Nested
    class ParameterTypeValidation {

        @Test
        void acceptsValueParameters() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(null, "chaos", method);

            assertThat(spec).isNotNull();
            assertThat(spec.numberOfArguments()).isEqualTo(1);
        }

        @Test
        void acceptsMultipleValueParameters() throws Exception {
            val method = ElementalLibrary.class.getMethod("bindElemental", TextValue.class, TextValue.class,
                    NumberValue.class);

            val spec = MethodSignatureProcessor.functionSpecification(null, "sorcery", method);

            assertThat(spec).isNotNull();
            assertThat(spec.numberOfArguments()).isEqualTo(3);
        }

        @Test
        void acceptsVarArgsAsLastParameter() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);

            val spec = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);

            assertThat(spec).isNotNull();
            assertThat(spec.hasVariableNumberOfArguments()).isTrue();
        }

        @Test
        void rejectsNonValueParameter() throws Exception {
            val method = BrokenLibrary.class.getMethod("invalidParam", String.class);

            assertThatThrownBy(() -> MethodSignatureProcessor.functionSpecification(null, "broken", method))
                    .isInstanceOf(InitializationException.class).hasMessageContaining("must only have Value");
        }

        @Test
        void rejectsVarArgsNotAsLastParameter() throws Exception {
            val method = BrokenLibrary.class.getMethod("varArgsNotLast", TextValue[].class, TextValue.class);

            assertThatThrownBy(() -> MethodSignatureProcessor.functionSpecification(null, "broken", method))
                    .isInstanceOf(InitializationException.class);
        }
    }

    @Nested
    class MethodExecution {

        @Test
        void invokesStaticMethodCorrectly() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "chaos", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("chaos.conjureSword", List.of(Value.of("Elric")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).isEqualTo("Stormbringer bound to Elric");
        }

        @Test
        void invokesInstanceMethodCorrectly() throws Exception {
            val library = new StormbringerLibrary();
            val method  = StormbringerLibrary.class.getMethod("drainSoul", TextValue.class);
            val spec    = MethodSignatureProcessor.functionSpecification(library, "chaos", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("chaos.drainSoul", List.of(Value.of("victim")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("drained");
        }

        @Test
        void invokesMultiParameterMethodCorrectly() throws Exception {
            val method = ElementalLibrary.class.getMethod("bindElemental", TextValue.class, TextValue.class,
                    NumberValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "sorcery", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("sorcery.bindElemental",
                    List.of(Value.of("Elric"), Value.of("fire"), Value.of(9)));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("Elric").contains("fire").contains("9");
        }

        @Test
        void invokesVarArgsMethodWithNoVarArgs() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("imrryr.awakeTower", List.of(Value.of("Elric")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("Elric awakens 0 towers");
        }

        @Test
        void invokesVarArgsMethodWithSingleVarArg() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("imrryr.awakeTower",
                    List.of(Value.of("Elric"), Value.of("Bronze")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("Elric awakens 1 towers");
        }

        @Test
        void invokesVarArgsMethodWithMultipleVarArgs() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("imrryr.awakeTower",
                    List.of(Value.of("Elric"), Value.of("Bronze"), Value.of("Jade"), Value.of("Silver")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("Elric awakens 3 towers");
        }

        @Test
        void invokesArrayParameterMethod() throws Exception {
            val method = ChaosLibrary.class.getMethod("summonLords", ArrayValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "chaos", method);
            assertThat(spec).isNotNull();

            val lords      = Value.ofArray(Value.of("Arioch"), Value.of("Xiombarg"), Value.of("Mabelode"));
            val invocation = new FunctionInvocation("chaos.summonLords", List.of(lords));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("Summoned 3 Chaos Lords");
        }

        @Test
        void invokesObjectParameterMethod() throws Exception {
            val method = RuneLibrary.class.getMethod("inscribeRune", ObjectValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "sorcery", method);
            assertThat(spec).isNotNull();

            val rune       = Value.ofObject(Map.of("symbol", Value.of("Actorios"), "power", Value.of(8)));
            val invocation = new FunctionInvocation("sorcery.inscribeRune", List.of(rune));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TextValue.class);
            val textResult = (TextValue) result;
            assertThat(textResult.value()).contains("Actorios").contains("8");
        }
    }

    @Nested
    class ArgumentValidation {

        @Test
        void rejectsTooFewArguments() throws Exception {
            val method = ElementalLibrary.class.getMethod("bindElemental", TextValue.class, TextValue.class,
                    NumberValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "sorcery", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("sorcery.bindElemental", List.of(Value.of("Elric")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("requires exactly 3 arguments").contains("received 1");
        }

        @Test
        void rejectsTooManyArguments() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "chaos", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("chaos.conjureSword",
                    List.of(Value.of("Elric"), Value.of("Extra")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("requires exactly 1 arguments").contains("received 2");
        }

        @Test
        void rejectsTooFewArgumentsForVarArgs() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("imrryr.awakeTower", List.of());

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("requires at least 1 arguments").contains("received 0");
        }

        @Test
        void rejectsWrongParameterType() throws Exception {
            val method = StormbringerLibrary.class.getMethod("conjureSword", TextValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "chaos", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("chaos.conjureSword", List.of(Value.of(666)));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("argument 0").contains("expected TextValue")
                    .contains("received NumberValue");
        }

        @Test
        void rejectsWrongTypeInVarArgs() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("imrryr.awakeTower", List.of(Value.of("Elric"), Value.of(true)));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("varargs argument 0").contains("expected TextValue")
                    .contains("received BooleanValue");
        }

        @Test
        void rejectsWrongTypeInSecondVarArg() throws Exception {
            val method = DreamingCityLibrary.class.getMethod("awakeTower", TextValue.class, TextValue[].class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "imrryr", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("imrryr.awakeTower",
                    List.of(Value.of("Elric"), Value.of("Bronze"), Value.of(42)));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("varargs argument 1");
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void capturesMethodInvocationException() throws Exception {
            val method = StormbringerLibrary.class.getMethod("throwsException", TextValue.class);
            val spec   = MethodSignatureProcessor.functionSpecification(null, "chaos", method);
            assertThat(spec).isNotNull();

            val invocation = new FunctionInvocation("chaos.throwsException", List.of(Value.of("test")));

            val result = spec.function().apply(invocation);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(ErrorValue.class);
            val error = (ErrorValue) result;
            assertThat(error.message()).contains("execution failed").contains("Stormbringer rebels");
        }
    }

    // Test library classes using Elric universe themes

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
