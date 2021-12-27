# SAPL Extension API

This module contains the interfaces and classes required to write custom Policy Information Points (PIPs) or function libraries.

A demo project with more documentation is available: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension>.

## SAPL Values

The internal data model of the SAPL engine uses the `Val` class. `Val` is a monad style wrapper around Jackson `JsonNode`, allowing for `undefined` and errors as additional values. In addition, the class contains numerous convenience methods for creating values or `Flux<Value>` objects. Example usages of `Val`:

```java
		Val undefined = Val.UNDEFINED;
		Val textValue = Val.of("Some Text");

		if(textValue.isDefined()) {
			JsonNode jsonNode = textValue.get();
		}

		if(textValue.isTextual()) {
			String text = textValue.getText();
		}

		if(undefined.isUndefined()) {
			// handle undefined value
		}

		if(undefined.isDefined()) {
			// handle defined value
		}

		Val error = Val.error("Well formulated error message");

		if(error.isError()) {
			String message = error.getMessage();
		}

		Flux<Val> fluxOnlyContainingOneFalse = Val.fluxOfFalse();
```

## Custom Function Library

By developing a custom function library, a PDP can be extended to interpret policies making use of these functions. An example of a custom function library can be found in the `sapl-geo` module. There, a number of functions are implemented, enabling the processing of GEO JSON Objects. So, for example, a function can test if a point is contained within a polygon using a geographic coordinate system to implement features like geo-fencing. Whenever an application domain makes repeated use of specific JSON schemes and its semantics require some domain-specific processing for the reasoning within SAPL policies, implementing a custom function library can be helpful and significantly improve the readability, maintainability, and expressiveness of the policies.

Each function library is a Java class and has to define a namespace in which its functions reside. This behavior is similar to Java packages, and analogous functions can be imported into SAPL policies to provide shorthand access. Methods of the class can be exposed as SAPL functions.

A function library class must be annotated with `@FunctionLibrary`. This annotation takes two optional string parameters. `name` defines the namespace and `description` contains a textual description of the function library. This description is used for automatically generating documentation for users of a PDP application with a graphical user interface. If the parameter `name` is not set, the namespace will be derived from the Java package and class name.

```java
@FunctionLibrary(name = "some.custom", description = SomeCustomFunctionLibrary.DESCRIPTION)
public class SomeCustomFunctionLibrary {
	public static final String DESCRIPTION = "This library contains custom functions to ...";
   [...]
}
```

All custom functions must not make use of IO operations because these would block the reactive processing within the policy engine. Functions must be simple transformations from a list of parameter values to a single output value. SAPL functions must not have side effects and should be deterministic. Functions are implemented as methods. These methods may contain any number of `Val` parameters and a final var args `Val...` parameter. A method to be exposed as a SAPL function must be annotated with the `@Function` annotation with the two optional parameters `name` and `docs`. A function with `name = "doSomething"` in a function library `some.custom` will be accessible as `some.custom.doSomething` in SAPL or simply as `doSomething` if imported accordingly. If `name` is undefined, the name of the method will be used. The `docs` parameter will be used for documentation in a UI analogous to the `description` parameter of the library itself and should contain information for a policy author on how to apply the function. The methods must have `public` visibility.

```java
@FunctionLibrary(name = "some.custom", description = SomeCustomFunctionLibrary.DESCRIPTION)
public class SomeCustomFunctionLibrary {
	public static final String DESCRIPTION = "This library contains custom functions to ...";

	@Function(docs = "converts a number to a string")
	public static Val numberToString(@Number Val parameter) {
		return Val.of(parameter.numberValue().toString());
	}
}
```

In this example, the method is `static` because it does not require the state of the class. As SAPL functions must not have side effects, many methods will look this way. However, if the function requires some other objects to perform its actions, these may be stored in the class, resulting in non-static methods. In a SAPL policy, the function can be used as follows:

```
policy "test" permit where "123" == some.custom.numberToString(123);
```

Also note the annotation `@Number` on the parameter. When the policy engine detects such an annotation on a parameter, it will automatically make a type check of the value and return an error if the implied type constraint on the value is violated before calling the method. Type validation is described in more detail below.

The policy engine will check if the number of parameters in a policy matches the number of the parameters of the method and return an error if this does not match. While doing so, var args semantics are respected.

## Custom Policy Information Point

Attribute finders are used to receive attributes that are not included in the authorization subscription context from external PIPs. Just like in `subject.age`, the selection step `.age` selects the attribute ``age``s value, `subject.<user.age>` could be used to fetch an `age` attribute which is not included in the `subject` but can be obtained from a PIP named `user`.

Attribute finders are organized in libraries as well and follow the same naming conventions as functions, including the use of imports. An attribute finder library constitutes a PIP (e.g., `user`) and can contain any number of attributes (e.g., `age`). They are called by a selection step applied to any value, e.g., `subject.<user.age>`. The attribute finder step receives the previous selection result (in the example: `subject`) and returns the requested attribute.

The concept of attribute finders can be used in a flexible manner: There may be finders that take an object (like in the example above, `subject.<user.age>`) as well as attribute finders which expect a primitive value (e.g., `subject.id.<user.age>` with `id` being a number). In addition, attribute finders may also return an object which can be traversed in subsequent selection steps (e.g., `subject.<user.profile>.age`). It is even possible to join multiple attribute finder steps in one expression (e.g., `subject.<user.profile>.supervisor.<user.profile>.age`).

