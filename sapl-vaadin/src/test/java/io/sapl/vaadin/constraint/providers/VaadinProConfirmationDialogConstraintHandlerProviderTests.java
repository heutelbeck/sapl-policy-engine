package io.sapl.vaadin.constraint.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

import io.sapl.vaadin.UIMock;
import reactor.core.publisher.Mono;

class VaadinProConfirmationDialogConstraintHandlerProviderTests {
    private VaadinProConfirmationDialogConstraintHandlerProvider vaadinConfirmationDialogConstraintHandlerProvider;

    @BeforeEach
    void setUp() {
        this.vaadinConfirmationDialogConstraintHandlerProvider = spy(VaadinProConfirmationDialogConstraintHandlerProvider.class);
    }

    @Test
    void when_constraintIsNull_then_providerIsNotResponsible() {
        // GIVEN
        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(null);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertTrue(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "log");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "test");
        node.put("id", "requestConfirmation");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", "requestConfirmation");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsNull_then_getHandlerReturnsNull() {
        // GIVEN
        ObjectNode node = null;

        // WHEN
        Function<UI, Mono<Boolean>> handler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertNull(handler);
    }

	@Test
	@SuppressWarnings("unchecked")//suppress mock
    void when_constraintHasDefaultValuesAndDialogIsConfirmed_then_getHandlerReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(3, ComponentEventListener.class).onComponentEvent(null);
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(), anyString(), any(), anyString(), any());


        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
    }

    @Test
    @SuppressWarnings("unchecked")//suppress mock
    void when_constraintHasCustomValuesAndDialogIsConfirmed_then_getHandlerReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");
        node.put("header", "test header");
        node.put("text", "test text");
        node.put("confirmText", "test confirmText");
        node.put("cancelText", "test cancelText");

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(3, ComponentEventListener.class).onComponentEvent(null);
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(), anyString(), any(), anyString(), any());

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
    }

    @Test
    @SuppressWarnings("unchecked")//suppress mock
    void when_constraintHasDefaultValuesAndDialogIsClosed_then_getHandlerReturnsFalse() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(5, ComponentEventListener.class).onComponentEvent(null);
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(), anyString(), any(), anyString(), any());


        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.FALSE, getHandler.apply(mockedUI).block());
    }

    @Test
    void when_openConfirmationDialogIsCalled_then_aNewConfirmDialogIsOpening() {
        // GIVEN
        try ( var mockedConstructor = mockConstruction(ConfirmDialog.class, (confirmDialog, context) -> doNothing().when(confirmDialog).open())) {
            var vaadinConfirmationDialogConstraintHandlerProvider = spy(VaadinProConfirmationDialogConstraintHandlerProvider.class);
            // WHEN
            vaadinConfirmationDialogConstraintHandlerProvider.openConfirmDialog("header" ,"text", "confirm", (event) -> {}, "cancel", (event) -> {});
            // THEN
            assertNotNull(mockedConstructor.constructed().get(0));
            verify(mockedConstructor.constructed().get(0), times(1)).open();
        }
    }
}
