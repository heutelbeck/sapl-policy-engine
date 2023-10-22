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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class CanonicalIndexDataContainerTests {

	@Test
	void testGetNumberOfFormulasWithConjunction() {
		var numberOfFormulasWithConjunction = new int[] { 0, 1, 2, 3 };

		var container = new CanonicalIndexDataContainer(Collections.emptyMap(), Collections.emptyMap(),
				Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
				new int[0], numberOfFormulasWithConjunction);

		assertThat(container.getNumberOfFormulasWithConjunction(0), is(0));
		assertThat(container.getNumberOfFormulasWithConjunction(3), is(3));

		assertThrows(ArrayIndexOutOfBoundsException.class,
				() -> container.getNumberOfFormulasWithConjunction(Integer.MIN_VALUE));
		assertThrows(ArrayIndexOutOfBoundsException.class,
				() -> container.getNumberOfFormulasWithConjunction(Integer.MAX_VALUE));
	}

}
