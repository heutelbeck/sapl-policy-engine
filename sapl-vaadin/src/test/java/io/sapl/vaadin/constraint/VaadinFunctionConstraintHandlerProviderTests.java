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
package io.sapl.vaadin.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import reactor.core.publisher.Mono;

@DisplayName("Vaadin function constraint handler provider")
class VaadinFunctionConstraintHandlerProviderTests {

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithPredicateAndFunctionIsResponsibleIsCalled_then_PredicateTestWithValueIsCalled() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Predicate<Value>            predicate = (Predicate<Value>) mock(Predicate.class);
        @SuppressWarnings("unchecked")
        Function<UI, Mono<Boolean>> function  = (Function<UI, Mono<Boolean>>) mock(Function.class);

        Value constraint = Value.of("test");

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(predicate, function);
        // WHEN
        vaadinFunctionConstraintHandlerProvider.isResponsible(constraint);

        // THEN
        verify(predicate).test(constraint);
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithPredicateAndFunctionGetHandlerIsCalled_then_FunctionIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Predicate<Value>            predicate = (Predicate<Value>) mock(Predicate.class);
        @SuppressWarnings("unchecked")
        Function<UI, Mono<Boolean>> function  = (Function<UI, Mono<Boolean>>) mock(Function.class);

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(predicate, function);
        // WHEN+THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.getHandler(Value.of("test"))).isEqualTo(function);
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerAndEmptyFilterIsResponsibleIsCalled_then_TrueIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer         = (Consumer<Value>) mock(Consumer.class);
        ObjectValue     constraintFilter = Value.EMPTY_OBJECT;
        ObjectValue     constraint       = ObjectValue.builder().put("key", Value.of("value")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, consumer);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isTrue();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerIsResponsibleIsCalledWithFilterFieldInConstraint_then_TrueIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer         = (Consumer<Value>) mock(Consumer.class);
        ObjectValue     constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();
        ObjectValue     constraint       = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, consumer);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isTrue();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerIsResponsibleIsCalledWithFilterFieldNotMatching_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer         = (Consumer<Value>) mock(Consumer.class);
        ObjectValue     constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();
        ObjectValue     constraint       = ObjectValue.builder().put("type", Value.of("other")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, consumer);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerIsResponsibleIsCalledWithFilterFieldNotInConstraintAndNotMatching_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer         = (Consumer<Value>) mock(Consumer.class);
        ObjectValue     constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();
        ObjectValue     constraint       = ObjectValue.builder().put("id", Value.of("other")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, consumer);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerIsResponsibleIsCalledWithFilterFieldNotInConstraintAndMatching_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer         = (Consumer<Value>) mock(Consumer.class);
        ObjectValue     constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).build();
        ObjectValue     constraint       = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, consumer);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerIsResponsibleIsCalledWithNonObjectConstraint_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer         = (Consumer<Value>) mock(Consumer.class);
        ObjectValue     constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();
        Value           constraint       = Value.of("notAnObject");

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, consumer);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndConsumerGetHandlerIsCalled_then_FunctionThatCallsHandlerIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Consumer<Value> consumer   = (Consumer<Value>) mock(Consumer.class);
        Value           constraint = Value.of("test");

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(Value.EMPTY_OBJECT, consumer);
        // WHEN
        vaadinFunctionConstraintHandlerProvider.getHandler(constraint).apply(mock(UI.class));

        // THEN
        verify(consumer).accept(constraint);
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndFunctionIsResponsibleIsCalledWithFilterFieldInConstraint_then_TrueIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Function<Value, Mono<Boolean>> function         = (Function<Value, Mono<Boolean>>) mock(Function.class);
        ObjectValue                    constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .build();
        ObjectValue                    constraint       = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, function);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isTrue();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndFunctionIsResponsibleIsCalledWithFilterFieldNotMatching_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Function<Value, Mono<Boolean>> function         = (Function<Value, Mono<Boolean>>) mock(Function.class);
        ObjectValue                    constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .build();
        ObjectValue                    constraint       = ObjectValue.builder().put("type", Value.of("other")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, function);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndFunctionIsResponsibleIsCalledWithFilterFieldNotInConstraintAndNotMatching_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Function<Value, Mono<Boolean>> function         = (Function<Value, Mono<Boolean>>) mock(Function.class);
        ObjectValue                    constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .build();
        ObjectValue                    constraint       = ObjectValue.builder().put("id", Value.of("other")).build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, function);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndFunctionIsResponsibleIsCalledWithFilterFieldNotInConstraintAndMatching_then_FalseIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Function<Value, Mono<Boolean>> function         = (Function<Value, Mono<Boolean>>) mock(Function.class);
        ObjectValue                    constraintFilter = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).build();
        ObjectValue                    constraint       = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .build();

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(constraintFilter, function);
        // WHEN + THEN
        assertThat(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_VaadinFunctionConstraintHandlerProviderWithObjectValueAndFunctionGetHandlerIsCalled_then_FunctionThatCallsHandlerIsReturned() {
        // GIVEN
        @SuppressWarnings("unchecked")
        Function<Value, Mono<Boolean>> function   = (Function<Value, Mono<Boolean>>) mock(Function.class);
        Value                          constraint = Value.of("test");

        VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider
                .of(Value.EMPTY_OBJECT, function);
        // WHEN
        vaadinFunctionConstraintHandlerProvider.getHandler(constraint).apply(mock(UI.class));

        // THEN
        verify(function).apply(constraint);
    }

}
