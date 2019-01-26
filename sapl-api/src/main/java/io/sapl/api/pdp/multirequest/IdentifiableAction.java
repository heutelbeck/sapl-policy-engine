package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableAction implements Identifiable {

    public static final String CREATE_ID = "create";
    public static final String READ_ID = "read";
    public static final String UPDATE_ID = "update";
    public static final String DELETE_ID = "delete";

    private String id;
    private Object action;

    public IdentifiableAction(String id, Object action) {
        requireNonNull(id, "id must not be null");
        requireNonNull(action, "action must not be null");

        this.id = id;
        this.action = action;
    }

}
