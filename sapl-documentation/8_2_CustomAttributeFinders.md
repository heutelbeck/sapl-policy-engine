---
layout: default
title: Custom Attribute Finders
#permalink: /reference/Custom-Attribute-Finders/
parent: Attribute Finders
grand_parent: SAPL Reference
nav_order: 250
---

## Custom Attribute Finders

For a more in-depth look at the process of creating a custom PIP, please refer to the demo project. It provides a walkthrough of the entire process and contains extensive examples: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension> .

**Attribute finders** are functions that potentially take a left-hand argument (i.e., the object of which the attribute is to be determined), a `Map` of variables defined in the current evaluation scope, and an optional list of incoming parameter streams.

In SAPL, a Policy Information Point is an instance of a class that supplies a set of such functions. To declare functions and classes to be attributes or PIPs, SAPL uses annotations. Each PIP class must be known to the PDP. The embedded PDP provides an `AnnotationAttributeContext`, which takes arbitrary Java objects as PIPs. To be recognized as a PIP, the respective class must be annotated with `@PolicyInformationPoint`. The optional annotation attribute `name` contains the PIP’s name as it will be available in SAPL policies. If the attribute is missing, the name of the Java class is used. The optional annotation attribute `description` contains a string describing the PIP for documentation purposes.

```java
@PolicyInformationPoint(name = "user", description = "This the documentation of the PIP")
public class SampleUserPIP {
    ...
}
```

The individual attributes supplied by the PIP are identified by adding the annotation `@Attribute`. An optional annotation attribute `name` can contain a name for the attribute. The attribute must be a string containing an identifier. By default, the name of the function will be used. The annotation attribute `docs` can contain a string describing the attribute.

SAPL allows for attribute name overloading. This means that can be multiple implementations for resolving an attribute with one name. For example, you could implement, an environment attribute (`<some.attribute>`), a regular attribute (`"UTC".<some.attribute>`), both with parameters (`<some.attribute("UTC")>`, `"UTC.<some.attribute(5000)>`), and maybe even with a variable number of arguments (`<some.attribute>("a","b","c","d",123,4)`).

For an attribute of some other object, this object is called the left-hand parameter of the attribute. When writing a method implementing an attribute that takes a left-hand parameter, this parameter must be the first parameter of the method, and it must be of type `Val`:

```java
/* subject.<user.attribute> */
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute(@Object Val leftHandObjectOfTheAttribute) {
    ...
}
```

Input parameters of the method may be annotated with type validation annotations (see module `sapl-extension-api`, package `io.sapl.api.validation`). When present, the policy engine validates the contents of the parameters before calling the method. Therefore, if present, the method does not need to perform additional type validation. The method will only be called if the left-hand parameter is a JSON Object in the example above. While different parameter types can be used to disambiguate overloaded methods in languages like Java, this is not possible in SAPL.

A typical use-case for attribute finder is retrieving attribute data from an external data source. If this is a network service like an API or database, the attribute finder usually must know the network address and credentials to authenticate with the service. Such data should never be hardcoded in the PIP. Also, developers should never store this data in policies. In SAPL, developers should store this information and further configuration data for PIPs in the environment variables. For the SAPL Server LT these variables are stored in the pdp.json configuration file, and for the SAPL Server CE they can be edited via the UI.

To access the environment variables, attribute finder methods can consume a Map<String,JsonNode>. The PDP will inject this map at runtime. The map contains all variables available in the current evaluation scope. This map must be the first parameter of the left-hand parameter, or it must be the first parameter for environment attributes. Note that attempting to overload an attribute name with and without variables as a parameter that accept the same number of other parameters will fail. The engine cannot disambiguate these two attributes at runtime.

```java
/* subject.<user.attribute> definition would clash with last example if defined at the same time in the same PIP*/
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute(@Object Val leftHandObjectOfTheAttribute, Map<String,JsonNode> variables) {
    ...
}
```

To define environment attributes, i.e., attributes without left-hand parameters, the method definition explicitly does not define a `Val` parameter as its first parameter.

```java
/* <user.attribute> */
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute() {
    ...
}
```

Optionally, the environment attribute can consume variables:

```java
/* <user.attribute> definition would clash with last example if defined at the same time in the same PIP */
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute(Map<String,JsonNode> variables) {
    ...
}
```

A unique feature of SAPL is the possibility to parameterize attribute finders in polices. E.g., `subject.<employees.qualificationOfType("IT")>` could return all qualifications of the subject in the domain of `"IT"`. Syntactically, SAPL also allows for the concatenation (`subject.<pip1.attr1>.<pip2.attr2>`) and nesting of attribute (`subject.<pip1.attr1(resource.<pip2.attr2>,<pip3.attr3>)>`).

Regardless of if the attribute is an environment attribute or not, the parameters in brackets are declared as parameters of the method with the type `Flux<Val>`.

```java
/* subject.<user.attribute("param1",123)> */
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute(@Object Val leftHandObjectOfTheAttribute, Map<String,JsonNode> variables, @Text Flux<Val> param1, @Number Flux<Val> param2) {
    ...
}
```

Additionally, using Java variable argument lists, it is possible to declare attributes with a variable number of attributes. If a method wants to use variable arguments, the method must not declare any other parameters besides the optional left-hand or variables parameters. If an attribute is overloaded, an implementation with an exact match of the number of arguments takes precedence over a variable arguments implementation.

```java
/* subject.<user.attribute("AA","BB","CC")> */
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute(@Object Val leftHandObjectOfTheAttribute, Map<String,JsonNode> variables, @Text Flux<Val>... params) {
    ...
}
```

Alternatively defining the variable arguments can be defined as an array.

```java
/* <user.attribute("AA","BB","CC")> */
@Attribute(name = "attribute", docs = "documentation")
public Flux<Val> attribute(@Text Flux<Val>[] params) {
    ...
}
```

The methods must not declare any further arguments.

**Note**: Developers must add `-parameters` parameter to the compilation to ensure that the automatically generated documentation does contain the names of the parameters used in the methods.
