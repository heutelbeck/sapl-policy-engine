package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableAction implements Identifiable {

    private String id;
    private Object action;

    public IdentifiableAction(String id, Object action) {
        requireNonNull(id, "id must not be null");
        requireNonNull(action, "action must not be null");

        this.id = id;
        this.action = action;
    }

}
