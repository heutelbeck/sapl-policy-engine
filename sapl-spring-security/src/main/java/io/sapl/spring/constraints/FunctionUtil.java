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
package io.sapl.spring.constraints;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FunctionUtil {

	public static final <T> Consumer<T> sink() {
		return t -> {
		};
	}

	public static final LongConsumer longSink() {
		return t -> {
		};
	}

	public static final <T> Predicate<T> all() {
		return t -> true;
	}

	public static final Runnable noop() {
		return () -> {
		};
	}
}
