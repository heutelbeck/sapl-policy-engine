/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.ui.views.pdpconfig;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import io.sapl.api.SaplVersion;
import lombok.Setter;

import java.io.Serial;

public class CreateVariable extends VerticalLayout {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final TextField nameTextField = new TextField("Variable Name");
    private final Button    createButton  = new Button("Create");
    private final Button    cancelButton  = new Button("Cancel");

    @Setter
    private transient UserConfirmedListener userConfirmedListener;

    public CreateVariable() {
        initUi();
    }

    public String getNameOfVariableToCreate() {
        return nameTextField.getValue();
    }

    private void initUi() {
        nameTextField.setPlaceholder("name");
        createButton.addClickListener(e -> setConfirmationResult(true));
        cancelButton.addClickListener(e -> setConfirmationResult(false));
        nameTextField.focus();

        final var buttonLayout = new HorizontalLayout(cancelButton, createButton);
        add(nameTextField, buttonLayout);
    }

    private void setConfirmationResult(boolean isConfirmed) {
        if (userConfirmedListener != null) {
            userConfirmedListener.onConfirmationSet(isConfirmed);
        }
    }

    public interface UserConfirmedListener {
        void onConfirmationSet(boolean isConfirmed);
    }
}
