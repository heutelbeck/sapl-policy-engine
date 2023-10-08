package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valNull;
import static io.sapl.hamcrest.Matchers.valUndefined;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.*;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class ValInterpreter {

    private final ObjectMapper objectMapper;

    public Val getValFromReturnValue(io.sapl.test.grammar.sAPLTest.Value value) {
        if (value instanceof NumberLiteral intVal) {
            return Val.of(intVal.getNumber());
        } else if (value instanceof StringLiteral stringVal) {
            return Val.of(stringVal.getString());
        } else if (value instanceof TrueLiteral) {
            return Val.of(true);
        } else if (value instanceof FalseLiteral) {
            return Val.of(false);
        } else if (value instanceof Object object) {
            final var objectNode = objectMapper.createObjectNode();
            final var objectProperties = destructureObject(object);
            objectNode.setAll(objectProperties);
            return Val.of(objectNode);
        }
        return null;
    }

    Map<String, JsonNode> destructureObject(final Object object) {
        if (object == null || object.getMembers() == null) {
            return Collections.emptyMap();
        }
        return object
                .getMembers()
                .stream()
                .collect(Collectors.toMap(Pair::getKey, pair -> getValFromReturnValue(pair.getValue()).get(), (oldVal, newVal) -> newVal));
    }

    Matcher<Val> getValMatcherFromVal(ValMatcher value) {
        if (value instanceof ValWithValue valWithValue) {
            final var valueObject = valWithValue.getValue();
            if (valueObject instanceof StringLiteral stringLiteral) {
                return val(stringLiteral.getString());
            } else if (valueObject instanceof NumberLiteral numberLiteral) {
                return val(numberLiteral.getNumber());
            } else if (valueObject instanceof TrueLiteral) {
                return val(true);
            } else if (valueObject instanceof FalseLiteral) {
                return val(false);
            } else if (valueObject instanceof NullLiteral) {
                return valNull();
            } else if (valueObject instanceof UndefinedLiteral) {
                return valUndefined();
            }
        }
        return null;
    }
}
