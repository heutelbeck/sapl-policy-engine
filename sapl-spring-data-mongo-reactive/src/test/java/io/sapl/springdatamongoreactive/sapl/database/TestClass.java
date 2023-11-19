package io.sapl.springdatamongoreactive.sapl.database;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TestClass {

    public Object setResource(String field, String value) {
        return "Static class set: " + field + ", " + value;
    }
}
