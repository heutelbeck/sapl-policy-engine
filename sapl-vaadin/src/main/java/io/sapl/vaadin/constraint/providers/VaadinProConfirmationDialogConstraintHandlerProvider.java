package io.sapl.vaadin.constraint.providers;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import reactor.core.publisher.Mono;

/**
 * This Constraint Handler Provider can be used to show a vaadin pro confirmation dialog based on SAPL Obligations.
 * This provider manages constrains of type "saplVaadin" with id "requestConfirmation", here an example:
 * ...
 * obligation
 *     {
 *         "type": "saplVaadin",
 *         "id"  : "requestConfirmation",
 *         "header": "Confirm Dialog",
 *         "text": "Please accept the policies.",
 *         "confirmText": "Okay",
 *         "cancelText": "No"
 *     }
 * ...
 *
 */
@Service
@ConditionalOnClass(name = "com.vaadin.flow.component.confirmdialog.ConfirmDialog")
public class VaadinProConfirmationDialogConstraintHandlerProvider implements VaadinFunctionConstraintHandlerProvider {

    @Override
    public boolean isResponsible(JsonNode constraint) {
        if (constraint == null) {
            return false;
        }
        return constraint.has("type") && "saplVaadin".equals(constraint.get("type").asText()) &&
                constraint.has("id") && "requestConfirmation".equals(constraint.get("id").asText());
    }

    @Override
    public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
        if (constraint == null) {
            return null;
        }
        String header = constraint.has("header") ? constraint.get("header").textValue() : "Confirm";
        String text = constraint.has("text") ? constraint.get("text").textValue() : "Confirmation has been requested. " +
                "Are you sure you want to execute this action?";
        String confirmText = constraint.has("confirmText") ? constraint.get("confirmText").textValue() : "Confirm";
        String cancelText = constraint.has("cancelText") ? constraint.get("cancelText").textValue() : "Cancel";
        return (UI ui) -> Mono.create(monoSink -> ui.access(() -> this.openConfirmDialog(header, text,
                confirmText, event -> monoSink.success(Boolean.TRUE),
                cancelText, event -> monoSink.success(Boolean.FALSE))));
    }

    void openConfirmDialog(String header, String text, String confirmText, ComponentEventListener<ConfirmDialog.ConfirmEvent> confirmAction, String cancelText, ComponentEventListener<ConfirmDialog.CancelEvent> cancelAction) {
        new ConfirmDialog(header, text,
                confirmText, confirmAction,
                cancelText, cancelAction).open();
    }
}
