package io.sapl.spring.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Import;

/**
 * Enables SAPL Method Security.
 * 
 * @author Dominic Heutelbeck
 * @since 3.0.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(SaplMethodSecuritySelector.class)
public @interface EnableSaplMethodSecurity {

	/**
	 * Indicate whether subclass-based (CGLIB) proxies are to be created as opposed
	 * to standard Java interface-based proxies. The default is {@code false}.
	 * <strong> Applicable only if {@link #mode()} is set to
	 * {@link AdviceMode#PROXY}</strong>.
	 * <p>
	 * Note that setting this attribute to {@code true} will affect <em>all</em>
	 * Spring-managed beans requiring proxying, not just those marked with
	 * {@code @Cacheable}. For example, other beans marked with Spring's
	 * {@code @Transactional} annotation will be upgraded to subclass proxying at
	 * the same time. This approach has no negative impact in practice unless one is
	 * explicitly expecting one type of proxy vs another, e.g. in tests.
	 * 
	 * @return true if subclass-based (CGLIB) proxies are to be created
	 */
	boolean proxyTargetClass() default false;

	/**
	 * Indicate how security advice should be applied. The default is
	 * {@link AdviceMode#PROXY}.
	 * 
	 * @see AdviceMode
	 * @return the {@link AdviceMode} to use
	 */
	AdviceMode mode() default AdviceMode.PROXY;

}