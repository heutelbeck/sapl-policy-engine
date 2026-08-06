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
package io.sapl.attributes.libraries;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.api.attributes.PolicyInformationPoint;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PolicyInformationPoint(name = "user")
public class UserPolicyInformationPoint {
    private String message = "";

    @Attribute(name = "age")
    public Stream<Value> age(Value subject, Value... args) {
        message = "PIP: Attribute age called for " + subject;
        log.info(message);
        return Streams.empty();
    }

    @Attribute(name = "department")
    public Stream<Value> department(Value subject, Value... args) {
        message = "PIP: Attribute department called for " + subject + " with values";
        log.info(message);
        return Streams.empty();
    }

    @Attribute(name = "role")
    public Stream<Value> role(Value subject, Value arg) {
        log.info("PIP role called with arg: {}", arg);
        return Streams.empty();
    }

    @Attribute(name = "plan")
    public Stream<Value> plan(Value subject, Value arg1, Value arg2) {
        return Streams.empty();
    }
}
