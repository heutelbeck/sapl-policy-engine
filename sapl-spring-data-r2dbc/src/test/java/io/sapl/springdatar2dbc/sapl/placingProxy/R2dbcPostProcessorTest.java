package io.sapl.springdatar2dbc.sapl.placingProxy;

import io.sapl.springdatar2dbc.database.R2dbcPersonRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean;

@SpringBootTest
class R2dbcPostProcessorTest {

    @Autowired
    R2dbcPostProcessor r2dbcPostProcessor;

    @MockBean
    R2dbcPersonRepository r2DbcPersonRepositoryMock;

    @MockBean
    R2dbcCustomizer r2dbcCustomizerMock;

    @Mock
    R2dbcRepositoryFactoryBean<?, ?, ?> r2dbcRepositoryFactoryBeanMock;

    @Test
    void when_R2dbcRepositoryFactoryBeanExists_then_addRepositoryFactoryCustomizer() {
        // GIVEN

        // WHEN
        var result = r2dbcPostProcessor.postProcessBeforeInitialization(r2dbcRepositoryFactoryBeanMock,
                "r2dbcRepositoryFactoryBean");

        // THEN
        Assertions.assertEquals(result, r2dbcRepositoryFactoryBeanMock);
        Mockito.verify(r2dbcRepositoryFactoryBeanMock, Mockito.times(1))
                .addRepositoryFactoryCustomizer(r2dbcCustomizerMock);
    }
}
