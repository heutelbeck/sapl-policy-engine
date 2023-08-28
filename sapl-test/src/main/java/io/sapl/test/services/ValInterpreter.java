package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.val;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.FalseLiteral;
import io.sapl.test.grammar.sAPLTest.NumberLiteral;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.TrueLiteral;
import org.hamcrest.Matcher;

public class ValInterpreter {

    Val getValFromReturnValue(io.sapl.test.grammar.sAPLTest.Value value) {
        if (value instanceof NumberLiteral intVal) {
            return Val.of(intVal.getNumber());
        } else if (value instanceof StringLiteral stringVal) {
            return Val.of(stringVal.getString());
        } else if (value instanceof TrueLiteral) {
            return Val.of(true);
        } else if (value instanceof FalseLiteral) {
            return Val.of(false);
        }
        return null;
    }

    Matcher<Val> getValMatcherFromVal(io.sapl.test.grammar.sAPLTest.Value value) {
        if (value instanceof NumberLiteral intVal) {
            return val(intVal.getNumber());
        } else if (value instanceof StringLiteral stringVal) {
            return val(stringVal.getString());
        } else if (value instanceof TrueLiteral) {
            return val(true);
        } else if (value instanceof FalseLiteral) {
            return val(false);
        }
        return null;
    }
}
