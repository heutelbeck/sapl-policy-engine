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
package io.sapl.playground.embed;

import com.vaadin.flow.component.WebComponentExporter;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.webcomponent.WebComponent;
import io.sapl.api.SaplVersion;

import java.io.Serial;

/**
 * Exports {@link EmbeddedSaplPlayground} as a {@code <sapl-playground>} custom
 * element.
 * <p>
 * Once the playground application is running, the component script is served at
 * {@code /web-component/sapl-playground.js} and can be embedded in any HTML
 * page.
 */
@CssImport("./embed-playground.css")
public class EmbeddedSaplPlaygroundExporter extends WebComponentExporter<EmbeddedSaplPlayground> {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public EmbeddedSaplPlaygroundExporter() {
        super("sapl-playground");
        addProperty("policy", "").onChange(EmbeddedSaplPlayground::setPolicy);
        addProperty("subscription", "").onChange(EmbeddedSaplPlayground::setSubscription);
    }

    @Override
    protected void configureInstance(WebComponent<EmbeddedSaplPlayground> webComponent,
            EmbeddedSaplPlayground component) {
        // No additional configuration needed for the initial validation.
    }
}
