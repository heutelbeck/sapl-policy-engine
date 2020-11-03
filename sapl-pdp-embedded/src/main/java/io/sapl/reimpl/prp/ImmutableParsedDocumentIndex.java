package io.sapl.reimpl.prp;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.reimpl.prp.PrpUpdateEvent.Type;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ImmutableParsedDocumentIndex {

    Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
                                                 FunctionContext functionCtx, Map<String, JsonNode> variables);

    ImmutableParsedDocumentIndex apply(PrpUpdateEvent event);

    default void applyEvent(Map<String, SAPL> newDocuments, PrpUpdateEvent event) {
        for (var update : event.getUpdates()) {
            applyUpdate(newDocuments, update);
        }
    }

    private void applyUpdate(Map<String, SAPL> newDocuments, Update update) {
        var name = update.getDocument().getPolicyElement().getSaplName();
        if (update.getType() == Type.UNPUBLISH) {
            newDocuments.remove(name);
        } else {
            if (newDocuments.containsKey(name)) {
                throw new RuntimeException("Fatal error. Policy name collision. A document with a name ('" + name
                        + "') identical to an existing document was published to the PRP.");
            }
            newDocuments.put(name, update.getDocument());
        }
    }

}