package io.sapl.reimpl.prp.index.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import io.sapl.reimpl.prp.PrpUpdateEvent.Type;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CanonicalImmutableParsedDocumentIndexTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private Map<String, Boolean> bindings;

    private SAPLInterpreter interpreter;

    private JsonNodeFactory json;

    private CanonicalImmutableParsedDocumentIndex emptyIndex;

    private Map<String, JsonNode> variables;

    @Before
    public void setUp() {
        bindings = new HashMap<>();
        for (String variable : getVariables()) {
            bindings.put(variable, null);
        }
        interpreter = new DefaultSAPLInterpreter();
        json = JsonNodeFactory.instance;
        emptyIndex = new CanonicalImmutableParsedDocumentIndex();
        variables = new HashMap<>();
    }


    @Test
    public void test_misc() throws Exception {
        //        List<Integer> numbers = IntStream.range(0, 500).boxed().collect(Collectors.toList());

        List<Integer> integerList = Arrays.asList(1, 2, 1, 1, 3, 4, 2, 4, 1, 4, 3, 2, 4);
        List<Integer> integersProcessedList = new ArrayList<>();

        Flux<Integer> resultFlux = Flux.fromIterable(integerList)
                .log()
                .filter(integer -> !integersProcessedList.contains(integer))
                .map(integer -> {
                    integersProcessedList.add(integer);
                    System.out.println("added " + integer + " to list of processed integers");
                    return integer;
                });

        List<Integer> resultIntegerList = resultFlux.collectList().block();
        System.out.println(resultIntegerList);

        Assertions.assertThat(resultIntegerList).hasSize(4);
        Assertions.assertThat(resultIntegerList).containsAll(Arrays.asList(1, 2, 3, 4));

    }


    @Test
    public void test_orphaned() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        List<Update> updates = new ArrayList<>(3);

        String def1 = "policy \"p_0\" permit !resource.x1";
        SAPL doc1 = interpreter.parse(def1);
        updates.add(new Update(Type.PUBLISH, doc1, def1));


        String def2 = "policy \"p_1\" permit !(resource.x0 | resource.x1)";
        SAPL doc2 = interpreter.parse(def2);
        updates.add(new Update(Type.PUBLISH, doc2, def2));

        String def3 = "policy \"p_2\" permit (resource.x1 | resource.x2)";
        SAPL doc3 = interpreter.parse(def3);
        updates.add(new Update(Type.PUBLISH, doc3, def3));

        PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
        ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", false);
        bindings.put("x1", false);
        bindings.put("x2", true);

        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies(authzSubscription, functionCtx, variables).block();

        // then
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(3).contains(doc1, doc2);
    }

    @Test
    public void testPut() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        List<Update> updates = new ArrayList<>(3);

        String definition = "policy \"p_0\" permit !(resource.x0 | resource.x1)";
        SAPL document = interpreter.parse(definition);
        updates.add(new Update(Type.PUBLISH, document, definition));

        PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
        ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", false);
        bindings.put("x1", false);
        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies(authzSubscription, functionCtx, variables).block();

        // then
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(1).contains(document);
    }

    @Test
    public void testRemove() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        List<Update> updates = new ArrayList<>(3);


        String definition = "policy \"p_0\" permit resource.x0 & resource.x1";
        SAPL document = interpreter.parse(definition);
        //            prp.updateFunctionContext(functionCtx);
        updates.add(new Update(Type.PUBLISH, document, definition));

        PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
        ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", true);
        bindings.put("x1", true);

        updates.clear();
        updates.add(new Update(Type.UNPUBLISH, document, definition));

        prpUpdateEvent = new PrpUpdateEvent(updates);
        updatedIndex = updatedIndex.apply(prpUpdateEvent);

        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies(authzSubscription, functionCtx, variables).block();

        // then
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).isEmpty();
    }

    @Test
    public void testUpdateFunctionCtx() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        List<Update> updates = new ArrayList<>(3);

        String definition = "policy \"p_0\" permit !resource.x0";
        SAPL document = interpreter.parse(definition);
        updates.add(new Update(Type.PUBLISH, document, definition));

        PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
        ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", false);
        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        //        prp.updateFunctionContext(functionCtx);
        PolicyRetrievalResult result = updatedIndex.retrievePolicies(authzSubscription, functionCtx, variables).block();

        // then
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(1).contains(document);
    }

    private AuthorizationSubscription createRequestObject() {
        ObjectNode resource = json.objectNode();
        for (Map.Entry<String, Boolean> entry : bindings.entrySet()) {
            Boolean value = entry.getValue();
            if (value != null) {
                resource.put(entry.getKey(), value);
            }
        }
        return new AuthorizationSubscription(NullNode.getInstance(), NullNode.getInstance(), resource,
                NullNode.getInstance());
    }

    private static Set<String> getVariables() {
        HashSet<String> variables = new HashSet<>();
        for (int i = 0; i < 10; ++i) {
            variables.add("x" + i);
        }
        return variables;
    }
}
