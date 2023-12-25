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
package io.sapl.pip;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = SchemaTestPIP.NAME, description = SchemaTestPIP.DESCRIPTION)
public class SchemaTestPIP {

    static final String NAME = "TestPIP";

    static final String DESCRIPTION = "Description of SchemaTestPIP";

    static final String PERSON_SCHEMA = "{\"name\": {\"type\": \"string\"}, \"age\": {\"type\": \"number\"}}";

    @Attribute(schema = PERSON_SCHEMA, docs = "Documented Environment Attribute")
    public Flux<Val> person(Val a1) {
        return Flux.empty();
    }
}
