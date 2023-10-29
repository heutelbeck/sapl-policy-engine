package io.sapl.test.dsl.interpreter.matcher;

import io.sapl.test.SaplTestException;

public class MultipleAmountInterpreter {
    public int getAmountFromMultipleAmountString(final String multipleAmount) {
        try {
            return Integer.parseInt(multipleAmount.substring(0, multipleAmount.length() - 1));
        } catch (Exception e) {
            throw new SaplTestException("Given amount has invalid format");
        }
    }
}
