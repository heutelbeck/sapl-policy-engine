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
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class CanonicalImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {

    private final CanonicalIndexDataContainer indexDataContainer;
    private final Map<String, SAPL> documents;

    public CanonicalImmutableParsedDocumentIndex() {
        this(Collections.emptyMap());
    }

    private CanonicalImmutableParsedDocumentIndex(Map<String, SAPL> updatedDocuments) {
        this.documents = updatedDocuments;
        Map<String, DisjunctiveFormula> targets = this.documents.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> retainTarget(entry.getValue())));
        //TODO currently the index data container is created each time from scratch
        // - it would be better if the existing container would be amended and reused
        this.indexDataContainer = CanonicalIndexDataCreationStrategy.construct(documents, targets);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
                                                        FunctionContext functionCtx, Map<String, JsonNode> variables) {
        try {
            VariableContext variableCtx = new VariableContext(authzSubscription, variables);

            return CanonicalIndexAlgorithm.matchMono(functionCtx, variableCtx, indexDataContainer, false);
        } catch (PolicyEvaluationException e) {
            return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
        }
    }


    @Override
    public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
        // Do a shallow copy. String is immutable, and SAPL is assumed to be too.
        var newDocuments = new HashMap<>(documents);
        applyEvent(newDocuments, event);
        return new CanonicalImmutableParsedDocumentIndex(newDocuments);
    }


    private DisjunctiveFormula retainTarget(SAPL sapl) {
        try {
            Expression targetExpression = sapl.getPolicyElement().getTargetExpression();
            Objects.requireNonNull(targetExpression);

            //TODO can this simply be a new function context?
            Map<String, String> imports = sapl.fetchFunctionImports(new AnnotationFunctionContext());
            return TreeWalker.walk(targetExpression, imports);
        } catch (PolicyEvaluationException e) {
            //TODO do something
            //            unusableDocuments.put(documentKey, sapl);

            return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
        }
    }
}
