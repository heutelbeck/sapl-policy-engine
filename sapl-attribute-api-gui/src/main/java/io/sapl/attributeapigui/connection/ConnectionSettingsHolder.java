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
package io.sapl.attributeapigui.connection;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.sapl.attributeapigui.config.AttributeApiConnectionProperties;

@VaadinSessionScope
@Component
public class ConnectionSettingsHolder {
    private final AtomicReference<ConnectionSettings> current;

    public ConnectionSettingsHolder(AttributeApiConnectionProperties properties) {
        this.current = new AtomicReference<>(ConnectionSettings.from(properties));
    }

    public ConnectionSettings get() {
        return current.get();
    }

    public void update(ConnectionSettings next) {
        current.set(next);
    }
}
