package io.sapl.springdatamongoreactive.sapl.handlers;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.MongoDbRepositoryTest;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatamongoreactive.sapl.database.TestClass;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class AuthorizationSubscriptionHandlerProviderTest {

    AuthorizationSubscriptionHandlerProvider authorizationSubscriptionHandlerProvider;

    private final AuthorizationSubscription generalProtectionMongoDbRepositoryTest   = AuthorizationSubscription
            .of("general", "general_protection_reactive_mongo_repository", "resource", "test");
    private final AuthorizationSubscription subjectAndEnvAuthSub                     = AuthorizationSubscription
            .of("method", "", "", "test");
    private final AuthorizationSubscription actionAndResourceAuthSub                 = AuthorizationSubscription.of("",
            "find_all_by_firstname_reactive_mongo_repository", "resource", "");
    private final MethodInvocation          methodInvocation                         = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), null, null);
    private final MethodInvocation          methodInvocationWithoutEnforceAnnotation = new MethodInvocationForTesting(
            "findAllByAge", new ArrayList<>(List.of(int.class)), null, null);

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    EnforceAnnotationHandler enforceAnnotationHandlerMock;

    @BeforeEach
    void beforeEach() {
        authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(beanFactoryMock,
                enforceAnnotationHandlerMock);
    }

    @Test
    void when_classIsNoRepository_then_throwException() {
        // GIVEN

        // WHEN
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> authorizationSubscriptionHandlerProvider.getAuthSub(TestClass.class, methodInvocation));

        // THEN
        verify(enforceAnnotationHandlerMock, times(0)).enforceAnnotation(any(MethodInvocation.class));
        verify(beanFactoryMock, times(0)).getBean(anyString());
    }

    @Test
    void when_annotationIsAvailableButNotCompleteAndBeanIsAvailable1_then_getAuthSub(CapturedOutput output) {
        // GIVEN
        var methodInvocationForTesting = new MethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);
        var correctAuthSub             = AuthorizationSubscription.of("method",
                "find_all_by_firstname_reactive_mongo_repository", "resource", "test");
        var annotationAuthSub          = AuthorizationSubscription.of("",
                "find_all_by_firstname_reactive_mongo_repository", "resource", "");
        var expectedLoggerMessage      = "Bean to receive specific AuthorizationSubscription Found: findAllByFirstnameMongoDbRepositoryTest";

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(annotationAuthSub);
        when(beanFactoryMock.getBean("findAllByFirstnameMongoDbRepositoryTest")).thenReturn(subjectAndEnvAuthSub);

        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class,
                methodInvocationForTesting);

        // THEN
        compareTwoAuthSubs(correctAuthSub, resultAuthSub);

        Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(methodInvocationForTesting);
        verify(beanFactoryMock, times(1)).getBean("findAllByFirstnameMongoDbRepositoryTest");
    }

    @Test
    void when_annotationIsAvailableButNotCompleteAndBeanIsAvailable2_then_getAuthSub(CapturedOutput output) {
        // GIVEN
        var methodInvocationForTesting = new MethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);
        var correctAuthSub             = AuthorizationSubscription.of("",
                "find_all_by_firstname_reactive_mongo_repository", "resource", "environment");
        var annotationAuthSub          = AuthorizationSubscription.of("",
                "find_all_by_firstname_reactive_mongo_repository", "", "environment");
        var expectedLoggerMessage      = "Bean to receive specific AuthorizationSubscription Found: findAllByFirstnameMongoDbRepositoryTest";

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(annotationAuthSub);
        when(beanFactoryMock.getBean("findAllByFirstnameMongoDbRepositoryTest")).thenReturn(actionAndResourceAuthSub);

        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class,
                methodInvocationForTesting);

        // THEN
        compareTwoAuthSubs(correctAuthSub, resultAuthSub);

        Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(methodInvocationForTesting);
        verify(beanFactoryMock, times(1)).getBean("findAllByFirstnameMongoDbRepositoryTest");
    }

    @Test
    void when_annotationIsAvailableButNotCompleteAndBeanIsAvailable3_then_getAuthSub() {
        // GIVEN
        var correctAuthSub    = AuthorizationSubscription.of("method",
                "find_all_by_firstname_reactive_mongo_repository", "resource", "test");
        var annotationAuthSub = AuthorizationSubscription.of("method", "", "", "test");

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(annotationAuthSub);
        when(beanFactoryMock.getBean("findAllByFirstnameMongoDbRepositoryTest")).thenReturn(actionAndResourceAuthSub);
        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class,
                methodInvocation);

        // THEN
        compareTwoAuthSubs(correctAuthSub, resultAuthSub);
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class));
        verify(beanFactoryMock, times(1)).getBean(anyString());
    }

    @Test
    void when_annotationIsAvailableAndAuthSubIsComplete_then_getAuthSub() {
        // GIVEN
        var correctAuthSub = AuthorizationSubscription.of("method", "find_all_by_firstname_reactive_mongo_repository",
                "resource", "test");

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(correctAuthSub);
        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class,
                methodInvocation);

        // THEN
        compareTwoAuthSubs(correctAuthSub, resultAuthSub);
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class));
        verify(beanFactoryMock, times(0)).getBean(anyString());
    }

    @Test
    void when_noAnnotationButSpecificBeanForRepositoryIsAvailable_then_getAuthSub() {
        // GIVEN
        var correctAuthSub = AuthorizationSubscription.of("method", "", "", "test");

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(null);
        when(beanFactoryMock.getBean("findAllByAgeMongoDbRepositoryTest")).thenReturn(subjectAndEnvAuthSub);

        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class,
                methodInvocationWithoutEnforceAnnotation);

        // THEN
        compareTwoAuthSubs(correctAuthSub, resultAuthSub);
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class));
        verify(beanFactoryMock, times(1)).getBean(anyString());
    }

    @Test
    void when_noAnnotationButBeanForMethodIsAvailable_then_getAuthSub() {
        // GIVEN
        var correctAuthSub = AuthorizationSubscription.of("general", "general_protection_reactive_mongo_repository",
                "resource", "test");

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(null);
        when(beanFactoryMock.getBean(anyString())).thenAnswer(ans -> {
            if (ans.getArgument(0).equals("findAllByFirstnameMongoDbRepositoryTest")) {
                throw new NoSuchBeanDefinitionException("No such bean.");
            }

            if (ans.getArgument(0).equals("generalProtectionMongoDbRepositoryTest")) {
                return generalProtectionMongoDbRepositoryTest;
            }

            return null;
        });
        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class,
                methodInvocation);

        // THEN
        compareTwoAuthSubs(correctAuthSub, resultAuthSub);
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class));
        verify(beanFactoryMock, times(2)).getBean(anyString());
    }

    @Test
    void when_noAnnotationAnNoBeanForMethodIsAvailable_then_getAuthSub() {
        // GIVEN

        // WHEN
        when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class))).thenReturn(null);
        when(beanFactoryMock.getBean(anyString())).thenAnswer(ans -> {
            if (ans.getArgument(0).equals("findAllByFirstnameMongoDbRepositoryTest")) {
                throw new NoSuchBeanDefinitionException("No such bean.");
            }

            if (ans.getArgument(0).equals("generalProtectionMongoDbRepositoryTest")) {
                throw new NoSuchBeanDefinitionException("No such bean.");
            }

            return null;
        });
        var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(MongoDbRepositoryTest.class, methodInvocation);

        // THEN

        Assertions.assertNull(resultAuthSub);
        verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class));
        verify(beanFactoryMock, times(2)).getBean(anyString());
    }

    private void compareTwoAuthSubs(AuthorizationSubscription first, AuthorizationSubscription second) {
        Assertions.assertEquals(first.getSubject(), second.getSubject());
        Assertions.assertEquals(first.getAction(), second.getAction());
        Assertions.assertEquals(first.getResource(), second.getResource());
        Assertions.assertEquals(first.getEnvironment(), second.getEnvironment());
    }
}
