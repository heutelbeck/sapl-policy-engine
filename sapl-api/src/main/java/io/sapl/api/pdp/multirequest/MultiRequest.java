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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.pdp.Request;
import lombok.Value;

@Value
@JsonInclude(NON_EMPTY)
public class MultiRequest implements Iterable<IdentifiableRequest> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	static {
		final Jdk8Module jdk8Module = new Jdk8Module();
		// jdk8Module.configureAbsentsAsNulls(true);
		MAPPER.registerModule(jdk8Module);
	}

	private ArrayList<Object> subjects = new ArrayList<>();
	private ArrayList<Object> actions = new ArrayList<>();
	private ArrayList<Object> resources = new ArrayList<>();
	private ArrayList<Object> environments = new ArrayList<>();

	private Map<String, RequestElements> requests = new HashMap<>();

	/**
	 * Convenience method to add a request without environment data. Call
	 * {@link #addRequest(String, Object, Object, Object) addRequest(requestId,
	 * subject, action, resource, null)}.
	 *
	 * @param requestId the id identifying the request to be added.
	 * @param subject   the subject of the request to be added.
	 * @param action    the action of the request to be added.
	 * @param resource  the resource of the request to be added.
	 * @return this {@code MultiRequest} instance to support chaining of multiple
	 *         calls to {@code addRequest}.
	 */
	public MultiRequest addRequest(String requestId, Object subject, Object action, Object resource) {
		return addRequest(requestId, subject, action, resource, null);
	}

	/**
	 * Adds the request defined by the given subject, action, resource and
	 * environment. The given {@code requestId} is associated with the according
	 * response to allow the recipient of the PDP responses to correlate
	 * request-response pairs.
	 *
	 * @param requestId   the id identifying the request to be added.
	 * @param subject     the subject of the request to be added.
	 * @param action      the action of the request to be added.
	 * @param resource    the resource of the request to be added.
	 * @param environment the environment of the request to be added.
	 * @return this {@code MultiRequest} instance to support chaining of multiple
	 *         calls to {@code addRequest}.
	 */
	public MultiRequest addRequest(String requestId, Object subject, Object action, Object resource,
			Object environment) {
		requireNonNull(requestId, "requestId must not be null");

		final Integer subjectId = ensureIsElementOfListAndReturnIndex(subject, subjects);
		final Integer actionId = ensureIsElementOfListAndReturnIndex(action, actions);
		final Integer resourceId = ensureIsElementOfListAndReturnIndex(resource, resources);
		final Integer environmentId = ensureIsElementOfListAndReturnIndex(environment, environments);

		requests.put(requestId, new RequestElements(subjectId, actionId, resourceId, environmentId));
		return this;
	}

	private int ensureIsElementOfListAndReturnIndex(Object element, ArrayList<Object> list) {
		int index = list.indexOf(element);
		if (index == -1) {
			index = list.size();
			list.add(element);
		}
		return index;
	}

	public boolean hasRequests() {
		return !requests.isEmpty();
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

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiRequest {");
		for (IdentifiableRequest request : this) {
			sb.append("\n\t[").append("REQ-ID: ").append(request.getRequestId()).append(" | ").append("SUBJECT: ")
					.append(request.getRequest().getSubject()).append(" | ").append("ACTION: ")
					.append(request.getRequest().getAction()).append(" | ").append("RESOURCE: ")
					.append(request.getRequest().getResource()).append(" | ").append("ENVIRONMENT: ")
					.append(request.getRequest().getEnvironment()).append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

	private static Request toRequest(Object subject, Object action, Object resource, Object environment) {
		return new Request(MAPPER.valueToTree(subject), MAPPER.valueToTree(action), MAPPER.valueToTree(resource),
				MAPPER.valueToTree(environment));
	}
}
