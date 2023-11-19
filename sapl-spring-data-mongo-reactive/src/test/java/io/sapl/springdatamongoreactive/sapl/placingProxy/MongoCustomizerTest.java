package io.sapl.springdatamongoreactive.sapl.placingProxy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class MongoCustomizerTest {

    @Autowired
    MongoCustomizer mongoCustomizer;

    @MockBean
    RepositoryFactorySupport repositoryFactorySupportMock;

    @MockBean
    MongoEnforcementPoint mongoEnforcementPointMock;

    @Test
    void when_usingMongoEnforcementPointIsDesired_then_customizeRepositoryFactorySupport() {
        // GIVEN

        // WHEN
        mongoCustomizer.customize(repositoryFactorySupportMock);

        // THEN
        verify(repositoryFactorySupportMock, times(1)).addRepositoryProxyPostProcessor(mongoEnforcementPointMock);
    }

}
