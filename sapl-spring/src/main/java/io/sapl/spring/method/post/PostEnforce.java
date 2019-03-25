package io.sapl.spring.method.post;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The @PostEnforce annotation establishes a policy enforcement point (PEP)
 * after the invocation of the annotated method, and alters the return value if
 * indicated by the policy decision point (PDP).
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface PostEnforce {
	String subject() default "";

	String action() default "";

	String resource() default "";

	String environment() default "";
}
