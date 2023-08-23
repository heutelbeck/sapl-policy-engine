package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.val;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.BoolVal;
import io.sapl.test.grammar.sAPLTest.IntVal;
import io.sapl.test.grammar.sAPLTest.StringVal;
import org.hamcrest.Matcher;

public class ValInterpreter {

    Val getValFromReturnValue(io.sapl.test.grammar.sAPLTest.Val value) {
        if (value instanceof IntVal intVal) {
            return Val.of(intVal.getValue());
        } else if (value instanceof StringVal stringVal) {
            return Val.of(stringVal.getValue());
        } else if (value instanceof BoolVal boolVal) {
            return Val.of(boolVal.isIsTrue());
        }
        return null;
    }

    Matcher<Val> getValMatcherFromVal(io.sapl.test.grammar.sAPLTest.Val value) {
        if (value instanceof IntVal intVal) {
            return val(intVal.getValue());
        } else if (value instanceof StringVal stringVal) {
            return val(stringVal.getValue());
        } else if (value instanceof BoolVal boolVal) {
            return val(boolVal.isIsTrue());
        }
        return null;
    }
}
