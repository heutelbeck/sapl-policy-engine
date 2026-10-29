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

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * This is the ConfirmationDialog for
 * {@link VaadinConfirmationDialogConstraintHandlerProvider}.
 */
class VaadinConfirmationDialog extends Dialog {

    private static final long serialVersionUID = -1392716296697354919L;

    public VaadinConfirmationDialog(String header,
            String text,
            String confirmText,
            final Runnable onConfirmListener,
            String cancelText,
            final Runnable onCancelListener) {

        setCloseOnOutsideClick(false);

        var layout = new VerticalLayout();

        var headline = new H2(header);
        headline.getStyle().set("margin", "var(--lumo-space-m) 0 0 0").set("font-size", "1.5em").set("font-weight",
                "bold");
        layout.add(headline);

        var textComponent = new Text(text);
        layout.add(textComponent);

        var confirmButton = new Button(confirmText);
        confirmButton.addClickListener(event -> {
            this.close();
            onConfirmListener.run();
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button(cancelText);
        cancelButton.addClickListener(event -> {
            this.close();
            onCancelListener.run();
        });

        HorizontalLayout buttonLayout = new HorizontalLayout(cancelButton, confirmButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        layout.add(buttonLayout);
        this.add(layout);
    }

}
