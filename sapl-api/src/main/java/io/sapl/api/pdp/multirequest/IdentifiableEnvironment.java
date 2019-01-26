package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableEnvironment implements Identifiable {

    private String id;
    private Object environment;

    public IdentifiableEnvironment(String id, Object environment) {
        requireNonNull(id, "id must not be null");
        requireNonNull(environment, "environment must not be null");

        this.id = id;
        this.environment = environment;
    }

}
