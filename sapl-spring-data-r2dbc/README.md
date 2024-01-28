# SAPL Spring Data R2DBC

## Overview
The sapl-spring-data-r2dbc module allows the user to manipulate database queries before they are executed. 

## Setup

There are a few prerequisites for a query to be rewritten.

### Dependency
To be able to use the module, the following dependency must be added to pom.xml: 

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-data-r2dbc</artifactId>
    <version>${sapl.version}</version>
</dependency>
```


### Scan base packages
The start method of the application must be made aware that the package with the functionality to manipulate the database query is scanned by Spring.

```java
@SpringBootApplication(scanBasePackages = {"io.sapl.saplspringdatar2dbc"})
```


### Spring interface
The user-defined repository whose methods are to be protected must be extended with one of the two interfaces.

```
org.springframework.data.repository.reactive.ReactiveCrudRepository
org.springframework.data.r2dbc.repository.R2dbcRepository
```


### Annotation for marking
To protect a method with SAPL, this method must be marked with the following annotation.

```java
@SaplProtectedR2dbc
public Flux<Person> findAllByAgeAfter(Integer age);
```

Alternatively, the user-defined repository can also be marked, so that all methods from the user-defined repository are marked.  

```java
@SaplProtectedR2dbc
public interface ProtectedR2dbcPersonRepository extends ReactiveCrudRepository<Person, Integer>
```


### Authorization Subscription
Two approaches have been created for defining the ``AuthorizationSubscription``. One of the two approaches can be used or both at the same time. An error is thrown if no ``AuthorizationSubscription`` was found. 

#### By Annotation 
The ``@EnforceR2dbc`` annotation can be used to create a complete ``AuthorizationSubscription``. Here is an example of all functionalities.

```java
@EnforceR2dbc(
    action = "find_all_by_age",
    subject = "@serviceForAnnotation.setSubject(#age, #firstnameContains)",
    resource = "#setResource(#age)",
    environment = "T(io.sapl.springdatar2dbcdemo.demo.repository.ClassForAnnotation)" +
                  ".setEnvironment(#firstnameContains)",
    staticClasses = {ClassForAnnotation.class})
public Flux<Person> findAllByAgeAfterAndFirstnameContaining(int age, String firstnameContains);
```

If the annotation ``EnforceR2dbc`` is used, the annotation ``SaplProtectedR2dbc`` does not also have to be used, as the method is also marked with the annotation ``EnforceR2dbc``. 

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
If no bean is found for the specific method, a general bean is searched for for the entire repository. The name of the general bean is made up of the keyword ``generalProtection`` and the name of the repository. However, no error is triggered if, for example, an attribute is missing, there is no annotation or no bean was found at all.
The following is an example of the naming of the bean.

| Type                | Name                                                |
| ------------------- | --------------------------------------------------- |
| Methode             | ``findAllByAgeAfter``                               |
| Repository          | ``ProtectedR2dbcPersonRepository``                  |
| Bean for method     | ``findAllByAgeAfterProtectedR2dbcPersonRepository`` |
| Bean for Repository | ``generalProtectedR2dbcPersonRepository``           |


The following is an example of creating the bean for protecting an entire repository.

```java
@Configuration
public class SaplConfig {

    @Bean
    public AuthorizationSubscription generalProtectionProtectedR2dbcPersonRepository() {
        return AuthorizationSubscription.of("subject", "action", "resource", "environment");
    }
}
```

### Policy
The following is an example of how a database query can be manipulated within a policy. The obligation is initiated by the ``r2dbcQueryManipulation`` type. Followed by a key called ``conditions``. Conditions here is introduced as an array, but the system only expects a string within the array. For generalization reasons, an array is expected here and a string is expected to make it easier to assemble the SQL query. Even with a string, the user can express everything desired and it makes everything less complicated. The condition must correspond to the syntax of an SQL WHERE clause. The domain type contains a property with name ``active``: `` "active = true" ``
Further obligations of type ``filterJsonContent`` or ``jsonContentFilterPredicate`` can be combined here as desired. These are part of the sapl-spring-security module. 

```json
policy "general_protection_person_repository_permit"
permit
where
    action == "general_protection_r2dbc_person_repository";
obligation {
               "type": "r2dbcQueryManipulation",
               "conditions": [ "active = true" ]
           }
advice {
    "id": "log",
    "message": "You are using SAPL for protection of database."
    }
```
 
