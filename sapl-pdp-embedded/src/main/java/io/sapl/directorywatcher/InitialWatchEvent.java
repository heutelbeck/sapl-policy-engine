/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Directory watch fluxes are combined with PDP fluxes. Therefore an initial event is
 * required even if nothing has been changed in the directory. Otherwise the combined flux
 * would only emit items after the first file modification. This class defines the type of
 * the initial directory watch event. No PRP document index update or PDP reconfiguration
 * will be triggered upon this event.
 */
public class InitialWatchEvent implements WatchEvent<Path> {

	public static final WatchEvent<Path> INSTANCE = new InitialWatchEvent();

	private InitialWatchEvent() {
		// singleton
	}

	@Override
	public Kind<Path> kind() {
		return ENTRY_MODIFY;
	}

	@Override
	public int count() {
		return 0;
	}

	@Override
	public Path context() {
		return null;
	}

}
