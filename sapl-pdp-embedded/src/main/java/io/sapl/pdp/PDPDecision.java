package io.sapl.pdp;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.Traced;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PDPDecision implements Traced {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	AuthorizationSubscription authorizationSubscription;
	List<SAPL>                matchingDocuments = new LinkedList<>();
	CombinedDecision          combinedDecision;
	Instant                   timestamp;

	private PDPDecision(AuthorizationSubscription authorizationSubscription, List<SAPL> matchingDocuments,
			CombinedDecision combinedDecision, Instant timestamp) {
		this.authorizationSubscription = authorizationSubscription;
		this.combinedDecision          = combinedDecision;
		this.timestamp                 = timestamp;
		this.matchingDocuments.addAll(matchingDocuments);
	}

	public static PDPDecision of(AuthorizationSubscription authorizationSubscription, CombinedDecision combinedDecision,
			List<SAPL> matchingDocuments) {
		return new PDPDecision(authorizationSubscription, matchingDocuments, combinedDecision, Instant.now());
	}

	public static PDPDecision of(AuthorizationSubscription authorizationSubscription,
			CombinedDecision combinedDecision) {
		return new PDPDecision(authorizationSubscription, List.of(), combinedDecision, Instant.now());
	}

	public AuthorizationDecision getAuthorizationDecision() {
		return combinedDecision.getAuthorizationDecision();
	}

	@Override
	public JsonNode getTrace() {
		var trace = Val.JSON.objectNode();
		trace.set("operator", Val.JSON.textNode("Policy Decision Point"));
		trace.set("subscription", MAPPER.valueToTree(authorizationSubscription));
		var matches = Val.JSON.arrayNode();
		matchingDocuments.forEach(doc -> matches.add(Val.JSON.textNode(doc.getPolicyElement().getSaplName())));
		trace.set("matchingDocuments", matches);
		trace.set("combinedDecision", combinedDecision.getTrace());
		trace.set("creationTime", Val.JSON.textNode(timestamp.toString()));

		return trace;
	}

}
