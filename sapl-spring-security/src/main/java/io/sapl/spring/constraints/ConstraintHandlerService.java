/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.constraints;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pep.ConstraintHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling advices and obligations of policy decisions for a policy
 * enforcement point.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstraintHandlerService {

	static final String FAILED_TO_HANDLE_OBLIGATION = "Failed to handle obligation.";
	static final String OBLIGATION_HANDLER_MISSING = "Obligation handler missing.";

	private static final String NO_OBLIGATION_HANDLER_FOUND_FOR = "No obligation handler found for: {}";

	private final List<ConstraintHandler> constraintHandlers;

	/**
	 * Attempts to handle all obligations of the authorization decision and throws
	 * AccessDeniedException on failure. If multiple handlers for an obligation are
	 * present, all must succeed.
	 * 
	 * @param authzDecision a PDP authorization decision
	 * @throws AccessDeniedException if obligation handling fails.
	 */
	public void handleObligations(AuthorizationDecision authzDecision) {
		if (authzDecision.getObligations().isEmpty())
			return;

		if (!handlersForAllObligationsAreAvailable(authzDecision.getObligations().get()))
			throw new AccessDeniedException(OBLIGATION_HANDLER_MISSING);

		if (!handleConstraints(authzDecision.getObligations().get()))
			throw new AccessDeniedException(FAILED_TO_HANDLE_OBLIGATION);
	}

	/**
	 * Makes a best effort to handle all advices of the authorization decision based
	 * on registered constraint handlers.
	 * 
	 * @param authzDecision a PDP authorization decision
	 */
	public void handleAdvices(AuthorizationDecision authzDecision) {
		if (authzDecision.getAdvices().isEmpty())
			return;

		handleConstraints(authzDecision.getAdvices().get());
	}

	/**
	 * Attempts to handle all constraints in the supplied array.
	 * 
	 * @param constraints an array of constraint JSON objects
	 * @return true, iff all handlers that could handle
	 */
	private boolean handleConstraints(Iterable<JsonNode> constraints) {
		var success = true;
		for (JsonNode constraint : constraints)
			success &= handleConstraint(constraint);

		return success;
	}

	/**
	 * Checks if for all obligations in a authorization decision at least one
	 * obligation handler is registered.
	 * 
	 * @param obligations an array of obligations
	 * @return true, if for all obligations in a authorization decision at least one
	 *         obligation handler is registered.
	 */
	private boolean handlersForAllObligationsAreAvailable(Iterable<JsonNode> obligations) {
		for (JsonNode obligation : obligations)
			if (!atLeastOneHandlerForObligationIsPresent(obligation))
				return false;

		return true;
	}

	/**
	 * @param constraint an obligation
	 * @return true, iff at least one constraint handler is present for handling the
	 *         obligation
	 */
	private boolean atLeastOneHandlerForObligationIsPresent(JsonNode constraint) {
		for (ConstraintHandler handler : constraintHandlers)
			if (handler.canHandle(constraint))
				return true;

		log.warn(NO_OBLIGATION_HANDLER_FOUND_FOR, constraint);
		return false;
	}

	/**
	 * This method iterates over all constraint handlers. All constraint handlers
	 * capable of handling a constraint care called. Thus, a constraint may trigger
	 * multiple handlers. As there is no way of prioritizing constraint handlers at
	 * the moment this is the only meaningful option if multiple handlers for a
	 * constraint are present.
	 * 
	 * A constraint is considered to be handled successfully, if all
	 * ConstraintHandlers capable of handling the constraint succeed.
	 * 
	 * @param constraint a constraint
	 * @return true, iff at least one ConstraintHandler successfully handled the
	 *         constraint.
	 */
	private boolean handleConstraint(JsonNode constraint) {
		boolean success = true;
		for (ConstraintHandler handler : constraintHandlers)
			if (handler.canHandle(constraint))
				success &= handler.handle(constraint);

		return success;
	}

}
