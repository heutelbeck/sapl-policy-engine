# SAPL Vaadin Library

The SAPL Vaadin Library provides functions and classes for an easy use of SAPL in combination with Vaadin components.
The following features are supported:
  - Single and multi subscription
  - Component interfaces for decision handling
  - Constraint handling
  - Constraint handler providers
  - Annotation & Builder based Lifecycle handling

## Getting started

Note: It is also recommended checking out the [SAPL Vaadin demo](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-vaadin) for some basic and easy use cases.

The entry point when using the library is the PepBuilderService, it should be autowired.

### Single Subscriptions

To link a Vaadin component, the function "with()" of the PepBuilderService needs to be used. This returns a specific pep builder of the given component.
With this builder, it is then possible to set subject, action and resource with the functions "subject()", "action()" and "resource()".
Afterwards, it needs to be specified, what to do when a decision occurs, for example with the methods:
  - onDecisionDo
  - onPermitDo
  - onDenyDo

At the end, it has to be subscribed with the "build()" function.

See the section "Change the enabled property of a button based on policy" for a simple example.

### Multi Subscriptions

To construct multi subscriptions, a MultiBuilder instance is needed. One may be created with PepBuilderService.getMultiBuilder().
First, a subject may be set with the "subject()" function. Then, as with single subscriptions, the "with()" function needs to be used with a Vaadin component object to create a VaadinMultiComponentPepBuilder.
With this builder, it is then possible to set subject, action and resource with the functions "subject()", "action()" and "resource()".
Afterwards, it needs to be specified, what is done when a decision occurs (See Single Subscription section).

When a component is done configuring, either the function "and()" may be used to add another subscription, or when fully done the function "build()" needs to be called.

See the section "Change the enabled property of multiple buttons based on policy" for a simple example

## Some basic use cases

Note: "builder" is a PepBuilderService and should be autowired.

### Change the enabled property of a button based on policy

```java
Button button = new Button();
builder.with(button)
	.subject("subject")
	.action("action")
	.resource("resource")
	.onDecisionEnableOrDisable()
	.build();
```

### Change the enabled property of multiple buttons based on policy

```java
Button button1 = new Button();
Button button2 = new Button();

builder.getMultiBuilder().subject("subject")
	.with(button1)
		.action("action").resource("resource")
		.onDecisionEnableOrDisable()
	.and(button2)
		.action("action").resource("resource")
		.onDecisionEnableOrDisable()
	.build()
```

## Constraint Handling
An AuthorizationDecision from the sapl PDP can include constraints. Sapl knows two types of constraints, obligations and
advices. The enforcement of obligations is mandatory and if there is no responsible handler or if a handler fails then the decision
evaluates to DENY.

Look at the <a href="https://sapl.io">sapl.io docs</a> for more information about ConstraintHandling.

Constraint enforcement is managed by the <b>VaadinConstraintEnforcementService</b> which manages an internal lists of 
 available constraint handler providers.

When the PDP retrieves a decision it forwards the decision to the VaadinConstraintEnforcementService. The VaadinConstraintEnforcementService 
asks every provider in its lists if there is a responsible provider. The matching handlers are put in a bundle. Finally, 
the handlers are being executed. In case of an obligation it returns a DENY decision if there is no responsible handler 
or if a handler fails.

### ConstraintHandlerProvider
The following ConstraintHandlerProvider interfaces are supported:
- RunnableConstraintHandlerProvider
- ConsumerConstraintHandlerProvider<UI>
- VaadinFunctionConstraintHandlerProvider

A new ConstraintHandlerProvider from the RunnableConstraintHandlerProvider can be defined as shown in the following 
example. Note: the getSignal method is required by the interface but not used in the context of vaadin.
```java
@Service
public class TestConstraintHandlerProvider implements RunnableConstraintHandlerProvider {
    @Override
    public Signal getSignal() {
        // The getSignal method is required by the interface but not used in the context of vaadin.
        return null;
    }

    @Override
    public Runnable getHandler(JsonNode constraint) {
        return () -> {
            if (constraint != null && constraint.has("message")) {
                var message = constraint.findValue("message").asText();
                this.logger.info(message);
            }
        };
    }

    @Override
    public boolean isResponsible(JsonNode constraint) {
        return constraint != null && constraint.has("type")
                && "log".equals(constraint.findValue("type").asText());
    }
}
```

