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


### Scan base packages
The start method of the application must be made aware that the package with the functionality to manipulate the database query is scanned by Spring.

```java
@SpringBootApplication(scanBasePackages = {"io.sapl.saplspringdatamongoreactive"})
```


### Spring interface
The user-defined repository whose methods are to be protected must be extended with one of the two interfaces.

```
org.springframework.data.repository.reactive.ReactiveCrudRepository
org.springframework.data.mongodb.repository.ReactiveMongoRepository
```


### Annotation for marking
To protect a method with SAPL, this method must be marked with the following annotation.

```java
@SaplProtectedMongoReactive
public Flux<User> findAllByAgeAfter(Integer age);
```

Alternatively, the user-defined repository can also be marked, so that all methods from the user-defined repository are marked.  

```java
@SaplProtectedMongoReactive
public interface ProtectedReactiveMongoUserRepository extends ReactiveCrudRepository<User, Integer>
```


### Authorization Subscription
Two approaches have been created for defining the ``AuthorizationSubscription``. One of the two approaches can be used or both at the same time. An error is thrown if no ``AuthorizationSubscription`` was found. 

#### By Annotation 
The ``@EnforceMongoReactive`` annotation can be used to create a complete ``AuthorizationSubscription``. Here is an example of all functionalities.

```java
@EnforceMongoReactive(
    action = "find_all_by_age",
    subject = "@serviceForAnnotation.setSubject(#age, #firstnameContains)",
    resource = "#setResource(#age)",
    environment = "T(io.sapl.springdatamongoreactivedemo.demo.repository.ClassForAnnotation)" +
                  ".setEnvironment(#firstnameContains)",
    staticClasses = {ClassForAnnotation.class})
public Flux<User> findAllByAgeAfterAndFirstnameContaining(int age, String firstnameContains);
```

If the annotation ``EnforceMongoReactive`` is used, the annotation ``SaplProtectedMongoReactive`` does not also have to be used, as the method is also marked with the annotation ``EnforceMongoReactive``. 

##### Static class with method
The individual attributes of the annotation can be filled with paths that point to static classes within the corresponding project. This requires a special operator ``T``, which is placed in front of the path within the value. The path itself is enclosed in round brackets. This is followed by the name of the desired method from the corresponding static class with suitable parameters. The environment attribute uses this functionality. The static class with the name ``ClassForAnnotation`` and its method ``setEnvironment`` with a parameter of type ``String`` can also be seen at this point. The operator ``T`` is a key element and initiates the use of SpEL. 


##### Static class with specification of the class type via an additional variable
The individual attributes of the annotation can be filled with names of methods from static classes within the corresponding project. So that the ``EnforceAnnotationHandler`` service can find a static method without the additional specification of the class path, it requires the specification of the static class in the additional variable of the annotation called ``staticClasses``. However, the class is not to be specified as type ``String``, but as type ``Class``. The resource attribute uses this functionality. The ``ClassForAnnotation`` class has a second method called ``setResource`` with a parameter of type ``int``. The ``staticClasses`` variable contains the specification of the static class. In order for the ``EnforceAnnotationHandler`` service to recognize that it is a reference to a method of a static class, the value of the variable must begin with a hash character.

##### Reference to a parameter of the method
The parameters of the method from the repository interface, or the arguments of the method, can be included within the individual attributes of the annotation. A hash character introduces a reference to a parameter of the method. The hash character is immediately followed by the exact name of the parameter. This functionality can be seen within the lists for the arguments of the methods of the three variables ``subject``, ``resource`` and ``environment``. 


##### Bean as a reference
The individual attributes of the annotation can be filled with references to beans. This functionality can be seen in the ``subject`` variable. The name of the bean in the example above follows the ``@`` symbol and is called ``serviceForAnnotation``. This bean has a method called ``setSubject``. The method is specified within the variable separated from the bean name by a dot. 

##### JSON string
The individual attributes of the annotation can be filled with JSON strings. To signal to the ``EnforceAnnotationHandler`` service that the value of the variable is a JSON string, the value of the variable must begin with an opening curly bracket and end with a corresponding closing bracket. The ``EnforceAnnotationHandler`` service then only checks whether the received value is a valid JSON string and otherwise issues an error.

### By Bean 
In order to be able to offer the ``AuthorizationSubscription`` as a bean, the bean must have a specific name. For this purpose, the name of the bean must consist of the name of the method and the name of the repository. 
If no bean is found for the specific method, a general bean is searched for the entire repository. The name of the general bean is made up of the keyword ``generalProtection`` and the name of the repository. However, no error is triggered if, for example, an attribute is missing, there is no annotation or no bean was found at all.
The following is an example of the naming of the bean.

| Type                | Name                                                      |
| ------------------- | --------------------------------------------------------- |
| Methode             | ``findAllByAgeAfter``                                     |
| Repository          | ``ProtectedReactiveMongoUserRepository``                  |
| Bean for method     | ``findAllByAgeAfterProtectedReactiveMongoUserRepository`` |
| Bean for Repository | ``generalProtectionProtectedReactiveMongoUserRepository`` |


The following is an example of creating the bean for protecting an entire repository.

```java
@Configuration
public class SaplConfig {

    @Bean
    public AuthorizationSubscription generalProtectionProtectedReactiveMongoUserRepository() {
        return AuthorizationSubscription.of("subject", "action", "resource", "environment");
    }
}
```

### Policy
The following is an example of how a database query can be manipulated within a policy. The obligation is initiated by the ``mongoQueryManipulation`` type. Followed by a key called ``conditions``, which expects an array of statements that follow the syntax of the MongoDB Query Language. The domain type contains a property with name ``active``:  `` "{'active': {'$eq': true}}" ``

Further obligations of type ``filterJsonContent`` or ``jsonContentFilterPredicate`` can be combined here as desired. These are part of the sapl-spring-security module. 

```json
policy "permit_general_protection_reactive_user_repository"
permit
where
    action == "general_protection_reactive_user_repository";
obligation {
               "type": "mongoQueryManipulation",
               "conditions": [
                                "{'active': {'$eq': true}}"
               ]
             }
advice {
    "id": "log",
    "message": "You are using SAPL for protection of database."
    }
```

