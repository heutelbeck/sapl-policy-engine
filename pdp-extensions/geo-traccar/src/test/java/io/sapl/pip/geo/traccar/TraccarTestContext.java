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
package io.sapl.pip.geo.traccar;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
class TraccarTestContext {
    static AttributeAccessContext context(ObjectValue endpoint, ObjectValue credentials) {
        val servers   = ObjectValue.builder().put("default", endpoint).build();
        val variables = ObjectValue.builder().put("traccar", ObjectValue.builder().put("servers", servers).build())
                .build();
        val secrets   = ObjectValue.builder().put("traccar", ObjectValue.builder().put("default", credentials).build())
                .build();
        return new AttributeAccessContext(variables, secrets, Value.EMPTY_OBJECT);
    }
}
