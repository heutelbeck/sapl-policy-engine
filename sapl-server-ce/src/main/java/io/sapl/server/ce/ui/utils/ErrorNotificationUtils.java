/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.ui.utils;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utilities for showing error notifications.
 */
@UtilityClass
public final class ErrorNotificationUtils {
    /**
     * Shows an error notification with a specified error message.
     *
     * @param errorMessage the error message to show
     */
    public static void show(@NonNull String errorMessage) {
        var notification = new Notification();
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);

        var text = new Div(new Text(errorMessage));

        var closeButton = new Button(new Icon("lumo", "cross"));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        closeButton.getElement().setAttribute("aria-label", "Close");
        closeButton.addClickListener(event -> notification.close());

        var layout = new HorizontalLayout(text, closeButton);
        layout.setAlignItems(Alignment.CENTER);

        notification.add(layout);
        notification.setDuration(5000);
        notification.open();
    }

    /**
     * Shows an error notification with a specified error message via an instance of
     * {@link Throwable}.
     *
     * @param throwable the {@link Throwable} to show its message
     */
    public static void show(@NonNull Throwable throwable) {
        show(throwable.getMessage());
    }
}
