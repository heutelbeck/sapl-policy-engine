package io.sapl.spring.annotation;

import java.lang.annotation.Annotation;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.spring.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Configuration
@RequiredArgsConstructor
public class EnforcePoliciesAspectPEP {

	private final PolicyDecisionPoint pdp;
	private final ConstraintHandlerService constraintHandlers;
	private final ObjectMapper mapper;
//	private final Optional<TokenStore> tokenStore;

	// @Around("@annotation(enforcePolicies) && execution(* *(..))")
	@Around("@annotation(enforcePolicies) && execution(* *(..))")
	public Object around(ProceedingJoinPoint pjp, EnforcePolicies enforcePolicies) throws Throwable {
		LOGGER.trace("Authorizing access to: {}.{}.", pjp.getTarget().getClass().getSimpleName(),
				pjp.getSignature().getName());

		Object subject = retrieveSubject(enforcePolicies, pjp);
		Object action = retrieveAction(enforcePolicies, pjp);

		Object result;

		if (enforcePolicies.resultResource()) {
			LOGGER.trace(
					"The policy enforcement point is deployed after the methods execution. I.e., resultResource == true. Running method now.");
			Object originalResult = pjp.proceed();

			JsonNode originalResultJson = mapper.valueToTree(originalResult);

			LOGGER.trace("The methods return value is used as the resource in the authorization request: {}",
					originalResultJson);

			Response response = pdp.decide(buildRequest(subject, action, originalResult)).block();

			if (response.getDecision() != Decision.PERMIT) {
				LOGGER.trace("Access not permitted by policy decision point. Decision was: {}", response.getDecision());
				throw new AccessDeniedException("Access not permitted by policy decision point.");
			}
			constraintHandlers.handleObligations(response);
			constraintHandlers.handleAdvices(response);

			if (response.getResource().isPresent()) {
				try {
					result = mapper.treeToValue(response.getResource().get(), originalResult.getClass());
				} catch (JsonProcessingException e) {
					LOGGER.trace("Transformed result cannot be mapped to original class. {}",
							response.getResource().get());
					throw new AccessDeniedException("Access not permitted by policy enforcement point.");
				}
			} else {
				result = originalResult;
			}

		} else {
			Object resource = retrieveResource(enforcePolicies, pjp);

			Response response = pdp.decide(buildRequest(subject, action, resource)).block();

			if (response.getDecision() != Decision.PERMIT) {
				LOGGER.trace("Access not permitted by policy decision point. Decision was: {}", response.getDecision());
				throw new AccessDeniedException("Access not permitted by policy decision point.");
			}

			constraintHandlers.handleObligations(response);
			constraintHandlers.handleAdvices(response);
			result = pjp.proceed();
		}

		return result;
	}

	private Request buildRequest(Object subject, Object action, Object resource) {
		return new Request(mapper.valueToTree(subject), mapper.valueToTree(action), mapper.valueToTree(resource), null);
	}

	private Object retrieveSubject(EnforcePolicies pdpAuthorize, ProceedingJoinPoint pjp) {
		LOGGER.trace("Constructing the subject for the authorization request...");
		if (pdpAuthorize.subject().isEmpty()) {
			JsonNode parameterSubject = retrieveSubjectFromParameters(pjp);
			if (parameterSubject != null) {
				return parameterSubject;
			}

			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			LOGGER.trace(
					"The subject will be defined by the current authentication set in the SecurityContextHolder: {}",
					authentication);
			return authentication;
		} else {
			LOGGER.trace("The subject is explicitly defined by the developer in the annotation: {}.",
					pdpAuthorize.subject());
			return pdpAuthorize.subject();
		}
	}

	// Marshal the authentication object as the value of the key "authentication"
//
//
//
//			subject = MAPPER.createObjectNode();
//			subject.set("authorization", );
//
//			Optional<String> token = retrieveJwtTokenFrom(authentication);
//
//			// If the project is set up with JWT, add the key "tokenValue"
//
//			if (tokenStore.isPresent() && token.isPresent()) {
//			LOGGER.trace("Constructing the subject from the JWT token: {}", token.get());
//			OAuth2AccessToken parsedToken = tokenStore.get().readAccessToken(token.get());
//			Map<String, Object> claims = parsedToken.getAdditionalInformation();
//			for (Entry<String, Object> kvp : claims.entrySet()) {
//				LOGGER.trace("Adding claim from JWT token to subject informarion: {} -> {}", kvp.getKey(),
//						kvp.getValue());
//			}
//				subject.set("tokenValue", MAPPER.valueToTree(token.get()));
//			}
//
//			try {
//				LOGGER.trace("The calculated subject is:\n{}", MAPPER.writeValueAsString(subject));
//			} catch (JsonProcessingException e) {
//				LOGGER.error("Cannot print subject: {}", e.getMessage());
//			}
//		}
//		return subject;
//	}
//
//	private static Optional<String> retrieveJwtTokenFrom(Authentication authentication) {
//		final JsonNode details = new ObjectMapper().convertValue(authentication.getDetails(), JsonNode.class);
//		final String token = details.findValue("tokenValue") != null ? details.findValue("tokenValue").textValue()
//				: null;
//		return Optional.ofNullable(token);
//	}

