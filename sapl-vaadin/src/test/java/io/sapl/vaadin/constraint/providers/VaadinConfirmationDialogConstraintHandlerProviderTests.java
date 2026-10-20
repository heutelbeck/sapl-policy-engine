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
package io.sapl.vaadin.constraint.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.vaadin.UIMock;
import reactor.core.publisher.Mono;

@DisplayName("Vaadin confirmation dialog constraint handler provider")
class VaadinConfirmationDialogConstraintHandlerProviderTests {

    private VaadinConfirmationDialogConstraintHandlerProvider vaadinConfirmationDialogConstraintHandlerProvider;

    @BeforeEach
    void setUp() {
        this.vaadinConfirmationDialogConstraintHandlerProvider = spy(
                VaadinConfirmationDialogConstraintHandlerProvider.class);
    }

    @Test
    void when_constraintIsNull_then_providerIsNotResponsible() {
        // GIVEN
        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(null);

        // THEN
        assertThat(isResponsibleResult).isFalse();
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("requestConfirmation")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertThat(isResponsibleResult).isTrue();
    }

    @Test
    void when_constraintHasIncorrectID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin")).put("id", Value.of("log")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertThat(isResponsibleResult).isFalse();
    }

    @Test
    void when_constraintHasNoID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertThat(isResponsibleResult).isFalse();
    }

    @Test
    void when_constraintHasIncorrectType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("test"))
                .put("id", Value.of("requestConfirmation")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertThat(isResponsibleResult).isFalse();
    }

    @Test
    void when_constraintHasNoType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("id", Value.of("requestConfirmation")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertThat(isResponsibleResult).isFalse();
    }

    @Test
    void when_constraintIsNull_then_getHandlerReturnsNull() {
        // GIVEN
        // WHEN
        Function<UI, Mono<Boolean>> handler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(null);

        // THEN
        assertThat(handler).isNull();
    }

    @Test
    void when_constraintHasDefaultValuesAndDialogIsConfirmed_then_getHandlerReturnsTrue() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("requestConfirmation")).build();

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(3, Runnable.class).run();
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(),
                anyString(), any(Runnable.class), anyString(), any(Runnable.class));

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertThat(getHandler.apply(mockedUI).block()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void when_constraintHasCustomValuesAndDialogIsConfirmed_then_getHandlerReturnsTrue() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("requestConfirmation")).put("header", Value.of("test header"))
                .put("text", Value.of("test text")).put("confirmText", Value.of("test confirmText"))
                .put("cancelText", Value.of("test cancelText")).build();

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(3, Runnable.class).run();
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(),
                anyString(), any(Runnable.class), anyString(), any(Runnable.class));

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertThat(getHandler.apply(mockedUI).block()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void when_constraintHasDefaultValuesAndDialogIsClosed_then_getHandlerReturnsFalse() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("requestConfirmation")).build();

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(5, Runnable.class).run();
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(),
                anyString(), any(Runnable.class), anyString(), any(Runnable.class));

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertThat(getHandler.apply(mockedUI).block()).isEqualTo(Boolean.FALSE);
    }

    @Test
    void when_openConfirmationDialogIsCalled_then_aNewConfirmDialogIsOpening() {
        // GIVEN
        try (var mockedConstructor = mockConstruction(VaadinConfirmationDialog.class,
                (confirmDialog, context) -> doNothing().when(confirmDialog).open())) {
            var aVaadinConfirmationDialogConstraintHandlerProvider = spy(
                    VaadinConfirmationDialogConstraintHandlerProvider.class);
            // WHEN
            aVaadinConfirmationDialogConstraintHandlerProvider.openConfirmDialog("header", "text", "confirm", () -> {},
                    "cancel", () -> {});
            // THEN
            assertThat(mockedConstructor.constructed().get(0)).isNotNull();
            verify(mockedConstructor.constructed().get(0), times(1)).open();
        }
    }
}
