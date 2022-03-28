package io.sapl.vaadin.constraint.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;

import io.sapl.vaadin.UIMock;
import reactor.core.publisher.Mono;

class VaadinNotificationConstraintHandlerProviderTests {

    private VaadinNotificationConstraintHandlerProvider vaadinNotificationConstraintHandlerProvider;

    @BeforeEach
    void setUp() {
        this.vaadinNotificationConstraintHandlerProvider = new VaadinNotificationConstraintHandlerProvider();
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "showNotification");

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

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
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "test");
        node.put("id", "showNotification");

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", "showNotification");

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsNull_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = null;

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsNull_then_getHandlerReturnsNull() {
        // GIVEN
        ObjectNode node = null;

        // WHEN
        Function<UI, Mono<Boolean>> handler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertNull(handler);
    }

    @Test
    void when_constraintHasCustomValues_then_notificationsIsShownAndReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "showNotification");
        node.put("message", "text message");
        node.put("duration", 6000);
        node.put("position", "TOP_START");
        var mockedUI = UIMock.getMockedUI();

        // mock Notification.show()
        MockedStatic<Notification> notificationMock = mockStatic(Notification.class);
        notificationMock.when(() -> Notification.show(anyString(), anyInt(), any(Notification.Position.class))).then(invocationOnMock -> {
            assertEquals(node.get("message").asText(), invocationOnMock.getArgument(0));
            assertEquals(node.get("duration").asInt(), (Integer) invocationOnMock.getArgument(1));
            assertEquals(Notification.Position.TOP_START, invocationOnMock.getArgument(2));
            return null;
        });

        // WHEN
        var getHandler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
        notificationMock.close();
    }

    @Test
    void when_constraintHasCustomValuesAndInvalidPosition_then_notificationsIsShownAndReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "showNotification");
        node.put("message", "text message");
        node.put("position", "invalid_value");
        var mockedUI = UIMock.getMockedUI();

        // mock Notification.show()
        MockedStatic<Notification> notificationMock = mockStatic(Notification.class);
        notificationMock.when(() -> Notification.show(anyString(), anyInt(), any(Notification.Position.class))).then(invocationOnMock -> {
            assertEquals(node.get("message").asText(), invocationOnMock.getArgument(0));
            assertEquals(5000, (Integer) invocationOnMock.getArgument(1));
            assertEquals(Notification.Position.TOP_STRETCH, invocationOnMock.getArgument(2));
            return null;
        });

        // WHEN
        var getHandler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
        notificationMock.close();
    }

    @Test
    void when_constraintHasCustomValuesAndNoPosition_then_notificationsIsShownAndReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "showNotification");
        var mockedUI = UIMock.getMockedUI();

        // mock Notification.show()
        MockedStatic<Notification> notificationMock = mockStatic(Notification.class);
        notificationMock.when(() -> Notification.show(anyString(), anyInt(), any(Notification.Position.class))).then(invocationOnMock -> {
            assertEquals(5000, (Integer) invocationOnMock.getArgument(1));
            assertEquals(Notification.Position.TOP_STRETCH, invocationOnMock.getArgument(2));
            return null;
        });

        // WHEN
        var getHandler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
        notificationMock.close();
    }
}
