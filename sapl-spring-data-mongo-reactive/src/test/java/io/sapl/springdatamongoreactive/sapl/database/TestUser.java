package io.sapl.springdatamongoreactive.sapl.database;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("EI_EXPOSE_REP2")
public class TestUser {
    ObjectId id;
    String   firstname;
    int      age;
}
