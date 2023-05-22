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
package io.sapl.interpreter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PolicyDecision implements DocumentEvaluationResult {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	static {
		MAPPER.registerModule(new Jdk8Module());
	}

	final String           documentName;
	final Decision         entitlement;
	final Optional<Val>    targetResult;
	final Optional<Val>    whereResult;
	final List<Val>        obligations = new LinkedList<>();
	final List<Val>        advice      = new LinkedList<>();
	final Optional<Val>    resource;
	final Optional<String> errorMessage;

	private PolicyDecision(String documentName, Decision entitlement, Optional<Val> targetResult,
			Optional<Val> whereResult, List<Val> obligations, List<Val> advice, Optional<Val> resource,
			Optional<String> errorMessage) {
		this.documentName = documentName;
		this.targetResult = targetResult;
		this.whereResult  = whereResult;
		this.resource     = resource;
		this.entitlement  = entitlement;
		this.errorMessage = errorMessage;
		this.obligations.addAll(obligations);
		this.advice.addAll(advice);
	}

	public List<Val> getObligations() {
		return Collections.unmodifiableList(obligations);
	}

	public List<Val> getAdvice() {
		return Collections.unmodifiableList(advice);
	}

	public static PolicyDecision ofTargetExpressionEvaluation(String policy, Val targetExpressionResult,
			Decision entitlement) {
		return new PolicyDecision(policy, entitlement, Optional.ofNullable(targetExpressionResult), Optional.empty(),
				List.of(), List.of(), Optional.empty(), Optional.empty());
	}

	public static PolicyDecision ofImportError(String policy, Decision entitlement, String errorMessage) {
		return new PolicyDecision(policy, entitlement, Optional.empty(), Optional.empty(), List.of(), List.of(),
				Optional.empty(), Optional.ofNullable(errorMessage));
	}

	public static PolicyDecision fromWhereResult(String documentName, Decision entitlement, Val whereResult) {
		return new PolicyDecision(documentName, entitlement, Optional.empty(), Optional.ofNullable(whereResult),
				List.of(), List.of(), Optional.empty(), Optional.empty());
	}

	public PolicyDecision withObligation(Val obligation) {
		var policyDecision = new PolicyDecision(documentName, entitlement, targetResult, whereResult, obligations,
				advice, resource, errorMessage);
		policyDecision.obligations.add(obligation);
		return policyDecision;
	}

	public PolicyDecision withAdvice(Val advice) {
		var policyDecision = new PolicyDecision(documentName, entitlement, targetResult, whereResult, obligations,
				this.advice, resource, errorMessage);
		policyDecision.advice.add(advice);
		return policyDecision;
	}

	public PolicyDecision withResource(Val resource) {
		return new PolicyDecision(documentName, entitlement, targetResult, whereResult, obligations, advice,
				Optional.ofNullable(resource), errorMessage);
	}

	@Override
	public DocumentEvaluationResult withTargetResult(Val targetResult) {
		return new PolicyDecision(documentName, entitlement, Optional.ofNullable(targetResult), whereResult,
				obligations, advice, resource, errorMessage);
	}

	public AuthorizationDecision getAuthorizationDecision() {

		if (targetResult.isPresent() && targetResult.get().isBoolean() && !targetResult.get().getBoolean())
			return AuthorizationDecision.NOT_APPLICABLE;

		if (hasErrors() || whereResult.isEmpty())
			return AuthorizationDecision.INDETERMINATE;

		if (!whereResult.get().getBoolean())
			return AuthorizationDecision.NOT_APPLICABLE;

		var authzDecision = AuthorizationDecision.DENY;
		if (entitlement == Decision.PERMIT)
			authzDecision = AuthorizationDecision.PERMIT;

		authzDecision = authzDecision.withObligations(collectConstraints(obligations));
		authzDecision = authzDecision.withAdvice(collectConstraints(advice));
		if (resource.isPresent())
			authzDecision = authzDecision.withResource(resource.get().get());
		return authzDecision;
	}

	private boolean hasErrors() {
		return (targetResult.isPresent() && targetResult.get().isError())
				|| (getWhereResult().isPresent() && getWhereResult().get().isError())
				|| containsErrorOrUndefined(getObligations()) || containsErrorOrUndefined(getAdvice())
				|| (getResource().isPresent() && (getResource().get().isError() || getResource().get().isUndefined()));
	}

	private ArrayNode collectConstraints(List<Val> constraints) {
		var array = Val.JSON.arrayNode();
		for (var constraint : constraints) {
			array.add(constraint.get());
		}
		return array;
	}

	private boolean containsErrorOrUndefined(Collection<Val> values) {
		return values.stream().anyMatch(val -> val.isError() || val.isUndefined());
	}

	@Override
	public JsonNode getTrace() {
		var trace = Val.JSON.objectNode();
		trace.set("documentType", Val.JSON.textNode("policy"));
		trace.set("policyName", Val.JSON.textNode(documentName));
		trace.set("authorizationDecision", MAPPER.valueToTree(getAuthorizationDecision()));
		if (entitlement != null)
			trace.set("entitlement", Val.JSON.textNode(entitlement.toString()));
		errorMessage.ifPresent(error -> trace.set("error", Val.JSON.textNode(errorMessage.get())));
		targetResult.ifPresent(target -> trace.set("target", target.getTrace()));
		whereResult.ifPresent(where -> trace.set("where", where.getTrace()));
		if (!obligations.isEmpty())
			trace.set("obligations", listOfValToTraceArray(obligations));
		if (!obligations.isEmpty())
			trace.set("advice", listOfValToTraceArray(advice));
		resource.ifPresent(r -> trace.set("resource", r.getTrace()));
		return trace;
	}

	private JsonNode listOfValToTraceArray(List<Val> values) {
		var arrayNode = Val.JSON.arrayNode();
		values.forEach(val -> arrayNode.add(val.getTrace()));
		return arrayNode;
	}

}
