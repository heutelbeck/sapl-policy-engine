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
package io.sapl.api.model;

import io.sapl.api.pdp.traced.AttributeRecord;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record TracedValue(Value value, List<AttributeRecord> contributingAttributes) {
    public static TracedValue of(Value value) {
        return new TracedValue(value, List.of());
    }

    public TracedValue with(List<AttributeRecord> additionalAttributes) {
        val contributions = new ArrayList<AttributeRecord>(additionalAttributes);
        contributions.addAll(contributingAttributes);
        return new TracedValue(value, contributions);
    }
}
