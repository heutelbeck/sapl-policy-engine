/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.tests;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = MockPolicyInformationPoint.NAME)
public class MockPolicyInformationPoint {

	public static final String NAME = "pip";

	@Attribute
	public static Flux<Val> emptyString(Val leftHand, Map<String, JsonNode> variables) {
		return Flux.just(Val.of(""));
	}

	@Attribute
	public static Flux<Val> fail(Val leftHand, Map<String, JsonNode> variables) {
		return Val.errorFlux("SOME ERROR");
	}

	@Attribute
	public static Flux<Val> empty(Val leftHand, Map<String, JsonNode> variables) {
		return Flux.empty();
	}

	@Attribute
	public static Flux<Val> numbers(Val leftHand, Map<String, JsonNode> variables) {
		return Flux.just(Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5), Val.of(6));
	}

	@Attribute
	public static Flux<Val> mixed(Val leftHand, Map<String, JsonNode> variables) {
		return Flux.just(Val.TRUE, Val.of(1), Val.of(2), Val.TRUE, Val.of(4), Val.FALSE, Val.of(6),
				Val.error("SOME ERROR"), Val.TRUE).delayElements(Duration.ofSeconds(1));
	}

	@Attribute
	public static Flux<Val> booleans(Val leftHand, Map<String, JsonNode> variables) {
		return Flux.just(Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE, Val.TRUE).delayElements(Duration.ofSeconds(1));
	}

	@Attribute
	public static Flux<Val> numbersUndefined(Val leftHand, Map<String, JsonNode> variables) {
		return Flux.just(Val.of(1), Val.of(2), Val.of(3), Val.UNDEFINED, Val.of(5), Val.of(6));
	}

}
