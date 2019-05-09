package io.sapl.grammar.web;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.HiddenHttpMethodFilter;

@EnableAutoConfiguration
@Configuration
public class XtextServletConfiguration {

	@Bean
	public static ServletRegistrationBean<SAPLServlet> xTextRegistrationBean() {
		ServletRegistrationBean<SAPLServlet> registration = new ServletRegistrationBean<>(
				new SAPLServlet(), "/xtext-service/*");
		registration.setName("XtextServices");
		registration.setAsyncSupported(true);
		return registration;
	}

	@Bean
	public static FilterRegistrationBean<OrderedFormContentFilter> registration1(
			OrderedFormContentFilter filter) {
		FilterRegistrationBean<OrderedFormContentFilter> registration = new FilterRegistrationBean<>(
				filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public static FilterRegistrationBean<HiddenHttpMethodFilter> registration2(
			HiddenHttpMethodFilter filter) {
		FilterRegistrationBean<HiddenHttpMethodFilter> registration = new FilterRegistrationBean<>(
				filter);
		registration.setEnabled(false);
		return registration;
	}

}
