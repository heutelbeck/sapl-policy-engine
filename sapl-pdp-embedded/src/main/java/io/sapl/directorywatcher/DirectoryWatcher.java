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
package io.sapl.directorywatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers a directory watch service on the directory passed to the
 * constructor and forwards all create, delete and modify events to a
 * {@link DirectoryWatchEventConsumer}.
 */
@Slf4j
@RequiredArgsConstructor
public class DirectoryWatcher {

	private final Path watchedDir;

	/**
	 * Registers a directory watch service on the directory passed to the
	 * constructor and forwards all create, delete and modify events to the given
	 * {@code eventConsumer}.
	 * 
	 * @param eventConsumer the consumer to be notified on create, delete and modify
	 *                      events or when the watch service has completed or the
	 *                      watched directory is no longer accessible.
	 */
	public void watch(DirectoryWatchEventConsumer<Path> eventConsumer) {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			watchedDir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			while (!eventConsumer.isCanceled()) {
				final WatchKey key = watcher.take();
				handleWatchKey(key, eventConsumer);
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		} catch (InterruptedException e) {
			log.debug("Shutdown directory watcher. Thread has been interrupted.");
			Thread.currentThread().interrupt();
		} finally {
			eventConsumer.onComplete();
		}
	}

	@SuppressWarnings("unchecked")
	void handleWatchKey(WatchKey key, DirectoryWatchEventConsumer<Path> eventConsumer) {
		for (WatchEvent<?> event : key.pollEvents()) {
			WatchEvent.Kind<?> kind = event.kind();
			if (kind == OVERFLOW) {
				continue;
			}
			eventConsumer.onEvent((WatchEvent<Path>) event);
		}

		// If the key is no longer valid, the directory is inaccessible.
		boolean valid = key.reset();
		if (!valid) {
			eventConsumer.cancel();
		}
	}

}
