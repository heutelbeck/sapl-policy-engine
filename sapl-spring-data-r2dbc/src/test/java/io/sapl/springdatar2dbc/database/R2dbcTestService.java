package io.sapl.springdatar2dbc.database;

import org.springframework.stereotype.Service;

@Service
public class R2dbcTestService {

    public int setEnvironment(int numb1, int numb2) {
        return (numb1 + numb2) + numb1 * numb2;
    }
}
