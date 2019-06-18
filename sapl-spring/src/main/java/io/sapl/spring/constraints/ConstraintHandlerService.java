package io.sapl.spring.constraints;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.Response;
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

	private static final String FAILED_TO_HANDLE_OBLIGATION = "Failed to handle obligation: %s";
	private final List<ConstraintHandler> constraintHandlers;

	/**
	 * Checks if for all obligations in a response at least one obligation handler is
	 * registered.
	 * @param response a PDP Response
	 * @return true, if for all obligations in a response at least one obligation handler
	 * is registered.
	 */
	public boolean handlersForObligationsAvailable(Response response) {
		if (response.getObligations().isPresent()) {
			for (JsonNode obligation : response.getObligations().get()) {
				if (!atLeastOneHandlerForConstraintIsPresent(obligation)) {
					LOGGER.warn("No obligation handler found for: {}", obligation);
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Attempts to handle all obligations of the response and throws AccessDeniedException
	 * on failure.
	 * @param response a PDP response
	 * @throws AccessDeniedException if obligation handling fails.
	 */
	public void handleObligations(Response response) {
		if (!handlersForObligationsAvailable(response)) {
			throw new AccessDeniedException("Obligation handlers missing.");
		}
		if (response.getObligations().isPresent()) {
			for (JsonNode obligation : response.getObligations().get()) {
				if (!handleConstraint(obligation)) {
					String message = String.format(FAILED_TO_HANDLE_OBLIGATION, obligation);
					LOGGER.warn(message);
					throw new AccessDeniedException(message);
				}
			}
		}
	}

	/**
	 * Makes a best effort to handle all advices of the response based on registered
	 * constraint handlers.
	 * @param response a PDP response
	 */
	public void handleAdvices(Response response) {
		if (response.getAdvices().isPresent()) {
			for (JsonNode advice : response.getAdvices().get()) {
				if (!handleConstraint(advice)) {
					LOGGER.warn("Failed to handle advice: {}", advice);
				}
			}
		}
	}

	private boolean atLeastOneHandlerForConstraintIsPresent(JsonNode constraint) {
		for (ConstraintHandler handler : constraintHandlers) {
			if (handler.canHandle(constraint)) {
				return true;
			}
		}
		return false;
	}

	private boolean handleConstraint(JsonNode constraint) {
		boolean success = false;
		for (ConstraintHandler handler : constraintHandlers) {
			if (handler.canHandle(constraint)) {
				// one success is sufficient for overall success, but all are attempted
				success = success || handler.handle(constraint);
			}
		}
		return success;
	}

}
