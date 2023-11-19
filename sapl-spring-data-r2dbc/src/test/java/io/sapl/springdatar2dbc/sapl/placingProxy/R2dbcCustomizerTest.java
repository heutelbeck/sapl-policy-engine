package io.sapl.springdatar2dbc.sapl.placingProxy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class R2dbcCustomizerTest {

    @Autowired
    R2dbcCustomizer r2dbcCustomizer;

    @MockBean
    RepositoryFactorySupport repositoryFactorySupportMock;

    @MockBean
    R2dbcEnforcementPoint r2dbcEnforcementPointMock;

    @Test
    void when_usingMongoEnforcementPointIsDesired_then_customizeRepositoryFactorySupport() {
        // GIVEN

        // WHEN
        r2dbcCustomizer.customize(repositoryFactorySupportMock);

        // THEN
        verify(repositoryFactorySupportMock, times(1)).addRepositoryProxyPostProcessor(r2dbcEnforcementPointMock);
    }
}