Optionally, an attribute finder may be supplied with a list of parameters: `x.<finder.name(p1,p2,...)>`. Also, here nesting is possible. Thus `x.<finder.name(p1.<finder.name2>,p2,...)>` is a working construct.

Furthermore, attribute finders may be used without any leading value `<finder.name(p1,p2,...)>`. These are called environment attributes.

The way to read a statement with an attribute finder is as follows. For `subject.<groups.membership("studygroup")>` one would say "get the attribute `group.membership` with parameter `"studygroup"` of the subject".

Attribute finders often receive information from external data sources such as files, databases, or HTTP requests which may take a certain amount of time. Therefore, they must not be used in a target expression. Attribute finders can access environment variables.

### Custom Attribute Finders

For a more in-depth look at the process of creating a custom PIP, please refer to the demo project. It provides a walkthrough of the entire process and contains extensive examples: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension>.

*Attribute finders* are functions that potentially take a left-hand argument (i.e., the object of which the attribute is to be determined), a `Map` of variables defined in the current evaluation scope, and an optional list of incoming parameter streams.

In SAPL, a Policy Information Point is an instance of a class that supplies a set of such functions. To declare functions and classes to be attributes or PIPs, SAPL uses annotations. Each PIP class must be known to the PDP. The embedded PDP provides an `AnnotationAttributeContext`,  which takes arbitrary Java objects as PIPs. To be recognized as a PIP, the respective class must be annotated with `@PolicyInformationPoint`. The optional annotation attribute `name` contains the PIP's name as it will be available in SAPL policies. If the attribute is missing, the name of the Java class is used. The optional annotation attribute `description` contains a string describing the PIP for documentation purposes.

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

Input parameters of the method may be annotated with type validation annotations (see module `sapl-extension-api`, package `io.sapl.api.validation`). When present, the policy engine validates the contents of the parameters before calling the method.  Therefore, if present, the method does not need to perform additional type validation. The method will only be called if the left-hand parameter is a JSON Object in the example above. While different parameter types can be used to disambiguate overloaded methods in languages like Java, this is not possible in SAPL.


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

## Parameter Validation

`Val` parameters of methods exposed as functions or attributes may be annotated with a subset of the SAPL type validation annotations: `@Array`, `@Bool`, `@Int`, `@JsonObject`, `@Long`, `@Number`, or `@Text`. Whenever at least one of these annotations is put on a parameter, before invoking the method the policy engine will check if the actual value matches at least one of the annotations.

## Deploying Function Libraries and Policy Information Points in a PDP

Both function libraries and PIP must be registered with the PDP to be usable within policies. There are currently two scenarios for registering. The most common scenario will be to extend an existing PDP Server implementation. The second scenario addresses using an embedded PDP in your application.

### Registration with an Embedded PDP

In a simple Java application, a PDP is instantiated using the `PolicyDecisionPointFactory`, the different factory methods for file- or resource-based PDPs take Collections of function library and PDP objects as parameters will register these for the PDP.

```java
	PolicyDecisionPoint pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(path, List.of(new EchoPIP()), List.of(new SimpleFunctionLibrary()));
```

When using the Spring Security integration, PIPs and function libraries can be registered with the application context as beans. The SAPL auto-configuration will pick them up automatically if present is their packages are configured to be scanned by Spring. E.e., a PIP may be annotated with `@Component` or  `@Service`.

### Registering with a SAPL Server

The details about deploying to a server are addressed in their respective documentation:

* SAPL Server LT: <https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-lt/README.md>
* SAPL Server CE: <https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-ce/README.md>

## Project Configuration (Maven)

Remember to get a GitHub access token to be able to use the dependency. See <https://github.com/heutelbeck/packages>.
Example POM for a SAPL extension:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>com.example</groupId>
	<artifactId>custom-sapl-extension</artifactId>
	<version>0.0.1</version>
	<name>A SAPL Extension</name>

	<properties>
		<java.version>11</java.version>
		<maven.compiler.source>${java.version}</maven.compiler.source>
		<maven.compiler.target>${java.version}</maven.compiler.target>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>io.sapl</groupId>
				<artifactId>sapl-bom</artifactId>
				<version>2.0.1</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>			
	</dependencyManagement>

	<dependencies>
		<dependency>
			<groupId>io.sapl</groupId>
			<artifactId>sapl-extensions-api</artifactId>
			<scope>provided</scope>
		</dependency>
		<!-- Add extension specific dependencies -->
	</dependencies>

	<repositories>
		<!-- The SAPL dependencies are hosted on GitHub packages. A matching server
			entry has to be set in the settings.xml using a GitHub personal access token.
			Also refer to: https://github.com/heutelbeck/packages -->
		<repository>
			<id>sapl</id>
			<name>SAPL Maven Repository</name>
			<url>https://maven.pkg.github.com/heutelbeck/packages</url>
			<snapshots>
				<enabled>true</enabled>
				<updatePolicy>always</updatePolicy>
			</snapshots>
		</repository>
	</repositories>

	<!--

		In case the resulting JAR should not be used as a dependency but as a fat
		JAR to be deployed with a PDP Server, the maven-assembly-plugin can be used
		to package all dependencies into a single JAR.
		It is recommended to declare all dependencies expected to be already present
		with the Server as <scope>provided</scope>.

	-->
	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-assembly-plugin</artifactId>
				<version>3.3.0</version>
				<configuration>
					<descriptorRefs>
						<descriptorRef>jar-with-dependencies</descriptorRef>
					</descriptorRefs>
				</configuration>
				<executions>
					<execution>
						<id>make-assembly</id>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
</project>
```
