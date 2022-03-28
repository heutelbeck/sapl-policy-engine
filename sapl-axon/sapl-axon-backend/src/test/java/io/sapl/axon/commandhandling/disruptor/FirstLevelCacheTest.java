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
package io.sapl.axon.commandhandling.disruptor;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.util.stream.IntStream;

import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FirstLevelCacheTest {
	private EventSourcedAggregate<MyAggregate> cacheable;

	private FirstLevelCache<MyAggregate> testSubject;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		cacheable = mock(EventSourcedAggregate.class);
		testSubject = new FirstLevelCache<>();
	}

	@Test
	void shouldPut() {
		testSubject.put("key", cacheable);

		assertEquals(1, testSubject.size());
	}

	@Test
	void shouldGet() {
		testSubject.put("key", cacheable);
		EventSourcedAggregate<MyAggregate> cached = testSubject.get("key");

		assertSame(cached, cacheable);
	}

	@Test
	void shouldRemove() {
		testSubject.put("key", cacheable);
		EventSourcedAggregate<MyAggregate> cached = testSubject.remove("key");

		assertSame(cached, cacheable);
		assertEquals(0, testSubject.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldClearWeakValues() throws Exception {
		FirstLevelCache<MyAggregate> customTestSubject = new FirstLevelCache<>();

		int numberOfEntries = 200;
		// noinspection unchecked
		IntStream.range(0, numberOfEntries).mapToObj(i -> "key-" + i)
				.forEach(key -> customTestSubject.put(key, mock(EventSourcedAggregate.class)));

		int i = 0;
		while (i < 10 && customTestSubject.size() > 0) {
			System.gc();
			// noinspection BusyWait
			sleep(50);
			i++;
		}
		assertEquals(0, customTestSubject.size());
	}

	static class MyAggregate {

	}
}
