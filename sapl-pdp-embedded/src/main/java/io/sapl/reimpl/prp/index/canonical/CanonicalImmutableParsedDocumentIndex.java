package io.sapl.reimpl.prp.index.canonical;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.Literal;
import io.sapl.prp.inmemory.indexed.TreeWalker;
import io.sapl.prp.inmemory.indexed.improved.ordering.DefaultPredicateOrderStrategy;
import io.sapl.prp.inmemory.indexed.improved.ordering.PredicateOrderStrategy;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
public class CanonicalImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {

    private final CanonicalIndexDataContainer indexDataContainer;
    private final Map<String, SAPL> documents;
    private final PredicateOrderStrategy predicateOrderStrategy;
    private final EvaluationContext pdpScopedEvaluationContext;

    public CanonicalImmutableParsedDocumentIndex(PredicateOrderStrategy predicateOrderStrategy,
                                                 EvaluationContext pdpScopedEvaluationContext) {
        this(Collections.emptyMap(), predicateOrderStrategy, pdpScopedEvaluationContext);
    }

    public CanonicalImmutableParsedDocumentIndex(EvaluationContext pdpScopedEvaluationContext) {
        this(Collections.emptyMap(), new DefaultPredicateOrderStrategy(), pdpScopedEvaluationContext);
    }

    private CanonicalImmutableParsedDocumentIndex(Map<String, SAPL> updatedDocuments,
                                                  PredicateOrderStrategy predicateOrderStrategy, EvaluationContext pdpScopedEvaluationContext) {
        this.documents = updatedDocuments;
        this.predicateOrderStrategy = predicateOrderStrategy;
        this.pdpScopedEvaluationContext = pdpScopedEvaluationContext;
        Map<String, DisjunctiveFormula> targets = this.documents.entrySet().stream().collect(
                Collectors.toMap(Entry::getKey, entry -> retainTarget(entry.getValue(), pdpScopedEvaluationContext)));

        this.indexDataContainer = new CanonicalIndexDataCreationStrategy(predicateOrderStrategy).constructNew(documents,
                targets);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
        try {
            return CanonicalIndexAlgorithm.match(subscriptionScopedEvaluationContext, indexDataContainer);
        } catch (PolicyEvaluationException e) {
            log.error("error while retrieving policies", e);
            return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
        }
    }

    @Override
    public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
        var newDocuments = new HashMap<>(documents);
        applyEvent(newDocuments, event);
        log.debug("returning updated index containing {} documents", newDocuments.size());
        return new CanonicalImmutableParsedDocumentIndex(newDocuments, predicateOrderStrategy,
                pdpScopedEvaluationContext);
    }

    private DisjunctiveFormula retainTarget(SAPL sapl, EvaluationContext pdpScopedEvaluationContext) {
        try {
            var targetExpression = sapl.getPolicyElement().getTargetExpression();
            DisjunctiveFormula targetFormula;
            if (targetExpression == null) {
                targetFormula = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
            } else {
                Map<String, String> imports = sapl.documentScopedEvaluationContext(pdpScopedEvaluationContext)
                        .getImports();
                targetFormula = TreeWalker.walk(targetExpression, imports);
            }

            return targetFormula;
        } catch (PolicyEvaluationException e) {
            log.error("exception while retaining target for document {}", sapl.getPolicyElement().getSaplName(), e);
            return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
        }
    }

}
