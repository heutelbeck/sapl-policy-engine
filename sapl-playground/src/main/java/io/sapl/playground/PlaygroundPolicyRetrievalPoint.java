package io.sapl.playground;

import java.util.*;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.Getter;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects
 * are assumed to be immutable.
 */
@ToString
public class PlaygroundPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private final Map<String, Document> documentsByName = new HashMap<>();
    private final Set<String>           names         = new HashSet<>();
    @Getter
    private boolean                     consistent    = true;
    SAPLInterpreter                     parser;

    public PlaygroundPolicyRetrievalPoint(List<String> documents, SAPLInterpreter parser) {
        this.parser = parser;
        documents.forEach(this::load);
    }

    private void load(String source) {
        final var document = parser.parseDocument(source);
        if (document.isInvalid()) {
            this.consistent = false;
        }
        if (!names.add(document.name())) {
            this.consistent = false;
        }
        documentsByName.put(document.name(), document);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        if (!consistent)
            return Mono.just(PolicyRetrievalResult.invalidPrpResult());

        final var documentMatches = Flux
                .merge(documentsByName.values().stream()
                        .map(document -> document.sapl().matches()
                                .map(targetExpressionResult -> new DocumentMatch(document, targetExpressionResult)))
                        .toList());

        return documentMatches.reduce(new PolicyRetrievalResult(), PolicyRetrievalResult::withMatch);
    }

    @Override
    public Collection<Document> allDocuments() {
        return documentsByName.values();
    }

}
