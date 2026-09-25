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

import java.util.function.Function;

import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import reactor.core.publisher.Mono;

/**
 * This Constraint Handler Provider can be used to show a vaadin pro
 * confirmation dialog based on SAPL Obligations. This provider manages
 * constrains of type "saplVaadin" with id "requestConfirmation", here an
 * example: ... obligation { "type": "saplVaadin", "id" : "requestConfirmation",
 * "header": "Confirm Dialog", "text": "Please accept the policies.",
 * "confirmText": "Okay", "cancelText": "No" } ...
 *
 */
public class VaadinProConfirmationDialogConstraintHandlerProvider implements VaadinFunctionConstraintHandlerProvider {

    @Override
    public boolean isResponsible(Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            return false;
        }
        return obj.containsKey("type") && obj.get("type") instanceof TextValue(var type) && "saplVaadin".equals(type)
                && obj.containsKey("id") && obj.get("id") instanceof TextValue(var id)
                && "requestConfirmation".equals(id);
    }

    @Override
    public Function<UI, Mono<Boolean>> getHandler(Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            return null;
        }
        String header      = obj.get("header") instanceof TextValue(var h) ? h : "Confirm";
        String text        = obj.get("text") instanceof TextValue(var t) ? t
                : "Confirmation has been requested. " + "Are you sure you want to execute this action?";
        String confirmText = obj.get("confirmText") instanceof TextValue(var ct) ? ct : "Confirm";
        String cancelText  = obj.get("cancelText") instanceof TextValue(var ct) ? ct : "Cancel";
        return (UI ui) -> Mono.create(monoSink -> ui.access(() -> this.openConfirmDialog(header, text, confirmText,
                event -> monoSink.success(Boolean.TRUE), cancelText, event -> monoSink.success(Boolean.FALSE))));
    }

    void openConfirmDialog(String header, String text, String confirmText,
            ComponentEventListener<ConfirmDialog.ConfirmEvent> confirmAction, String cancelText,
            ComponentEventListener<ConfirmDialog.CancelEvent> cancelAction) {
        new ConfirmDialog(header, text, confirmText, confirmAction, cancelText, cancelAction).open();
    }
}
