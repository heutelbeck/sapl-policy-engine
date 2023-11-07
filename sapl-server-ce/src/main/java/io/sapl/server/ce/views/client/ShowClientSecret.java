/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views.client;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.templatemodel.TemplateModel;

import lombok.NonNull;
import lombok.Setter;

@Tag("show-client-secret")
@JsModule("./show-client-secret.js")
public class ShowClientSecret extends PolymerTemplate<ShowClientSecret.ShowClientSecretModel> {
    @Id(value = "keyTextField")
    private TextField keyTextField;

    @Id(value = "secretTextField")
    private TextField secretTextField;

    @Id(value = "okButton")
    private Button okButton;

    @Setter
    private OnClosingListener onClosingListener;

    public ShowClientSecret(@NonNull String key, @NonNull String secret) {
        initUi(key, secret);
    }

    private void initUi(@NonNull String key, @NonNull String secret) {
        keyTextField.setValue(key);
        secretTextField.setValue(secret);

        okButton.addClickListener((clickEvent) -> {
            if (onClosingListener != null) {
                onClosingListener.onClosing();
            }
        });
    }

    public interface ShowClientSecretModel extends TemplateModel {
    }

    public interface OnClosingListener {
        void onClosing();
    }
}
