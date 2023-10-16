package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.Array;
import io.sapl.test.grammar.sAPLTest.FalseLiteral;
import io.sapl.test.grammar.sAPLTest.NullLiteral;
import io.sapl.test.grammar.sAPLTest.NumberLiteral;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.Pair;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.TrueLiteral;
import io.sapl.test.grammar.sAPLTest.UndefinedLiteral;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ValInterpreter {

    private final ObjectMapper objectMapper;

    public Val getValFromValue(io.sapl.test.grammar.sAPLTest.Value value) {
        if (value instanceof NumberLiteral intVal) {
            return Val.of(intVal.getNumber());
        } else if (value instanceof StringLiteral stringVal) {
            return Val.of(stringVal.getString());
        } else if (value instanceof FalseLiteral) {
            return Val.of(false);
        } else if (value instanceof TrueLiteral) {
            return Val.of(true);
        } else if (value instanceof NullLiteral) {
            return Val.NULL;
        } else if (value instanceof UndefinedLiteral) {
            return Val.UNDEFINED;
        } else if (value instanceof Array array) {
            return interpretArray(array);
        } else if (value instanceof Object object) {
            return interpretObject(object);
        }
        return null;
    }

    private Val interpretArray(final Array array) {
        final var items = array.getItems();

        if (items == null || items.isEmpty()) {
            return Val.ofEmptyArray();
        }

        final var mappedItems = array.getItems().stream().map(item -> getValFromValue(item).get()).toList();

        final var arrayNode = objectMapper.createArrayNode();
        arrayNode.addAll(mappedItems);

        return Val.of(arrayNode);
    }

    private Val interpretObject(final Object object) {
        final var objectProperties = destructureObject(object);

        if (objectProperties.isEmpty()) {
            return Val.ofEmptyObject();
        }

        final var objectNode = objectMapper.createObjectNode();
        objectNode.setAll(objectProperties);
        return Val.of(objectNode);
    }

    Map<String, JsonNode> destructureObject(final Object object) {
        if (object == null || object.getMembers() == null) {
            return Collections.emptyMap();
        }
        return object
                .getMembers()
                .stream()
                .collect(Collectors.toMap(Pair::getKey, pair -> getValFromValue(pair.getValue()).get(), (oldVal, newVal) -> newVal));
    }
}
