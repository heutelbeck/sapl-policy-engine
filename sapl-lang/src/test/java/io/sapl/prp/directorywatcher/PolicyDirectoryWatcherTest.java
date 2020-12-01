/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.directorywatcher;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
public class PolicyDirectoryWatcherTest {

	/**
	 * Run this test and create, modify or delete files under the directory
	 * target/test-classes/policies. To terminate the test, add a file called "stop.sapl"
	 * or delete the whole directory.
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	// @Test
	public void watchPoliciesDirectory() throws URISyntaxException, InterruptedException {
		Path watchDir = Paths.get(getClass().getResource("/policies").toURI());
		log.info("watchDir: {}", watchDir);
		final DirectoryWatcher watcher = new DirectoryWatcher(watchDir);
		final CountDownLatch cdl = new CountDownLatch(1);
		watcher.watch(new DirectoryWatchEventConsumer<>() {

            private boolean isCanceled;

            @Override
            public void onEvent(WatchEvent<Path> event) {
                log.info("watch event of kind {} for path {}", event.kind().name(), event.context().toString());
                Path filename = event.context();
                if (filename.toString().equals("stop.sapl")) {
                    cancel();
                }
            }

            @Override
            public void onComplete() {
                cdl.countDown();
            }

            @Override
            public void cancel() {
                isCanceled = true;
            }

            @Override
            public boolean isCanceled() {
                return isCanceled;
            }
        });
		cdl.await();
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void handleWatchKey() {
		// given
		final WatchEvent createEvent = mock(WatchEvent.class);
		when(createEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
		final WatchEvent modifyEvent = mock(WatchEvent.class);
		when(modifyEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_MODIFY);
		final WatchEvent deleteEvent = mock(WatchEvent.class);
		when(deleteEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_DELETE);
		final WatchEvent overflowEvent = mock(WatchEvent.class);
		when(overflowEvent.kind()).thenReturn(StandardWatchEventKinds.OVERFLOW);

		final WatchKey watchKey = mock(WatchKey.class);
		when(watchKey.pollEvents()).thenReturn(Arrays.asList(createEvent, modifyEvent, deleteEvent, overflowEvent));
		when(watchKey.isValid()).thenReturn(false);

		final DirectoryWatchEventConsumer eventConsumer = spy(DirectoryWatchEventConsumer.class);

		// when
		new DirectoryWatcher(null).handleWatchKey(watchKey, eventConsumer);

		// then
		verify(eventConsumer, times(3)).onEvent(any(WatchEvent.class));
		verify(eventConsumer, times(1)).cancel();
	}

}
