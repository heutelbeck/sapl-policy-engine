package io.sapl.spring;

import java.io.Serializable;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SAPLPermissionEvaluator implements PermissionEvaluator {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private final PolicyDecisionPoint pdp;
	private final ConstraintHandlerService constraintHandlers;
	private final ObjectMapper mapper;

	@Override
	public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
		Response response = pdp.decide(new Request(mapper.valueToTree(authentication), mapper.valueToTree(permission),
				mapper.valueToTree(targetDomainObject), null)).blockFirst();
		if (response.getDecision() != Decision.PERMIT) {
			LOGGER.trace("Access not permitted by policy decision point. Decision was: {}", response.getDecision());
			return false;
		}
		try {
			constraintHandlers.handleObligations(response);
		} catch (AccessDeniedException e) {
			LOGGER.warn("Obligations cannot be fulfilled: {}", e.getMessage());
			return false;
		}
		constraintHandlers.handleAdvices(response);
		return true;
	}

	@Override
	public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
			Object permissionText) {

		ObjectNode target = JSON.objectNode();
		target.set("id", mapper.valueToTree(targetId));
		target.set("type", JSON.textNode(targetType));

		Response response = pdp.decide(
				new Request(mapper.valueToTree(authentication), mapper.valueToTree(permissionText), target, null))
				.blockFirst();
		if (response.getDecision() != Decision.PERMIT) {
			LOGGER.trace("Access not permitted by policy decision point. Decision was: {}", response.getDecision());
			return false;
		}
		try {
			constraintHandlers.handleObligations(response);
		} catch (AccessDeniedException e) {
			LOGGER.warn("Obligations cannot be fulfilled: {}", e.getMessage());
			return false;
		}
		constraintHandlers.handleAdvices(response);
		return true;
	}

}
