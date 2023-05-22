/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.CombinedDecision;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

@Getter
@ToString
public class PDPDecision implements TracedDecision {
	public static final String MODIFICATIONS_STRING       = "modifications";
	public static final String TIMESTAMP_STRING           = "timestamp";
	public static final String COMBINED_DECISION          = "combinedDecision";
	public static final String MATCHING_DOCUMENTS         = "matchingDocuments";
	public static final String AUTHORIZATION_DECISION     = "authorizationDecision";
	public static final String AUTHORIZATION_SUBSCRIPTION = "authorizationSubscription";
	public static final String OPERATOR                   = "operator";

	public static final ObjectMapper MAPPER = new ObjectMapper();

	static {
		MAPPER.registerModule(new Jdk8Module());
	}

	AuthorizationSubscription authorizationSubscription;
	List<SAPL>                matchingDocuments = new LinkedList<>();
	CombinedDecision          combinedDecision;
	Instant                   timestamp;
	LinkedList<Modification>  modifications     = new LinkedList<>();

	@Value
	private static class Modification {
		AuthorizationDecision authorizationDecision;
		String                explanation;
	}

	private PDPDecision(AuthorizationSubscription authorizationSubscription, List<SAPL> matchingDocuments,
			CombinedDecision combinedDecision, Instant timestamp, List<Modification> modifications) {
		this.authorizationSubscription = authorizationSubscription;
		this.combinedDecision          = combinedDecision;
		this.timestamp                 = timestamp;
		this.matchingDocuments.addAll(matchingDocuments);
		this.modifications.addAll(modifications);
	}

	public static PDPDecision of(AuthorizationSubscription authorizationSubscription, CombinedDecision combinedDecision,
			List<SAPL> matchingDocuments) {
		return new PDPDecision(authorizationSubscription, matchingDocuments, combinedDecision, Instant.now(),
				List.of());
	}

	public static PDPDecision of(AuthorizationSubscription authorizationSubscription,
			CombinedDecision combinedDecision) {
		return new PDPDecision(authorizationSubscription, List.of(), combinedDecision, Instant.now(), List.of());
	}

	@Override
	public AuthorizationDecision getAuthorizationDecision() {
		if (modifications.isEmpty())
			return combinedDecision.getAuthorizationDecision();
		return modifications.peekLast().getAuthorizationDecision();
	}

	@Override
	public TracedDecision modified(AuthorizationDecision authzDecision, String explanation) {
		var modified = new PDPDecision(authorizationSubscription, matchingDocuments, combinedDecision, timestamp,
				modifications);
		modified.modifications.add(new Modification(authzDecision, explanation));
		return modified;
	}

	@Override
	public JsonNode getTrace() {
		var trace = Val.JSON.objectNode();
		trace.set(OPERATOR, Val.JSON.textNode("Policy Decision Point"));
		trace.set(AUTHORIZATION_SUBSCRIPTION, MAPPER.valueToTree(authorizationSubscription));
		trace.set(AUTHORIZATION_DECISION, MAPPER.valueToTree(getAuthorizationDecision()));
		var matches = Val.JSON.arrayNode();
		matchingDocuments.forEach(doc -> matches.add(Val.JSON.textNode(doc.getPolicyElement().getSaplName())));
		trace.set(MATCHING_DOCUMENTS, matches);
		trace.set(COMBINED_DECISION, combinedDecision.getTrace());
		trace.set(TIMESTAMP_STRING, Val.JSON.textNode(timestamp.toString()));
		if (!modifications.isEmpty()) {
			trace.set(MODIFICATIONS_STRING, getModificationsTrace());
		}
		return trace;
	}

	private JsonNode getModificationsTrace() {
		var modificationTrace = Val.JSON.arrayNode();
		for (var mod : modifications) {
			var modJson = Val.JSON.objectNode();
			modJson.set(AUTHORIZATION_DECISION, MAPPER.valueToTree(mod.getAuthorizationDecision()));
			modJson.set("explanation", Val.JSON.textNode(mod.getExplanation()));
			modificationTrace.add(modJson);
		}
		return modificationTrace;
	}
}
