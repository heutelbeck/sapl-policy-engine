package io.sapl.interpreter.combinators;

import java.util.List;

import io.sapl.interpreter.CombinedDecision;
import io.sapl.prp.MatchingDocument;
import reactor.core.publisher.Flux;

@FunctionalInterface
public interface DocumentsCombiningAlgorithm {
    Flux<CombinedDecision> combinePreMatchedDocuments(List<MatchingDocument> documents);
}
