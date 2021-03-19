/*
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pep;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Utility interface for implementing constraint handlers.
 * 
 * Both, obligations and advices are considered constraints. The only difference
 * is the behavior of the PEP, if no handler for an obligation can be found. If
 * no obligation handler for any obligation is present, the PEP must not grant
 * access.
 * 
 * A constraint handler is a component which is capable of implementing the
 * behavior implied by a constraint.
 *
 */
public interface ConstraintHandler {

	/**
	 * 
	 * Perform the behavior required by the constraint. If the behavior was not
	 * successfully completed, return false. If the constraint was an obligation and
	 * the handle method returns false, the PEP must not grant access.
	 * 
	 * @param constraint the constraint JSON value from the AuthorizationDecision
	 *                   object.
	 * @return true, iff the constraint was handled successfully
	 */
	boolean handle(JsonNode constraint);

	/**
	 * SAPL does not impose any scheme on constraint values. Specifying such a
	 * scheme is up to the application developers. However, it should be possible to
	 * distinguish between different types of constraints by inspecting the JSON
	 * value.
	 * 
	 * This method is used to perform this inspection, so that a PEP can decide if a
	 * matching constraint handler is present and to call it if applicable.
	 * 
	 * @param constraint the constraint JSON value from the AuthorizationDecision
	 *                   object.
	 * @return true, iff the class is able to handle the given constraint.
	 */
	boolean canHandle(JsonNode constraint);

}
