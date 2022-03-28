package io.sapl.vaadin.constraint.providers;

import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;

import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import reactor.core.publisher.Mono;

/**
 * This Constraint Handler Provider can be used to show a vaadin notification based on SAPL Obligations.
 * This provider manages constrains of type "saplVaadin" with id "showNotification", here an example:
 * ...
 * obligation
 *     {
 *         "type": "saplVaadin",
 *         "id"  : "showNotification",
 *         "message": "test message",
 *         "position": "TOP_STRETCH", (default)
 *         "duration": "5000" (default)
 *     }
 * ...
 *
 */
@Service
public class VaadinNotificationConstraintHandlerProvider implements VaadinFunctionConstraintHandlerProvider {

    @Override
    public boolean isResponsible(JsonNode constraint) {
        if (constraint == null) {
            return false;
        }
        return constraint.has("type") && "saplVaadin".equals(constraint.get("type").asText()) &&
                constraint.has("id") && "showNotification".equals(constraint.get("id").asText());
    }

    @Override
    public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
        if (constraint == null) {
            return null;
        }
        return ui -> {
            String message = constraint.has("message") ? constraint.get("message").asText() : "";
            int duration = constraint.has("duration") ? constraint.get("duration").asInt() : 5000;
            Notification.Position position;
            try {
                position = constraint.has("position")
                        ? Notification.Position.valueOf(constraint.get("position").asText())
                        : Notification.Position.TOP_STRETCH;
            } catch (IllegalArgumentException e){
                position = Notification.Position.TOP_STRETCH;
            }
            Notification.Position finalPosition = position;
            ui.access(() -> Notification.show(message, duration, finalPosition));
            return Mono.just(Boolean.TRUE);
        };
    }
}
