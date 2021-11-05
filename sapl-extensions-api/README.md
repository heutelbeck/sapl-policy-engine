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

A custom Policy Information Point (PIP) can be used in policies to retrieve external attributes as data streams for making policy decisions. Each PIP is a Java class and has to define a namespace in which its attributes reside. This is similar to Java packages, and analogous attributes can be imported in SAPL policies to provide shorthand access. Methods of the class can be exposed as SAPL attributes.

A PIP must be annotated with `@PolicyInformationPoint`. This annotation takes two optional string parameters. `name` defines the namespace and `description` contains a textual description of the PIP. This description is used for automatically generating documentation for users of a PDP application with a graphical user interface. If the parameter `name` is not set, the namespace will be derived from the Java package and class name.

```java
@PolicyInformationPoint(name = "sales", description = SomeCustomFunctionLibrary.DESCRIPTION)
public class SalesPolicyInformationPoint {
	public static final String DESCRIPTION = "This PIP provides access to the sales data ...";
   [...]
}
```

Methods of the PIP class may be exposed as SAPL attributes by adding the `@Attribute` annotation with the two optional parameters `name` and `docs`. An attribute with `name = "someAttribute"` in a PIP class `some.custom` will be accessible as `some.custom.someAttribute` in SAPL or simply as `someAttribute` if imported accordingly. If `name` is undefined, the name of the method will be used. The `docs` parameter will be used for documentation in a UI analogous to the `description` parameter of the PIP itself and should contain information for a policy author on how to use the attribute. The methods must have `public` visibility.

Individual attributes take in a number of parameters and the variables of the current evaluation context and return a `Flux<Val>`.
All attributes may take parameters, e.g., `subject.id.<totalSales(2021,"USD")>`. And attributes may have a left-hand input like `subject.id` in the example. Using attributes without a left-hand parameter are called environment attributes, e.g., `<time.now>`. 

```java
@PolicyInformationPoint(name = "sales", description = SalesPolicyInformationPoint.DESCRIPTION)
public class SalesPolicyInformationPoint {
	public static final String DESCRIPTION = "This PIP provides access to the sales data ...";
   
    private final SalesService salesService;
    
    public SalesPolicyInformationPoint(SalesService salesService) {
        this.salesService = salesService;
    }
   
	@Attribute(name = "sales", docs = "Get sales of subject. Usage: subjectId.<sales.sales>")
	public Flux<Val> ethGetLogs(@Text Val subjectId, Map<String, JsonNode> variables) {
		var accessToken = variables.get("salesServiceAccessToken");
		return salesService.sales(subjectId.getText(),accessToken.asText()).map(Val::of);
	}
	
	@Attribute(name = "totalSales", docs = "Return Flux of current accumulated sales of an employee for a given year in a given currency. Usage: subjectId.<sales.totalSales(2021,\"USD\")")
	public Flux<Val> ethGetLogs(@Text Val subjectId, @Int Val year, @Text Val currency, Map<String, JsonNode> variables) {
		var accessToken = variables.get("salesServiceAccessToken");
		return salesService.totalSales(subjectId.getText(), year.get().asInt(), currency.asText(), accessToken.getText()).map(Val::of);
	}
}
```

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
	<version>0.0.1-SNAPSHOT</version>
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
				<version>2.0.0-SNAPSHOT</version>
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