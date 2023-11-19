package io.sapl.springdatamongoreactive.sapl.database;

import org.springframework.stereotype.Service;

@Service
public class MongoTestService {

    public int setEnvironment(int numb1, int numb2) {
        return (numb1 + numb2) + numb1 * numb2;
    }
}
