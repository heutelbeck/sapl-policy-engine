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
package io.sapl.api.pdp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The authorization subscription object defines the tuple of objects constituting a SAPL
 * authorization subscription. Each authorization subscription consists of:
 * <ul>
 * <li>a subject describing the entity which is requesting permission</li>
 * <li>an action describing for which activity the subject is requesting permission</li>
 * <li>a resource describing or even containing the resource for which the subject is
 * requesting the permission to execute the action</li>
 * <li>an environment object describing additional contextual information from the
 * environment which may be required for evaluating the underlying policies.</li>
 * </ul>
 *
 * All of these objects are expected to be Jackson JsonNodes representing an arbitrary
 * JSON object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class AuthorizationSubscription {

	private JsonNode subject;

	private JsonNode action;

	private JsonNode resource;

	private JsonNode environment;

}
