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
package io.sapl.grammar.ide.contentassist;

import java.util.Collection;

/**
 * Base interface to provide library and function search methods for the proposal
 * provider.
 */
public interface LibraryAttributeFinder {

	/**
	 * Offers a list of matching libraries and functions on the basis of the provided
	 * identifier.
	 * @param identifier A string that is used as needle to look for partially matching
	 * libraries and function, e.g. "clock.n"
	 * @return Returns a list with libraries and functions that partially match the
	 * needle.
	 */
	Collection<String> getAvailableAttributes(String identifier);

}
