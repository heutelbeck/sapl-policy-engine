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
package io.sapl.extensions.attributerepository;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;

public class TestAttributeRepositoryProvider implements AttributeRepositoryProvider {
    static final String TYPE             = "test";
    static final int    UNREACHABLE_PORT = 1;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean validate(ObjectValue config) {
        return (config.get("host") instanceof TextValue && config.get("port") instanceof NumberValue
                && config.get("password") instanceof TextValue);
    }

    @Override
    public AttributeRepository create(ObjectValue config, String pdpId) {
        if (config.get("port") instanceof NumberValue port && port.value().intValue() == UNREACHABLE_PORT) {
            throw new IllegalStateException("Test backend is unreachable.");
        }
        return new InMemoryAttributeRepository();
    }
}
