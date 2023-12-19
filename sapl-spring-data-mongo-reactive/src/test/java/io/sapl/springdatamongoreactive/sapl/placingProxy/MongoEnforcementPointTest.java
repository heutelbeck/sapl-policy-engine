package io.sapl.springdatamongoreactive.sapl.placingProxy;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.RepositoryInformation;

import static org.mockito.Mockito.*;

@SpringBootTest
class MongoEnforcementPointTest {

    @Autowired
    MongoEnforcementPoint mongoEnforcementPoint;

    @MockBean
    MongoProxyInterceptor<?> mongoProxyInterceptorMock;

    @Test
    void when_thereIsProxyFactory_then_postProcessAndAddMongoHandlerAsAdvice() {
        // GIVEN
        ProxyFactory proxyFactoryMock = mock(ProxyFactory.class);

        // WHEN
        mongoEnforcementPoint.postProcess(proxyFactoryMock, any(RepositoryInformation.class));

        // THEN
        verify(proxyFactoryMock, times(1)).addAdvice(mongoProxyInterceptorMock);
    }
}