	private Object retrieveAction(EnforcePolicies pdpAuthorize, ProceedingJoinPoint pjp) {
		Object action;
		if (pdpAuthorize.action().isEmpty()) {
			ObjectNode actionNode = mapper.createObjectNode();
			Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();
			if (httpServletRequest.isPresent()) {
				try {
					LOGGER.trace("The action is in the context of a HTTP request: {}",
							mapper.writerWithDefaultPrettyPrinter()
									.writeValueAsString(mapper.valueToTree(httpServletRequest.get())));
				} catch (JsonProcessingException | IllegalArgumentException e) {
					LOGGER.trace("could not print JSON object");
				}
				actionNode.set("http", mapper.valueToTree(httpServletRequest.get()));
			}
			JsonNode signature = mapper.valueToTree(pjp.getSignature());
			LOGGER.trace("The action is enforced in java around: {}", signature);
			actionNode.set("java", signature);
			action = actionNode;
		} else {
			LOGGER.trace("The action is explicitly defined by the developer in the annotation: {}.",
					pdpAuthorize.action());
			action = pdpAuthorize.action();
		}
		return action;
	}

	private Object retrieveResource(EnforcePolicies pdpAuthorize, ProceedingJoinPoint pjp) {
		Object resource;
		if (pdpAuthorize.resource().isEmpty()) {
			JsonNode parameterResource = retrieveResourceFromParameters(pjp);
			if (parameterResource != null) {
				resource = parameterResource;
			} else {
				LOGGER.trace(
						"No specific resouce information found. Construct basic information from the runtime context");
				ObjectNode resourceNode = mapper.createObjectNode();
				Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();
				if (httpServletRequest.isPresent()) {
					LOGGER.trace("The action is in the context of a HTTP request: {}",
							mapper.valueToTree(httpServletRequest.get()));
					resourceNode.set("http", mapper.valueToTree(httpServletRequest.get()));
				}
				JsonNode target = serializeTargetClassDescription(pjp.getTarget());
				LOGGER.trace("The target of access is: {}", target);
				resourceNode.set("targetClass", target);

				resource = resourceNode;
			}
		} else {
			LOGGER.trace("The resource is explicitly defined by the developer in the annotation: {}.",
					pdpAuthorize.resource());
			resource = pdpAuthorize.resource();
		}
		return resource;
	}

	private JsonNode retrieveResourceFromParameters(ProceedingJoinPoint thisJoinPoint) {
		LOGGER.trace("inspecting signature...");
		Object[] methodArgs = thisJoinPoint.getArgs();
		int numArgs = methodArgs.length;
		MethodSignature methodSignature = (MethodSignature) thisJoinPoint.getSignature();
		Annotation[][] annotationMatrix = methodSignature.getMethod().getParameterAnnotations();
		for (int i = 0; i < numArgs; i++) {
			Annotation[] annotations = annotationMatrix[i];
			for (Annotation annotation : annotations) {
				if (Resource.class.isAssignableFrom(annotation.annotationType())) {
					JsonNode result = mapper.valueToTree(methodArgs[i]);
					LOGGER.trace("@Resource argument detected. Use it as the resource: {}", result);
					return result;
				}
			}
		}
		return null;
	}

	private JsonNode retrieveSubjectFromParameters(ProceedingJoinPoint thisJoinPoint) {
		LOGGER.trace("inspecting signature...");
		Object[] methodArgs = thisJoinPoint.getArgs();
		int numArgs = methodArgs.length;
		MethodSignature methodSignature = (MethodSignature) thisJoinPoint.getSignature();
		Annotation[][] annotationMatrix = methodSignature.getMethod().getParameterAnnotations();
		for (int i = 0; i < numArgs; i++) {
			Annotation[] annotations = annotationMatrix[i];
			for (Annotation annotation : annotations) {
				if (Subject.class.isAssignableFrom(annotation.annotationType())) {
					JsonNode result = mapper.valueToTree(methodArgs[i]);
					LOGGER.trace("@Subject argument detected. Use it as the resource: {}", result);
					return result;
				}
			}
		}
		return null;
	}

	private static JsonNode serializeTargetClassDescription(Object obj) {
		JsonNodeFactory factory = JsonNodeFactory.instance;
		ObjectNode result = factory.objectNode();
		result.set("name", factory.textNode(obj.getClass().getName()));
		result.set("canonicaName", factory.textNode(obj.getClass().getCanonicalName()));
		result.set("typeName", factory.textNode(obj.getClass().getTypeName()));
		result.set("simpleName", factory.textNode(obj.getClass().getSimpleName()));
		return result;
	}

	private static Optional<HttpServletRequest> retrieveRequestObject() {
		final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
		final HttpServletRequest httpRequest = requestAttributes != null
				? ((ServletRequestAttributes) requestAttributes).getRequest()
				: null;
		return Optional.ofNullable(httpRequest);
	}

}
