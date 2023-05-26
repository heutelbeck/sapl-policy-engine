/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import com.google.common.base.Preconditions;

import io.sapl.api.interpreter.Val;
import lombok.Getter;
import reactor.core.publisher.Mono;

@Getter
public class Predicate {

	private final Bool bool;

	private final Bitmask conjunctions = new Bitmask();

	private final Bitmask falseForTruePredicate = new Bitmask();

	private final Bitmask falseForFalsePredicate = new Bitmask();

	public Predicate(final Bool bool) {
		this.bool = Preconditions.checkNotNull(bool);
	}

	public Mono<Val> evaluate() {
		return getBool().evaluateExpression();
	}

}
