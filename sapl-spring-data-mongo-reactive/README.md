# SAPL Spring Data Reactive Mongo

## Overview
The sapl-spring-data-mongo-reactive module allows the user to manipulate database queries before they are executed. 

## Setup

There are a few prerequisites for a query to be rewritten.

### Dependency
To be able to use the module, the following dependency must be added to pom.xml: 

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-data-mongo-reactive</artifactId>
    <version>${sapl.version}</version>
</dependency>
```


### Spring interface
The user-defined repository whose methods are to be protected must be extended with one of the two interfaces.

```
org.springframework.data.repository.reactive.ReactiveCrudRepository
org.springframework.data.mongodb.repository.ReactiveMongoRepository
```


### Authorization Subscription
The ``@QueryEnforce`` annotation can be used to create a complete ``AuthorizationSubscription``. Here is an example of all functionalities. An error is thrown if no ``AuthorizationSubscription`` was found. 

```java
@QueryEnforce(
    action = "find_all_by_age",
    subject = "@serviceForAnnotation.setSubject(#age, #firstnameContains)",
    resource = "#setResource(#age)",
    environment = "T(io.sapl.springdatamongoreactivedemo.demo.repository.ClassForAnnotation)" +
                  ".setEnvironment(#firstnameContains)",
    staticClasses = {ClassForAnnotation.class})
public Flux<User> findAllByAgeAfterAndFirstnameContaining(int age, String firstnameContains);
```

##### Static class with method
The individual attributes of the annotation can be filled with paths that point to static classes within the corresponding project. This requires a special operator ``T``, which is placed in front of the path within the value. The path itself is enclosed in round brackets. This is followed by the name of the desired method from the corresponding static class with suitable parameters. The environment attribute uses this functionality. The static class with the name ``ClassForAnnotation`` and its method ``setEnvironment`` with a parameter of type ``String`` can also be seen at this point. The operator ``T`` is a key element and initiates the use of SpEL. 


##### Static class with specification of the class type via an additional variable
The individual attributes of the annotation can be filled with names of methods from static classes within the corresponding project. So that the ``EnforceAnnotationHandler`` service can find a static method without the additional specification of the class path, it requires the specification of the static class in the additional variable of the annotation called ``staticClasses``. However, the class is not to be specified as type ``String``, but as type ``Class``. The resource attribute uses this functionality. The ``ClassForAnnotation`` class has a second method called ``setResource`` with a parameter of type ``int``. The ``staticClasses`` variable contains the specification of the static class. In order for the ``EnforceAnnotationHandler`` service to recognize that it is a reference to a method of a static class, the value of the variable must begin with a hash character.


##### Reference to a parameter of the method
The parameters of the method from the repository interface, or the arguments of the method, can be included within the individual attributes of the annotation. A hash character introduces a reference to a parameter of the method. The hash character is immediately followed by the exact name of the parameter. This functionality can be seen within the lists for the arguments of the methods of the three variables ``subject``, ``resource`` and ``environment``. 


##### Bean as a reference
The individual attributes of the annotation can be filled with references to beans. This functionality can be seen in the ``subject`` variable. The name of the bean in the example above follows the ``@`` symbol and is called ``serviceForAnnotation``. This bean has a method called ``setSubject``. The method is specified within the variable separated from the bean name by a dot. 


### Policy
The following is an example of how a database query can be manipulated within a policy. The obligation is initiated by the ``mongoQueryManipulation`` type. Followed by a key called ``conditions``, which expects an array of statements that follow the syntax of the MongoDB Query Language. The domain type contains a property with name ``active``:  `` "{'active': {'$eq': true}}" ``

```json
policy "permit active equals true"
permit
where
    action == "findAll";
obligation {
               "type": "mongoQueryManipulation",
               "conditions": [
                                "{'active': {'$eq': true}}"
               ]
           }
```