/*
 * Copyright © 2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A multi-subscription holds a list of subjects, a list of actions, a list of
 * resources, a list of environments (which are the elements of a
 * {@link AuthorizationSubscription SAPL authorization subscription}) and a map
 * holding subscription IDs and corresponding
 * {@link AuthorizationSubscriptionElements authorization subscription
 * elements}. It provides methods to
 * {@link #addAuthorizationSubscription(String, Object, Object, Object, Object)
 * add} single authorization subscriptions and to {@link #iterator() iterate}
 * over all the authorization subscriptions.
 *
 * @see AuthorizationSubscription
 */
@Data
@NoArgsConstructor
@JsonInclude(NON_EMPTY)
public class MultiAuthorizationSubscription implements Iterable<IdentifiableAuthorizationSubscription> {

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

	@NotEmpty
	List<Object> subjects = new ArrayList<>();

	@NotEmpty
	List<Object> actions = new ArrayList<>();

	@NotEmpty
	List<Object> resources = new ArrayList<>();

	@NotEmpty
	List<Object> environments = new ArrayList<>();

	@NotEmpty
	Map<String, AuthorizationSubscriptionElements> authorizationSubscriptions = new HashMap<>();

	/**
	 * Convenience method to add an authorization subscription without environment
	 * data. Calls
	 * {@link #addAuthorizationSubscription(String, Object, Object, Object)
	 * addAuthorizationSubscription(subscriptionId, subject, action, resource,
	 * null)}.
	 * 
	 * @param subscriptionId the id identifying the authorization subscription to be
	 *                       added.
	 * @param subject        the subject of the authorization subscription to be
	 *                       added.
	 * @param action         the action of the authorization subscription to be
	 *                       added.
	 * @param resource       the resource of the authorization subscription to be
	 *                       added.
	 * @return this {@code MultiAuthorizationSubscription} instance to support
	 *         chaining of multiple calls to {@code addAuthorizationSubscription}.
	 */
	public MultiAuthorizationSubscription addAuthorizationSubscription(String subscriptionId, Object subject,
			Object action, Object resource) {
		return addAuthorizationSubscription(subscriptionId, subject, action, resource, null);
	}

	/**
	 * Convenience method to add an authorization subscription without environment
	 * data. Calls
	 * {@link #addAuthorizationSubscription(String, Object, Object, Object)
	 * addAuthorizationSubscription(subscriptionId, subject, action, resource,
	 * null)}.
	 * 
	 * @param subscriptionId the id identifying the authorization subscription to be
	 *                       added.
	 * @param subscription   an authorization subscription.
	 * @return this {@code MultiAuthorizationSubscription} instance to support
	 *         chaining of multiple calls to {@code addAuthorizationSubscription}.
	 */
	public MultiAuthorizationSubscription addAuthorizationSubscription(String subscriptionId,
			AuthorizationSubscription subscription) {
		return addAuthorizationSubscription(subscriptionId, subscription.getSubject(), subscription.getAction(),
				subscription.getResource(), subscription.getEnvironment());
	}

