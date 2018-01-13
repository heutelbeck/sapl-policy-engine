/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter.pip;

import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pip.AttributeException;

public interface AttributeContext {

	JsonNode evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables) throws AttributeException;

	Boolean provides(String function);

	Collection<String> findersInLibrary(String libraryName);

	void loadPolicyInformationPoint(Object library) throws AttributeException;

	Collection<PolicyInformationPointDocumentation> getDocumentation();

}
