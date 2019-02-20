package io.sapl.spring;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.Response;
import io.sapl.api.pep.ConstraintHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling global constraint handlers of the application
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstraintHandlerService {

	private final List<ConstraintHandler> constraintHandlers;

	public boolean obligationHandlersForObligationsAvailable(Response response) {
		if (response.getObligation().isPresent()) {
			for (JsonNode obligation : response.getObligation().get()) {
				if (!atLeastOneHandlerForConstraintIsPresent(obligation)) {
					LOGGER.warn("No obligation handler found for: {}", obligation);
					return false;
				}
			}
		}
		return true;
	}

	public void handleObligations(Response response) {
		if (response.getObligation().isPresent()) {
			for (JsonNode obligation : response.getObligation().get()) {
				if (!handleConstraint(obligation)) {
					LOGGER.warn(String.format("Failed to handle obligation: %s", obligation));
					throw new AccessDeniedException(String.format("Failed to handle obligation: %s", obligation));
				}
			}
		}
	}

	public void handleAdvices(Response response) {
		if (response.getAdvice().isPresent()) {
			for (JsonNode advice : response.getAdvice().get()) {
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
