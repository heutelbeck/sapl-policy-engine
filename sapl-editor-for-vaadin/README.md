# An Editor for the SAPL DSL

Usage:

```java
public class JavabasedViewView extends Div {
    public JavabasedViewView() {
        setId("javabased-view-view");
        SaplEditor editor = new SaplEditor();
        add(editor);
        editor.addValueChangeListener(e -> System.out.println("value changed: "+e.isFromClient()+"  '"+e.getValue()+"'"));
        editor.setValue("policy \"set by Vaadin View after instantiation ->\\u2588<-\" permit");
    }
}
```

The editor requires the matching stext-services to be available on the server. These can be set up as follows:

```java
@Configuration
public class XtextServletConfiguration {

	@Bean
	public static ServletRegistrationBean<SAPLServlet> xTextRegistrationBean() {
		ServletRegistrationBean<SAPLServlet> registration = new ServletRegistrationBean<>(new SAPLServlet(),
				"/xtext-service/*");
		registration.setName("XtextServices");
		registration.setAsyncSupported(true);
		return registration;
	}

	@Bean
	public static FilterRegistrationBean<OrderedFormContentFilter> registration1(OrderedFormContentFilter filter) {
		FilterRegistrationBean<OrderedFormContentFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

}
```
