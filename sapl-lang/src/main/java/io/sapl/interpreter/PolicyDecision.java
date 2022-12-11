package io.sapl.interpreter;

import java.util.List;
import java.util.Optional;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

public class PolicyDecision {
	AuthorizationDecision decision;
	String                documentName;
	Decision              entitlement;
	Optional<Val>         explainedTargetMatch;
	Val                   where;
	List<Val>             obligations;
	List<Val>             advice;
	Optional<Val>         resource;
}
