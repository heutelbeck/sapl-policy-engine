package io.sapl.vaadin;

import com.vaadin.flow.component.UI;

import io.sapl.api.pdp.AuthorizationDecision;
import reactor.core.publisher.Mono;

public interface EnforceConstraintsOfDecision {

    Mono<AuthorizationDecision> enforceConstraintsOfDecision(
            AuthorizationDecision authorizationDecision,
            UI ui,
            VaadinPep vaadinPep
    );

}
