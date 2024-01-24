package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.grammar.sapl.BinaryOperator;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Schema;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.TestPIP;
import io.sapl.testutil.EObjectUtil;

public class EnforcedSchemaTests {
    private static final JsonNodeFactory        JSON        = JsonNodeFactory.instance;
    private static final ObjectMapper           MAPPER      = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    private static AnnotationAttributeContext   attributeContext;
    private static AnnotationFunctionContext    functionContext;

    @BeforeAll
    static void beforeAll() throws JsonProcessingException, InitializationException {
        attributeContext = new AnnotationAttributeContext();
        attributeContext.loadPolicyInformationPoint(new TestPIP());
        functionContext = new AnnotationFunctionContext();
        functionContext.loadLibrary(SimpleFunctionLibrary.class);
        functionContext.loadLibrary(FilterFunctionLibrary.class);
        functionContext.loadLibrary(StandardFunctionLibrary.class);

    }

    @Test
    void when_enforcedSubjectSchemaAndValidSubject_then_matches() throws JsonProcessingException {
        var subscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document     = """
                subject     enforced schema "sub1"
                subject              schema "sub2"
                subject     enforced schema "sub3"
                action      enforced schema "action1"
                action               schema "action2"
                resource    enforced schema "resource1"
                resource             schema "resource2"
                environment enforced schema "environment1"
                environment          schema "environment2"
                
                policy "test"
                permit true
                """;
        var sapl         = INTERPRETER.parse(document);
        EObjectUtil.dump(sapl);

        var authzSubscription = MAPPER.readValue(subscription, AuthorizationSubscription.class);
        var decisions         = INTERPRETER.evaluate(authzSubscription, document, attributeContext, functionContext,
                Map.of());
        decisions.doOnNext(System.out::println).blockFirst();

        EObjectUtil.dump(schemasEnforcementExpression(sapl.getSchemas()));
    }

    private static Expression schemasEnforcementExpression(EList<Schema> schemas) {
        var expressionsByKeyword = collectEnforcedSchemaExpressionsByKeyword(schemas);
        var keywordPredicates    = new ArrayList<Expression>(4);
        for (var expressionByKeyword : expressionsByKeyword.entrySet()) {
            keywordPredicates.add(inBraces(concatenateExpressionsWithOperator(expressionByKeyword.getValue(),
                    () -> SaplFactory.eINSTANCE.createEagerOr())));
        }
        if (keywordPredicates.isEmpty()) {
            var value = SaplFactory.eINSTANCE.createBasicValue();
            value.setValue(SaplFactory.eINSTANCE.createTrueLiteral());
            return value;
        }
        return inBraces(
                concatenateExpressionsWithOperator(keywordPredicates, () -> SaplFactory.eINSTANCE.createEagerAnd()));
    }

    private static Map<String, List<Expression>> collectEnforcedSchemaExpressionsByKeyword(EList<Schema> schemas) {
        Map<String, List<Expression>> schemasByKeyword = new HashMap<>();
        for (var schema : schemas) {
            if ("enforced".equals(schema.getEnforced())) {
                var expression = schemaPredicateExpression(schema);
                schemasByKeyword.computeIfAbsent(schema.getSubscriptionElement(), k -> new ArrayList<>(1))
                        .add(expression);
            }
        }
        return schemasByKeyword;
    }

    private static Expression inBraces(Expression expression) {
        var group = SaplFactory.eINSTANCE.createBasicGroup();
        group.setExpression(expression);
        return group;
    }

    private static Expression concatenateExpressionsWithOperator(List<Expression> expressions,
            Supplier<BinaryOperator> operatorSupplier) {
        var head = expressions.get(0);
        if (expressions.size() == 1)
            return head;

        var operator = operatorSupplier.get();
        operator.setLeft(head);
        var tail = expressions.subList(1, expressions.size());
        operator.setRight(concatenateExpressionsWithOperator(tail, operatorSupplier));
        return operator;
    }

    private static Expression schemaPredicateExpression(Schema schema) {
        var function = SaplFactory.eINSTANCE.createBasicFunction();
        var fSteps   = function.getFsteps();
        fSteps.add(SchemaValidationLibrary.NAME);
        fSteps.add("isCompliantWithSchema");

        var identifier = SaplFactory.eINSTANCE.createBasicIdentifier();
        identifier.setIdentifier(schema.getSubscriptionElement());

        var arguments = SaplFactory.eINSTANCE.createArguments();
        var args      = arguments.getArgs();
        args.add(identifier);
        args.add(schema.getSchemaExpression());

        function.setArguments(arguments);
        return function;
    }

}
