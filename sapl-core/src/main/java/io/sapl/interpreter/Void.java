/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.interpreter;

/**
 * The reactive version of a non reactive method returning void would return a Flux of
 * {@link java.lang.Void}. Since the only instance of the type java.lang.Void is
 * {@code null} which cannot be used for Flux items (e.g. Flux.just(null) is not
 * possible), another type representing void must be used.
 *
 * This is the purpose of this class. It provides a non null instance representing a void
 * result.
 */
public class Void {

	public static final Void INSTANCE = new Void();

}
