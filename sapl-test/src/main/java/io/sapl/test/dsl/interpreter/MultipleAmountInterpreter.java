package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;

class MultipleAmountInterpreter {
    int getAmountFromMultipleAmountString(final String multipleAmount) {
        try {
            var amount = multipleAmount.endsWith("x") ? multipleAmount.substring(0, multipleAmount.length() - 1) : "";

            return Math.absExact(Integer.parseInt(amount));
        } catch (Exception e) {
            throw new SaplTestException("Given amount has invalid format");
        }
    }
}
