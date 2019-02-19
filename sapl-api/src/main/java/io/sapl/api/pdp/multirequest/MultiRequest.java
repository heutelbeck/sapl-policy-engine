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
package io.sapl.api.pdp.multirequest;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Request;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(NON_EMPTY)
public class MultiRequest implements Iterable<IdentifiableRequest> {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private Map<String, Object> subjects = new HashMap<>();
	private Map<String, Object> actions = new HashMap<>();
	private Map<String, Object> resources = new HashMap<>();
	private Map<String, Object> environments = new HashMap<>();
	private Map<String, RequestElements> requests = new HashMap<>();

	public void addSubject(IdentifiableSubject identifiableSubject) {
		requireNonNull(identifiableSubject, "subject must not be null");
		this.subjects.put(identifiableSubject.getId(), identifiableSubject.getSubject());
	}

	/**
	 * Convenience method to add a string subject using its value as its ID.
	 *
	 * @param subject the string subject to be added.
	 */
	public void addSubject(String subject) {
		addSubject(new IdentifiableSubject(subject, subject));
	}

	public void addAction(IdentifiableAction identifiableAction) {
		requireNonNull(identifiableAction, "action must not be null");
		this.actions.put(identifiableAction.getId(), identifiableAction.getAction());
	}

	/**
	 * Convenience method to add a string action using its value as its ID.
	 *
	 * @param action the string action to be added.
	 */
	public void addAction(String action) {
		addAction(new IdentifiableAction(action, action));
	}

	public void addResource(IdentifiableResource identifiableResource) {
		requireNonNull(identifiableResource, "resource must not be null");
		this.resources.put(identifiableResource.getId(), identifiableResource.getResource());
	}

	/**
	 * Convenience method to add a string resource using its value as its ID.
	 *
	 * @param resource the string resource to be added.
	 */
	public void addResource(String resource) {
		addResource(new IdentifiableResource(resource, resource));
	}

	public void addEnvironment(IdentifiableEnvironment identifiableEnvironment) {
		requireNonNull(identifiableEnvironment, "environment must not be null");
		this.environments.put(identifiableEnvironment.getId(), identifiableEnvironment.getEnvironment());
	}

	/**
	 * Convenience method to add a string environment using its value as its ID.
	 *
	 * @param environment the string environment to be added.
	 */
	public void addEnvironment(String environment) {
		addEnvironment(new IdentifiableEnvironment(environment, environment));
	}

	public void addRequest(String requestId, RequestElements requestElements) {
		requireNonNull(requestId, "requestId must not be null");
		requireNonNull(requestElements, "requestParts must not be null");
		validateRequest(requestElements);
		this.requests.put(requestId, requestElements);
	}

	private void validateRequest(RequestElements requestElements) throws UnknownIdException {
		final String subjectId = requestElements.getSubjectId();
		if (isNotEmpty(subjectId) && ! subjects.containsKey(subjectId)) {
			throw new UnknownIdException("The multi-request does not contain a subject with id " + subjectId);
		}

		final String actionId = requestElements.getActionId();
		if (isNotEmpty(actionId) && ! actions.containsKey(actionId)) {
			throw new UnknownIdException("The multi-request does not contain an action with id " + actionId);
		}

		final String resourceId = requestElements.getResourceId();
		if (isNotEmpty(resourceId) && ! resources.containsKey(resourceId)) {
			throw new UnknownIdException("The multi-request does not contain a resource with id " + resourceId);
		}

		final String environmentId = requestElements.getEnvironmentId();
		if (isNotEmpty(environmentId) && ! environments.containsKey(environmentId)) {
			throw new UnknownIdException("The multi-request does not contain an environment with id " + environmentId);
		}
	}

	private static boolean isNotEmpty(String s) {
		return s != null && ! s.isEmpty();
	}

	public boolean hasRequests() {
		return ! requests.isEmpty();
	}

	@Override
	public Iterator<IdentifiableRequest> iterator() {
		final Iterator<Map.Entry<String, RequestElements>> requestIterator = requests.entrySet().iterator();
		return new Iterator<IdentifiableRequest>() {
			@Override
			public boolean hasNext() {
				return requestIterator.hasNext();
			}

			@Override
			public IdentifiableRequest next() {
				final Map.Entry<String, RequestElements> requestsEntry = requestIterator.next();
				final String id = requestsEntry.getKey();
				final RequestElements requestElements = requestsEntry.getValue();
				final Object subject = subjects.get(requestElements.getSubjectId());
				final Object action = actions.get(requestElements.getActionId());
				final Object resource = resources.get(requestElements.getResourceId());
				final Object environment = environments.get(requestElements.getEnvironmentId());
				final Request request = toRequest(subject, action, resource, environment);
				return new IdentifiableRequest(id, request);
			}
		};
	}

	private static Request toRequest(Object subject, Object action, Object resource, Object environment) {
		return new Request(
				MAPPER.convertValue(subject, JsonNode.class),
				MAPPER.convertValue(action, JsonNode.class),
				MAPPER.convertValue(resource, JsonNode.class),
				MAPPER.convertValue(environment, JsonNode.class)
		);
	}
}