	/**
	 * Adds the authorization subscription defined by the given subject, action,
	 * resource and environment. The given {@code subscriptionId} is associated with
	 * the according decision to allow the recipient of the PDP decision to
	 * correlate subscription/decision pairs.
	 * 
	 * @param subscriptionId the id identifying the authorization subscription to be
	 *                       added.
	 * @param subject        the subject of the authorization subscription to be
	 *                       added.
	 * @param action         the action of the authorization subscription to be
	 *                       added.
	 * @param resource       the resource of the authorization subscription to be
	 *                       added.
	 * @param environment    the environment of the authorization subscription to be
	 *                       added.
	 * @return this {@code MultiAuthorizationSubscription} instance to support
	 *         chaining of multiple calls to {@code addAuthorizationSubscription}.
	 */
	public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
			Object action, Object resource, Object environment) {

		if (authorizationSubscriptions.containsKey(subscriptionId))
			throw new IllegalArgumentException("Cannot add two sunscriptions with the same ID: " + subscriptionId);

		var subjectId = ensureIsElementOfListAndReturnIndex(subject, subjects);
		var actionId = ensureIsElementOfListAndReturnIndex(action, actions);
		var resourceId = ensureIsElementOfListAndReturnIndex(resource, resources);
		var environmentId = ensureIsElementOfListAndReturnIndex(environment, environments);

		authorizationSubscriptions.put(subscriptionId,
				new AuthorizationSubscriptionElements(subjectId, actionId, resourceId, environmentId));
		return this;
	}

	private int ensureIsElementOfListAndReturnIndex(Object element, List<Object> list) {
		int index = list.indexOf(element);
		if (index == -1) {
			index = list.size();
			list.add(element);
		}
		return index;
	}

	/**
	 * @return {@code true} if this multi-subscription holds at least one
	 *         authorization subscription, {@code false} otherwise.
	 */
	public boolean hasAuthorizationSubscriptions() {
		return !authorizationSubscriptions.isEmpty();
	}

	/**
	 * Returns the authorization subscription related to the given ID or
	 * {@code null} if this multi-subscription contains no such ID.
	 * 
	 * @param subscriptionId the ID of the authorization subscription to be
	 *                       returned.
	 * @return the authorization subscription related to the given ID or
	 *         {@code null}.
	 */
	public AuthorizationSubscription getAuthorizationSubscriptionWithId(String subscriptionId) {
		final AuthorizationSubscriptionElements subscriptionElements = authorizationSubscriptions.get(subscriptionId);
		if (subscriptionElements != null) {
			return toAuthzSubscription(subscriptionElements);
		}
		return null;
	}

	/**
	 * @return an {@link Iterator iterator} providing access to the
	 *         {@link IdentifiableAuthorizationSubscription identifiable
	 *         authorization subscriptions} created from the data held by this
	 *         multi-subscription.
	 */
	@Override
	public Iterator<IdentifiableAuthorizationSubscription> iterator() {
		final Iterator<Map.Entry<String, AuthorizationSubscriptionElements>> subscriptionIterator = authorizationSubscriptions
				.entrySet().iterator();
		return new Iterator<IdentifiableAuthorizationSubscription>() {
			@Override
			public boolean hasNext() {
				return subscriptionIterator.hasNext();
			}

			@Override
			public IdentifiableAuthorizationSubscription next() {
				final Map.Entry<String, AuthorizationSubscriptionElements> entry = subscriptionIterator.next();
				final String id = entry.getKey();
				final AuthorizationSubscriptionElements subscriptionElements = entry.getValue();
				final AuthorizationSubscription authzSubscription = toAuthzSubscription(subscriptionElements);
				return new IdentifiableAuthorizationSubscription(id, authzSubscription);
			}
		};
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiAuthorizationSubscription {");
		for (IdentifiableAuthorizationSubscription subscription : this) {
			sb.append("\n\t[").append("SUBSCRIPTION-ID: ").append(subscription.getAuthorizationSubscriptionId())
					.append(" | ").append("SUBJECT: ").append(subscription.getAuthorizationSubscription().getSubject())
					.append(" | ").append("ACTION: ").append(subscription.getAuthorizationSubscription().getAction())
					.append(" | ").append("RESOURCE: ")
					.append(subscription.getAuthorizationSubscription().getResource()).append(" | ")
					.append("ENVIRONMENT: ").append(subscription.getAuthorizationSubscription().getEnvironment())
					.append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

	private AuthorizationSubscription toAuthzSubscription(AuthorizationSubscriptionElements subscriptionElements) {
		final Object subject = subjects.get(subscriptionElements.getSubjectId());
		final Object action = actions.get(subscriptionElements.getActionId());
		final Object resource = resources.get(subscriptionElements.getResourceId());
		final Object environment = environments.get(subscriptionElements.getEnvironmentId());
		return new AuthorizationSubscription(MAPPER.valueToTree(subject), MAPPER.valueToTree(action),
				MAPPER.valueToTree(resource), MAPPER.valueToTree(environment));
	}

}