A global ConstraintHandlerProvider can also be added using the following methods on the <b>ConstraintHandlerProviderService</b>
```java
// -> ConstraintHandlerProviderService
public void addGlobalVaadinFunctionProvider(VaadinFunctionConstraintHandlerProvider provider);
public void addGlobalConsumerProviders(ConsumerConstraintHandlerProvider<UI> provider);
public void addGlobalRunnableProviders(RunnableConstraintHandlerProvider provider);
```

A PEP local ConstraintHandlerProvider can also be added using the following methods on the abstract class <b>VaadinPepBuilder</b>
```java
// -> VaadinPepBuilder
public T addVaadinFunctionConstraintHandlerProvider(VaadinFunctionConstraintHandlerProvider provider);
public T addConsumerConstraintHandlerProvider(ConsumerConstraintHandlerProvider<UI> provider);
public T addRunnableConstraintHandlerProvider(RunnableConstraintHandlerProvider provider);
public T addConstraintHandler(Predicate<JsonNode> isResponsible, Function<JsonNode, Consumer<UI>> getHandler)
```


## Available Constraint Handler Providers
This library includes some Vaadin-specific global ConstraintHandlerProvider.

### Vaadin Notification
Use the global VaadinLoggingConstraintHandlerProvider to display a Vaadin notification based on sapl policies.
Just add the "logMessage" type to your sapl policy.
```
policy "log_transactions_vaadin"
permit action == "submit_transaction"
where
    action == "submit_transaction";
obligation
    {
        "type":     "saplVaadin",
        "id":       "showNotification",
        "message":  "transaction of " + resource.amount + " requested by " + subject.username
    }
```
| Index    | Description                                                                                                                                                                           | Default     |
|----------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------|
| message  | text content                                                                                                                                                                          |
| position | position of the notification, look at the <a href="https://vaadin.com/api/platform/22.0.2/com/vaadin/flow/component/notification/Notification.Position.html">vaadin documentation</a> | TOP_STRETCH |
| duration | milliseconds in which the notification is displayed.                                                                                                                                  | 5000        |

### Vaadin Confirmation Dialog
Use the global VaadinConfirmationDialogConstraintHandlerProvider to display a vaadin confirmation dialog based on sapl policies.
Just add the "requestConfirmation" type to your sapl policy.
```
policy "request_confirmation"
permit action == "submit_transaction"
where
    action == "submit_transaction";
    resource.amount > 2500;
obligation
    {
        "type" 	: "saplVaadin",
        "id"	: "requestConfirmation",
        "header": "Confirm transaction",
        "text": "Are you sure you want to execute this transaction?",
        "confirmText": "Execute"
    }
```
| Index       | Description                      | Default                                                                        |
|-------------|:---------------------------------|:-------------------------------------------------------------------------------|
| header      | heading                          | Confirm                                                                        |
| text        | text content                     | Confirmation has been requested. Are you sure you want to execute this action? |
| confirmText | label of the confirmation button | Confirm                                                                        |
| cancelText  | label of the cancel button       | Cancel                                                                         |

### Field Validation
Use the global FieldValidationConstraintHandlerProvider to validate Vaadin input fields based on sapl policies and JSON-schema.
Just add the "validation" type to your sapl policy.
```
policy "request_confirmation"
permit action == "submit_transaction"
obligation
    {
        "type" 	: "saplVaadin",
        "id"	: "validation",
        "fields": {
            "someFieldName": {
                "$schema": "http://json-schema.org/draft-07/schema#",
                "type": "number",
                "maximum": 20,
                "message": "cheese limit of 20"
            }
        }
    }
```
| Index         | Description                                                                                                                 |                                                                      
|---------------|:----------------------------------------------------------------------------------------------------------------------------|
| someFieldName | name of the input component                                                                                                 |
| $schema       | JSON-schema URI                                                                                                             |
| fieldType     | JSON-schema field type                                                                                                      |
| message       | error message                                                                                                               |
| ...           | some JSON-schema <a href="https://json-schema.org/draft/2020-12/json-schema-validation.html">constraints</a> (e.g. maximum) |

