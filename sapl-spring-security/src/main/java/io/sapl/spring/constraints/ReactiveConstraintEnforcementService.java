package io.sapl.spring.constraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ReactiveConstraintEnforcementService {

    private final List<AbstractConstraintHandler> handlerServices;

    public ReactiveConstraintEnforcementService(List<AbstractConstraintHandler> handlerServices) {
        this.handlerServices = new ArrayList<AbstractConstraintHandler>(handlerServices);
        Collections.sort(this.handlerServices);
    }

    public Flux<Object> enforceConstraintsOnResourceAccessPoint(AuthorizationDecision decision,
                                                                Flux<Object> resourceAccessPoint) {
        var wrapped = resourceAccessPoint;
        if (decision.getObligations().isPresent()) {
            for (var constraint : decision.getObligations().get()) {
                var handlers = getHandlersResponsibleForConstraint(constraint);
                if (handlers.isEmpty())
                    return Flux.error(new AccessDeniedException("No handler for obligation: " + constraint.asText()));
                for (var handler : handlers) {
                    wrapped = handler.applyObligation(wrapped, constraint);
                }
            }
        }
        if (decision.getAdvices().isPresent()) {
            for (var constraint : decision.getAdvices().get()) {
                var handlers = getHandlersResponsibleForConstraint(constraint);
                if (handlers.isEmpty())
                    log.info("No handler for advice: {}", constraint);
                for (var handler : handlers) {
                    wrapped = handler.applyAdvice(wrapped, constraint);
                }
            }
        }
        return wrapped;
    }

    private List<AbstractConstraintHandler> getHandlersResponsibleForConstraint(JsonNode constraint) {
        return handlerServices.stream()
                .filter(handlerService -> handlerService.isResponsible(constraint))
                .collect(Collectors.toList());
    }

    public boolean handleForBlockingMethodInvocationOrAccessDenied(AuthorizationDecision decision) {
        if (decision.getObligations().isPresent()) {
            for (var constraint : decision.getObligations().get()) {
                var handlers = getHandlersResponsibleForConstraint(constraint);
                if (handlers.isEmpty()) {
                    log.warn("No handler for obligation: {}", constraint.asText());
                    return false;
                }
                for (var handler : handlers) {
                    if (!handler.preBlockingMethodInvocationOrOnAccessDenied(constraint))
                        return false;
                }
            }
        }
        if (decision.getAdvices().isPresent()) {
            for (var constraint : decision.getAdvices().get()) {
                var handlers = getHandlersResponsibleForConstraint(constraint);
                if (handlers.isEmpty())
                    log.warn("No handler for advice: {}", constraint.asText());
                for (var handler : handlers)
                    handler.preBlockingMethodInvocationOrOnAccessDenied(constraint);
            }
        }
        return true;
    }

    public Object handleAfterBlockingMethodInvocation(AuthorizationDecision decision, Object returnedObject,
                                                      Class<?> returnType) {
        var mappedReturnObject = returnedObject;

        if (decision.getObligations().isPresent()) {
            for (var constraint : decision.getObligations().get()) {
                var handlers = getHandlersResponsibleForConstraint(constraint);

                if (handlers.isEmpty())
                    throw new AccessDeniedException("No handler for obligation: " + constraint.asText());

                for (var handler : handlers) {
                    if (returnType == null)
                        throw new AccessDeniedException("Generics type not specified in annotation.");
                    var handlerFunction = handler.postBlockingMethodInvocation(constraint);
                    if (handlerFunction == null)
                        throw new AccessDeniedException(
                                "Obligation handler indicated that it can handle the obligation, but does not implement postBlockingMethodInvocation.");
                    mappedReturnObject = handlerFunction.apply(returnType.cast(returnedObject));
                }
            }
        }

        if (decision.getAdvices().isPresent()) {
            for (var constraint : decision.getAdvices().get()) {
                var handlers = getHandlersResponsibleForConstraint(constraint);
                if (handlers.isEmpty())
                    log.warn("No handler for advice: {}", constraint.asText());
                for (var handler : handlers) {
                    if (returnType == null)
                        throw new AccessDeniedException("Generics type not specified in annotation.");
                    var handlerFunction = handler.postBlockingMethodInvocation(constraint);
                    if (handlerFunction == null)
                        log.warn("No handler implemented for advice when responsibilioty was indicated: {}",
                                constraint.asText());
                    else
                        mappedReturnObject = handlerFunction.apply(returnType.cast(mappedReturnObject));
                }
            }
        }

        return mappedReturnObject;
    }

}
