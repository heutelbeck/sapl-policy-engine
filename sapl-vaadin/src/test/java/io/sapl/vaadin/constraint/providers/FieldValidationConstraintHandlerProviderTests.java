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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.json.JsonMapper;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.Binder;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.vaadin.constraint.VaadinSignals;
import lombok.Data;
import lombok.Getter;

@DisplayName("Field validation constraint handler provider")
class FieldValidationConstraintHandlerProviderTests {

    private Binder<TestData> binder;
    private TestForm         form;
    private UI               defaultUi;

    @BeforeEach
    void setupTest() {
        // ui
        defaultUi = mock(UI.class);
        when(defaultUi.getLocale()).thenReturn(Locale.ENGLISH);
        UI.setCurrent(defaultUi);

        // binder
        binder = spy(new Binder<>(TestData.class));

        // form
        form               = new TestForm();
        form.integerField  = new IntegerField();
        form.dateTimeField = new DateTimePicker();
        form.timeField     = new TimePicker();
    }

    @SuppressWarnings("unchecked")
    private static void applyHandler(FieldValidationConstraintHandlerProvider sut, Value constraint, UI ui) {
        var handlers = sut.getConstraintHandlers(constraint, VaadinSignals.SUPPORTED_SIGNALS);
        assertThat(handlers).hasSize(1);
        ((ConstraintHandler.Consumer<UI>) handlers.get(0).handler()).accept(ui);
    }

