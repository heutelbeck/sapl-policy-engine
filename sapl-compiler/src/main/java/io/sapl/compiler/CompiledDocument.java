package io.sapl.compiler;

import io.sapl.api.pdp.Decision;
import io.sapl.api.v2.Value;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class CompiledDocument {

    private List<Value> constants = new ArrayList<>();
    private CompiledExpression targetExpression;

    public boolean matches(AuthorizationSubscription authorizationSubscription) {
        return true;
    }
    public Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authorizationSubscription) {
        return Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE, Value.undefined(), List.of(), List.of()));
    }
}