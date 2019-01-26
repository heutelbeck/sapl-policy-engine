package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableSubject implements Identifiable {

    public static final String AUTHENTICATION_ID = "authentication";

    private String id;
    private Object subject;

    public IdentifiableSubject(String id, Object subject) {
        requireNonNull(id, "id must not be null");
        requireNonNull(subject, "subject must not be null");

        this.id = id;
        this.subject = subject;
    }

}
