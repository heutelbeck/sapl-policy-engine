package io.sapl.interpreter;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;

public class PDPDecision {
	AuthorizationSubscription     authzSubscription;
	AuthorizationDecision         decision;
	String                        combiningAlgorithm;
	Instant                       timestamp;
	List<PolicyDecision> matchingDocuments = new LinkedList<>();
}
