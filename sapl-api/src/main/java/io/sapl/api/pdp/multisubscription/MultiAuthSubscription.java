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
package io.sapl.api.pdp.multisubscription;

import static java.util.Objects.requireNonNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.pdp.AuthSubscription;
import lombok.Value;

/**
 * A multi-subscription holds a list of subjects, a list of actions, a list of resources,
 * a list of environments (which are the elements of a {@link AuthSubscription SAPL
 * authorization subscription}) and a map holding subscription IDs and corresponding
 * {@link AuthSubscriptionElements authorization subscription elements}. It provides
 * methods to {@link #addAuthSubscription(String, Object, Object, Object, Object) add}
 * single authorization subscriptions and to {@link #iterator() iterate} over all the
 * authorization subscriptions.
 *
 * @see AuthSubscription
 */
@Value
@JsonInclude(NON_EMPTY)
public class MultiAuthSubscription implements Iterable<IdentifiableAuthSubscription> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	static {
		final Jdk8Module jdk8Module = new Jdk8Module();
		// jdk8Module.configureAbsentsAsNulls(true);
		MAPPER.registerModule(jdk8Module);
	}

	private List<Object> subjects = new ArrayList<>();

	private List<Object> actions = new ArrayList<>();

	private List<Object> resources = new ArrayList<>();

	private List<Object> environments = new ArrayList<>();

	private Map<String, AuthSubscriptionElements> authSubscriptions = new HashMap<>();

	/**
	 * Convenience method to add an authorization subscription without environment data.
	 * Calls {@link #addAuthSubscription(String, Object, Object, Object)
	 * addAuthSubscription(subscriptionId, subject, action, resource, null)}.
	 * @param subscriptionId the id identifying the authorization subscription to be
	 * added.
	 * @param subject the subject of the authorization subscription to be added.
	 * @param action the action of the authorization subscription to be added.
	 * @param resource the resource of the authorization subscription to be added.
	 * @return this {@code MultiAuthSubscription} instance to support chaining of multiple
	 * calls to {@code addAuthSubscription}.
	 */
	public MultiAuthSubscription addAuthSubscription(String subscriptionId, Object subject, Object action,
			Object resource) {
		return addAuthSubscription(subscriptionId, subject, action, resource, null);
	}

	/**
	 * Adds the authorization subscription defined by the given subject, action, resource
	 * and environment. The given {@code subscriptionId} is associated with the according
	 * decision to allow the recipient of the PDP decision to correlate
	 * subscription/decision pairs.
	 * @param subscriptionId the id identifying the authorization subscription to be
	 * added.
	 * @param subject the subject of the authorization subscription to be added.
	 * @param action the action of the authorization subscription to be added.
	 * @param resource the resource of the authorization subscription to be added.
	 * @param environment the environment of the authorization subscription to be added.
	 * @return this {@code MultiAuthSubscription} instance to support chaining of multiple
	 * calls to {@code addAuthSubscription}.
	 */
	public MultiAuthSubscription addAuthSubscription(String subscriptionId, Object subject, Object action,
			Object resource, Object environment) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");

		final Integer subjectId = ensureIsElementOfListAndReturnIndex(subject, subjects);
		final Integer actionId = ensureIsElementOfListAndReturnIndex(action, actions);
		final Integer resourceId = ensureIsElementOfListAndReturnIndex(resource, resources);
		final Integer environmentId = ensureIsElementOfListAndReturnIndex(environment, environments);

		authSubscriptions.put(subscriptionId,
				new AuthSubscriptionElements(subjectId, actionId, resourceId, environmentId));
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
	 * @return {@code true} if this multi-subscription holds at least one authorization
	 * subscription, {@code false} otherwise.
	 */
	public boolean hasAuthSubscriptions() {
		return !authSubscriptions.isEmpty();
	}

	/**
	 * Returns the authorization subscription related to the given ID or {@code null} if
	 * this multi-subscription contains no such ID.
	 * @param subscriptionId the ID of the authorization subscription to be returned.
	 * @return the authorization subscription related to the given ID or {@code null}.
	 */
	public AuthSubscription getAuthSubscriptionWithId(String subscriptionId) {
		final AuthSubscriptionElements subscriptionElements = authSubscriptions.get(subscriptionId);
		if (subscriptionElements != null) {
			return toAuthSubscription(subscriptionElements);
		}
		return null;
	}

	/**
	 * @return an {@link Iterator iterator} providing access to the
	 * {@link IdentifiableAuthSubscription identifiable authorization subscriptions}
	 * created from the data held by this multi-subscription.
	 */
	@Override
	public Iterator<IdentifiableAuthSubscription> iterator() {
		final Iterator<Map.Entry<String, AuthSubscriptionElements>> subscriptionIterator = authSubscriptions
				.entrySet().iterator();
		return new Iterator<IdentifiableAuthSubscription>() {
			@Override
			public boolean hasNext() {
				return subscriptionIterator.hasNext();
			}

			@Override
			public IdentifiableAuthSubscription next() {
				final Map.Entry<String, AuthSubscriptionElements> entry = subscriptionIterator.next();
				final String id = entry.getKey();
				final AuthSubscriptionElements subscriptionElements = entry.getValue();
				final AuthSubscription authSubscription = toAuthSubscription(subscriptionElements);
				return new IdentifiableAuthSubscription(id, authSubscription);
			}
		};
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiAuthSubscription {");
		for (IdentifiableAuthSubscription subscription : this) {
			sb.append("\n\t[").append("REQ-ID: ").append(subscription.getAuthSubscriptionId()).append(" | ")
					.append("SUBJECT: ").append(subscription.getAuthSubscription().getSubject()).append(" | ")
					.append("ACTION: ").append(subscription.getAuthSubscription().getAction()).append(" | ")
					.append("RESOURCE: ").append(subscription.getAuthSubscription().getResource()).append(" | ")
					.append("ENVIRONMENT: ").append(subscription.getAuthSubscription().getEnvironment()).append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

	private AuthSubscription toAuthSubscription(AuthSubscriptionElements subscriptionElements) {
		final Object subject = subjects.get(subscriptionElements.getSubjectId());
		final Object action = actions.get(subscriptionElements.getActionId());
		final Object resource = resources.get(subscriptionElements.getResourceId());
		final Object environment = environments.get(subscriptionElements.getEnvironmentId());
		return new AuthSubscription(MAPPER.valueToTree(subject), MAPPER.valueToTree(action),
				MAPPER.valueToTree(resource), MAPPER.valueToTree(environment));
	}

}
