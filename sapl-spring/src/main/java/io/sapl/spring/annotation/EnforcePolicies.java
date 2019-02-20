package io.sapl.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The EnforcePolicies annotation establishes a policy enforcement point (PEP)
 * surrounding the annotated method.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface EnforcePolicies {

	String subject() default "";

	String action() default "";

	String resource() default "";

	boolean resultResource() default false;
}
