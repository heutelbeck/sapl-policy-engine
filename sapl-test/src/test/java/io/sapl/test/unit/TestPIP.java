/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.unit;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Text;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = TestPIP.NAME, description = TestPIP.DESCRIPTION)
public class TestPIP {

	public static final String NAME        = "test";
	public static final String DESCRIPTION = "Policy information Point for testing";

	@Attribute
	public Flux<Val> upper(@Text Val value, Map<String, JsonNode> variables) {
		return Flux.just(Val.of(value.get().asText().toUpperCase()));
	}

}