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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import reactor.core.publisher.Mono;

/**
 * This Constraint Handler Provider can be used to show a vaadin notification
 * based on SAPL Obligations. This provider manages constrains of type
 * "saplVaadin" with id "showNotification", here an example: ... obligation {
 * "type": "saplVaadin", "id" : "showNotification", "message": "test message",
 * "position": "TOP_STRETCH", (default) "duration": "5000" (default) } ...
 *
 */
public class VaadinNotificationConstraintHandlerProvider implements VaadinFunctionConstraintHandlerProvider {

    @Override
    public boolean isResponsible(Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            return false;
        }
        return obj.containsKey("type") && obj.get("type") instanceof TextValue(var type) && "saplVaadin".equals(type)
                && obj.containsKey("id") && obj.get("id") instanceof TextValue(var id) && "showNotification".equals(id);
    }

    @Override
    public Function<UI, Mono<Boolean>> getHandler(Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            return null;
        }
        return ui -> {
            String                message  = obj.get("message") instanceof TextValue(var m) ? m : "";
            int                   duration = obj.get("duration") instanceof NumberValue(var d) ? d.intValue() : 5000;
            Notification.Position position;
            try {
                position = obj.get("position") instanceof TextValue(var p) ? Notification.Position.valueOf(p)
                        : Notification.Position.TOP_STRETCH;
            } catch (IllegalArgumentException e) {
                position = Notification.Position.TOP_STRETCH;
            }
            Notification.Position finalPosition = position;
            ui.access(() -> Notification.show(message, duration, finalPosition));
            return Mono.just(Boolean.TRUE);
        };
    }
}
