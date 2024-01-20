/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = TestPIP.NAME, description = TestPIP.DESCRIPTION)
public class TestPIP {

    public static final String NAME = "sapl.pip.test";

    public static final String DESCRIPTION = "Policy information Point for testing";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Attribute
    public Flux<Val> echo(Val value, Map<String, JsonNode> variables) {
        return Flux.just(value);
    }

    @Attribute
    public Flux<Val> echoRepeat(Val value, Map<String, JsonNode> variables, Val repetitions) {
        return Flux.just(Val
                .of(StringUtils.repeat(value.orElse(JSON.textNode("undefined")).asText(), repetitions.get().asInt())));
    }

    @Attribute
    public Flux<Val> someVariableOrNull(Val value, Map<String, JsonNode> variables) {
        if (value.isDefined() && variables.containsKey(value.get().asText())) {
            return Flux.just(Val.of(variables.get(value.get().asText()).deepCopy()));
        }
        return Val.fluxOfNull();
    }

}