### Custom Vaadin-specific ConstraintHandlerProvider
You can also create your own ConstraintHandlerProvider with access to the Vaadin-UI. Implement the `VaadinFunctionConstraintHandlerProvider` in your custom provider (e.g. `VaadinLoggingConstraintHandlerProvider.class`).

## Builder based lifecycle handling
Similar to the use of the builder for components is the handling of vaadin lifecycle events with this library.
To enforce actions via blocking fluxes the user needs to use the PepBuilderService. To get the right lifecycle pep
builder the getLifecycleBeforeEnterBuilder() method needs to be called for handling beforeEnter events or
getLifecycleBeforeLeaveBuilder() needs to be called for handling beforeLeave events.

With this builder, it is possible to set subject, action, resource and environment with the
functions "subject()", "action()", "resource()", "environment()". If subject, action or resource isn't set
manually, subject will be automatically set to the current user role, action to "beforeEnter" or "beforeLeave",
depending on the use case, and resource to the path from source root.

Afterwards, it needs to be specified, what to do when a decision occurs, for example with the methods:

- onDecisionDo
- onPermitDo
- onDenyDo
- onDenyRerouteTo
- onDenyRedirectTo
- onDenyLogout
- onDenyNotify

Finally, the builder pattern is concluded by calling the build() method. This method returns a beforeEnterListener or
a beforeLeaveListener. Out of this reason the implementing class needs to implement the BeforeEnterObserver or 
the BeforeLeaveObserver. The beforeEnter() or beforeLeave() method of these Observers needs to be overwritten. So in 
the methods the previously constructed listener needs to be called and given the occurred lifecycle event.

### A basic use case
```java
public class ExamplePage implements BeforeEnterObserver {

 private final BeforeEnterListener beforeEnterListener;

 public ExamplePage(PepBuilderService builder) {
  this.beforeEnterListener = builder.getLifecycleBeforeEnterPepBuilder()
          .onDenyRerouteTo("/")
          .build();
 }

 @Override
 public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
  beforeEnterListener.beforeEnter(beforeEnterEvent);
 }
}
```

## Annotation based lifecycle handling with Spring Expression language (SpEL)

Annotate a view with the `OnDenyNavigate` annotation to manage Vaadin navigation with SAPL Vaadin. This annotation accepts
the following arguments:

| Name               | Type | Description                                                                                     | Default Value                                            |
|--------------------|------|-------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| `value`            | String | The path to navigate to                                                                         | "/"                                                      |
| `subject`          | String | SAPL subject                                                                                    | JSON representation of the current authorization context |
| `action`           | String | SAPL action                                                                                     | string "navigate_to"                                     |
| `resource`         | String | SAPL resource                                                                                   | Name of the target class of the navigation               |
| `environment`      | String | SAPL environment                                                                                | empty String                                             |
| `navigation`       | VaadinNavigationPepService.NavigationType | Whether to trigger a REDIRECT or a REROUTE event                                                | REDIRECT                                                 |
| `onLifecycleEvent` | VaadinNavigationPepService.LifecycleType | The Vaadin lifecycle at which the policy is executed (ENTER = beforeEnter, LEAVE = beforeLeave) | ENTER                                                    |

The parameters `subject`, `action`, `resource`, and `environment` can be set using the Spring Expression Language (SpEL).
When doing so, the current, possibly extended, `org.springframework.security.core.Authentication` (from `SecurityContextHolder.getContext().getAuthentication()`)
is used as the expression context. That way, you can access any custom fields of your `Authentication` from inside the expression
instead of using the provided default values.

For an example see `AnnotationPage.java` in the demo project:
```java
@OnDenyNavigate(value = "/", subject = "{roles: getAuthorities().![getAuthority()]}", environment="'environment information'")
@PageTitle("Annotation Page")
@Route(value = "annotation-page", layout = MainLayout.class)
public class AnnotationPage extends VerticalLayout {
    /// ...
}
```

# build jar file (to ~/.m2/repository/io/sapl/sapl-vaadin/)
```shell
mvn install
````

# create zip package for vaadin Directory
```sh
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions
mvn install -Pdirectory
```
