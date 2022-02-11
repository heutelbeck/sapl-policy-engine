/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BitmaskTest {

	Bitmask bitmask;

	@BeforeEach
	void setUp() {
		bitmask = new Bitmask();
		bitmask.set(2, 4);
	}

	@Test
	void flipTest() {
		assertThat(bitmask.toString(), is("{2, 3}"));

		bitmask.flip(0, 4);
		assertThat(bitmask.toString(), is("{0, 1}"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void forEachSetBitTest() {
		var listMock = (List<Integer>) mock(List.class);

		bitmask.forEachSetBit(listMock::add);

		verify(listMock, times(2)).add(anyInt());
	}

}
