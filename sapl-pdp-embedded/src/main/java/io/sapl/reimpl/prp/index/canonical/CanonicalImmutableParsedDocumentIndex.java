package io.sapl.reimpl.prp.index.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
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

    public CanonicalImmutableParsedDocumentIndex(PredicateOrderStrategy predicateOrderStrategy) {
        this(Collections.emptyMap(), predicateOrderStrategy);
    }

    public CanonicalImmutableParsedDocumentIndex() {
        this(Collections.emptyMap(), new DefaultPredicateOrderStrategy());
    }

    private CanonicalImmutableParsedDocumentIndex(Map<String, SAPL> updatedDocuments,
                                                  PredicateOrderStrategy predicateOrderStrategy) {

        this.documents = updatedDocuments;
        this.predicateOrderStrategy = predicateOrderStrategy;
        Map<String, DisjunctiveFormula> targets = this.documents.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> retainTarget(entry.getValue())));

        this.indexDataContainer = new CanonicalIndexDataCreationStrategy(predicateOrderStrategy)
                .constructNew(documents, targets);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
                                                        FunctionContext functionCtx, Map<String, JsonNode> variables) {
        try {
            VariableContext variableCtx = new VariableContext(authzSubscription, variables);
            return CanonicalIndexAlgorithm.match(functionCtx, variableCtx, indexDataContainer);
        } catch (PolicyEvaluationException e) {
            log.error("error while retrieving policies", e);
            return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
        }
    }


    @Override
    public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
        var newDocuments = new HashMap<>(documents);
        applyEvent(newDocuments, event);
        log.debug("returning index with updated documents. before: {}, after: {}", documents.size(), newDocuments
                .size());
        return new CanonicalImmutableParsedDocumentIndex(newDocuments, predicateOrderStrategy);
    }


    private DisjunctiveFormula retainTarget(SAPL sapl) {
        try {
            Expression targetExpression = sapl.getPolicyElement().getTargetExpression();
            DisjunctiveFormula targetFormula;
            if (targetExpression == null) {
                targetFormula = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
            } else {
                Map<String, String> imports = sapl.fetchFunctionImports(new AnnotationFunctionContext());
                targetFormula = TreeWalker.walk(targetExpression, imports);
            }

            return targetFormula;
        } catch (PolicyEvaluationException e) {
            log.error("exception while retaining target for document {}", sapl.getPolicyElement().getSaplName(), e);
            return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
        }
    }
}
