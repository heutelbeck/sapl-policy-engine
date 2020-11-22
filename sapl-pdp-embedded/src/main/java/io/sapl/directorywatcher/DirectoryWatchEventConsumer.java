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

import java.nio.file.WatchEvent;

/**
 * Consumes directory watch events emitted by the {@link DirectoryWatcher} and is called
 * when the watch service has completed or when the watched directory is no longer
 * accessible.
 *
 * @param <T> the type of the watch event's context
 */
public interface DirectoryWatchEventConsumer<T> {

	/**
	 * Called upon each watch event emitted by the directory watch service registered to
	 * the policy directory.
	 * @param event the directory watch event.
	 */
	void onEvent(WatchEvent<T> event);

	/**
	 * Called when the watch service has completed.
	 */
	void onComplete();

	/**
	 * Called when the watched directory is no longer accessible.
	 */
	void cancel();

	/**
	 * @return {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
	 */
	boolean isCanceled();

}
