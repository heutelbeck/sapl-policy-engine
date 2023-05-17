package io.sapl.spring.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import lombok.extern.slf4j.Slf4j;

/**
 * Registers an
 * {@link org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator
 * AnnotationAwareAspectJAutoProxyCreator} against the current
 * {@link BeanDefinitionRegistry} as appropriate based on a given @
 * {@link EnableMethodSecurity} annotation.
 *
 * <p>
 * Note: This class is necessary because AspectJAutoProxyRegistrar only supports
 * EnableAspectJAutoProxy.
 * </p>
 *
 * Based on MethodSecurityAspectJAutoProxyRegistrar
 */
@Slf4j
class SaplMethodSecurityAspectJAutoProxyRegistrar implements ImportBeanDefinitionRegistrar {

	/**
	 * Register, escalate, and configure the AspectJ auto proxy creator based on the
	 * value of the @{@link EnableSaplMethodSecurity#proxyTargetClass()} attribute
	 * on the importing {@code @Configuration} class.
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		log.info("registerBeanDefinitions");
		// TODO: ...
		registerBeanDefinition("preFilterAuthorizationMethodInterceptor",
				"org.springframework.security.authorization.method.aspectj.PreFilterAspect", "preFilterAspect$0",
				registry);
		registerBeanDefinition("postFilterAuthorizationMethodInterceptor",
				"org.springframework.security.authorization.method.aspectj.PostFilterAspect", "postFilterAspect$0",
				registry);
		registerBeanDefinition("preAuthorizeAuthorizationMethodInterceptor",
				"org.springframework.security.authorization.method.aspectj.PreAuthorizeAspect", "preAuthorizeAspect$0",
				registry);
		registerBeanDefinition("postAuthorizeAuthorizationMethodInterceptor",
				"org.springframework.security.authorization.method.aspectj.PostAuthorizeAspect",
				"postAuthorizeAspect$0", registry);
		registerBeanDefinition("securedAuthorizationMethodInterceptor",
				"org.springframework.security.authorization.method.aspectj.SecuredAspect", "securedAspect$0", registry);
	}

	private void registerBeanDefinition(String beanName, String aspectClassName, String aspectBeanName,
			BeanDefinitionRegistry registry) {
		if (!registry.containsBeanDefinition(beanName)) {
			return;
		}
		BeanDefinition        interceptor = registry.getBeanDefinition(beanName);
		BeanDefinitionBuilder aspect      = BeanDefinitionBuilder.rootBeanDefinition(aspectClassName);
		aspect.setFactoryMethod("aspectOf");
		aspect.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		aspect.addPropertyValue("saplSecurityInterceptor", interceptor);
		registry.registerBeanDefinition(aspectBeanName, aspect.getBeanDefinition());
	}

}
