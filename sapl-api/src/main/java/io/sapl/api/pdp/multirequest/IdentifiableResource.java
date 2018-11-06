package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableResource implements Identifiable {

    private String id;
    private Object resource;

    public IdentifiableResource(String id, Object resource) {
        requireNonNull(id, "id must not be null");
        requireNonNull(resource, "resource must not be null");

        this.id = id;
        this.resource = resource;
    }

}
