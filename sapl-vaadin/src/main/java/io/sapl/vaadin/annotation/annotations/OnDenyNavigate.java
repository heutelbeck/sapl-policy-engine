package io.sapl.vaadin.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.sapl.vaadin.annotation.VaadinNavigationPepService;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnDenyNavigate {
    String value() default "/";

    String subject() default "";

    String action() default "";

    String resource() default "";

    String environment() default "";

    VaadinNavigationPepService.NavigationType navigation() default VaadinNavigationPepService.NavigationType.REDIRECT;

    VaadinNavigationPepService.LifecycleType onLifecycleEvent() default VaadinNavigationPepService.LifecycleType.ENTER;
}

