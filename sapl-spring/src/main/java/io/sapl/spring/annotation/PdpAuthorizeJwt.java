package io.sapl.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PdpAuthorizeJwt {

	String DEFAULT = "default";

	String subject() default DEFAULT;

	String action() default DEFAULT;

	String resource() default DEFAULT;
}
