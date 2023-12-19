package io.sapl.springdatar2dbc.sapl.placingProxy;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.RepositoryInformation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
class R2dbcEnforcementPointTest {

    @Autowired
    R2dbcEnforcementPoint r2dbcEnforcementPoint;

    @MockBean
    R2dbcProxyInterceptor<?> r2DbcProxyInterceptorMock;

    @Mock
    ProxyFactory factoryMock;

    @Mock
    RepositoryInformation repositoryInformationMock;

    @Test
    void when_ProxyR2dbcHandlerIsAddedToProxyFactoryAsAdvice_then_postProcess() {
        // GIVEN

        // WHEN
        r2dbcEnforcementPoint.postProcess(factoryMock, repositoryInformationMock);

        // THEN
        verify(factoryMock, Mockito.times(1)).addAdvice(eq(r2DbcProxyInterceptorMock));

    }
}