    @Test
    void when_bindFieldIsCalled_then_ValidatorIsAddedToBinder() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        // WHEN
        sut.bindField(form.integerField);
        // THEN
        verify(binder).forMemberField(form.integerField);
    }

    @Test
    void when_bindFieldIsCalledWithInvalidField_then_ErrorShouldOccur() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        // WHEN + THEN
        assertThatThrownBy(() -> sut.bindField(null)).isInstanceOf(Exception.class);
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form);
        var constraint = ObjectValue.builder().put("type", Value.of("saplVaadin")).put("id", Value.of("validation"))
                .build();
        // WHEN+THEN
        assertThat(sut.isResponsible(constraint)).isTrue();
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithInvalidID_then_providerIsNotResponsible() {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, JsonMapper.builder().build());
        var constraint = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("showNotification")).build();
        // WHEN+THEN
        assertThat(sut.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithoutType_then_providerIsNotResponsible() {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, JsonMapper.builder().build());
        var constraint = ObjectValue.builder().put("id", Value.of("validation")).build();
        // WHEN+THEN
        assertThat(sut.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithoutID_then_providerIsNotResponsible() {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, JsonMapper.builder().build());
        var constraint = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();
        // WHEN+THEN
        assertThat(sut.isResponsible(constraint)).isFalse();
    }

    @Test
    void when_constraintIsEmptyOrNull_then_providerIsNotResponsible() {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, JsonMapper.builder().build());
        var constraint = Value.EMPTY_OBJECT;
        // WHEN+THEN
        assertThat(sut.isResponsible(constraint)).isFalse();
        assertThat(sut.isResponsible(null)).isFalse();
    }

    @Test
    void when_constraintInDecision_then_validValueIsDetectedCorrectly() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // constraint
        var integerFieldSchema = ObjectValue.builder()
                .put("$schema", Value.of("http://json-schema.org/draft-07/schema#")).put("type", Value.of("number"))
                .put("maximum", Value.of(20)).put("message", Value.of("maximum is limited to 20")).build();
        var fields             = ObjectValue.builder().put("integerField", integerFieldSchema).build();
        var constraint         = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).put("fields", fields).build();

        // WHEN
        applyHandler(sut, constraint, defaultUi);
        form.integerField.setValue(10);

        // THEN
        assertThat(form.integerField.isInvalid()).isFalse();
    }

    @Test
    void when_constraintHasNoFields_then_updateValidationSchemesDoNothing() {
        // GIVEN
        var mockedForm = spy(form);
        var sut        = new FieldValidationConstraintHandlerProvider(binder, mockedForm);
        sut.bindField(mockedForm.integerField);

        // constraint
        var constraint = ObjectValue.builder().put("type", Value.of("saplVaadin")).put("id", Value.of("validation"))
                .build();

        // WHEN
        applyHandler(sut, constraint, defaultUi);

        // THEN
        verifyNoInteractions(mockedForm);
    }

    @Test
    void when_constraintInDecision_then_invalidValueIsDetected() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);
        UI ui = mock(UI.class);

        // constraint
        var integerFieldSchema = ObjectValue.builder().put("type", Value.of("number")).put("maximum", Value.of(20))
                .build();
        var fields             = ObjectValue.builder().put("integerField", integerFieldSchema).build();
        var constraint         = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).put("fields", fields).build();

        // WHEN
        applyHandler(sut, constraint, ui);
        form.integerField.setValue(21);

        // THEN
        assertThat(form.integerField.isInvalid()).isTrue();
    }

    @Test
    void when_constraintInDecision_then_invalidValueIsDetectedWithCustomMessage() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);
        UI ui = mock(UI.class);

        // constraint
        var integerFieldSchema = ObjectValue.builder()
                .put("$schema", Value.of("http://json-schema.org/draft-07/schema#")).put("type", Value.of("number"))
                .put("maximum", Value.of(20)).put("message", Value.of("maximum is limited to 20")).build();
        var fields             = ObjectValue.builder().put("integerField", integerFieldSchema).build();
        var constraint         = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).put("fields", fields).build();

        // WHEN
        applyHandler(sut, constraint, ui);
        form.integerField.setValue(21);

        // THEN
        assertThat(form.integerField.isInvalid()).isTrue();
    }

    @Test
    void when_constraintForUnboundFieldInDecision_then_throwException() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // constraint
        var field42Schema = ObjectValue.builder().put("$schema", Value.of("http://json-schema.org/draft-07/schema#"))
                .put("type", Value.of("number")).put("maximum", Value.of(20))
                .put("message", Value.of("maximum is limited to 20")).build();
        var fields        = ObjectValue.builder().put("field42", field42Schema).build();
        var constraint    = ObjectValue.builder().put("type", Value.of("saplVaadin")).put("id", Value.of("validation"))
                .put("fields", fields).build();
        // WHEN+THEN
        assertThatThrownBy(() -> sut.getConstraintHandlers(constraint, VaadinSignals.SUPPORTED_SIGNALS))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_constraintIsNotResponsible_then_noConstraintHandlersAreReturned() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);
        var unrelatedConstraint = ObjectValue.builder().put("type", Value.of("other")).build();

        // WHEN+THEN
        assertThat(sut.getConstraintHandlers(null, VaadinSignals.SUPPORTED_SIGNALS)).isEmpty();
        assertThat(sut.getConstraintHandlers(unrelatedConstraint, VaadinSignals.SUPPORTED_SIGNALS)).isEmpty();
    }

    @Test
    void when_constraintHasDateTimeFormat_then_constraintIsApplied() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.dateTimeField);
        binder.bindInstanceFields(form);

        // constraint
        var dateTimeFieldSchema = ObjectValue.builder().put("type", Value.of("string"))
                .put("format", Value.of("date-time")).build();
        var fields              = ObjectValue.builder().put("dateTimeField", dateTimeFieldSchema).build();
        var constraint          = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).put("fields", fields).build();

        // WHEN+THEN
        applyHandler(sut, constraint, defaultUi);
        // check valid value
        form.dateTimeField.setValue(LocalDateTime.parse("2022-04-01T10:00:00"));
        assertThat(form.dateTimeField.isInvalid()).isFalse();
    }

    @Test
    void when_constraintHasTimeFormat_then_constraintIsApplied() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.timeField);
        binder.bindInstanceFields(form);

        // constraint
        var timeFieldSchema = ObjectValue.builder().put("type", Value.of("string")).put("format", Value.of("time"))
                .build();
        var fields          = ObjectValue.builder().put("timeField", timeFieldSchema).build();
        var constraint      = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("validation")).put("fields", fields).build();

        // WHEN+THEN
        applyHandler(sut, constraint, defaultUi);
        // check valid value
        form.timeField.setValue(LocalTime.parse("10:00:00"));
        assertThat(form.dateTimeField.isInvalid()).isFalse();
    }

    @Test
    void when_fieldCauseReflectionException_then_isFieldBoundReturnsFalse() throws IllegalAccessException {
        // GIVEN
        var sut         = new FieldValidationConstraintHandlerProvider(binder, form);
        var mockedField = mock(Field.class);
        doThrow(IllegalArgumentException.class).when(mockedField).get(any());
        // WHEN
        var isFieldBound = sut.isFieldBound(mockedField, null, null);
        // THEN
        assertThat(isFieldBound).isFalse();
    }

    @Test
    void when_constraintHasEmptyFields_then_updateValidationSchemesDoNothing() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // constraint
        var constraint = ObjectValue.builder().put("type", Value.of("saplVaadin")).put("id", Value.of("validation"))
                .build();
        // WHEN+THEN
        applyHandler(sut, constraint, defaultUi);
        // THEN
        form.integerField.setValue(21);
        assertThat(form.integerField.isInvalid()).isFalse();
    }

    @Getter
    static class TestForm extends VerticalLayout {
        private static final long serialVersionUID = 6075150831882767199L;
        private IntegerField      integerField;
        private DateTimePicker    dateTimeField;
        private TimePicker        timeField;
    }

    @Data
    static class TestData {
        private Integer       integerField = 0;
        private LocalDateTime dateTimeField;
        private LocalTime     timeField;
    }

}
