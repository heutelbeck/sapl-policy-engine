
# SAPL Spring Security Integration

This package provides integration into Spring Security. It provides a number of infrastructure Beans to establish Policy Enforcement Points within a an application.

## Dependencies

To use this package two dependencies must be declared. First, the this package, and second a Policy Decision Point implementation has to be selected.

```xml

  <!-- Import the SAPL 'Bill Of Materials', or BOM, package. This takes care of setting consistent versions for all SAPL Dependencies. -->
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
        <!-- This is the package for integrating directly with Spring Security mechanisms -->
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-security</artifactId>
      </dependency>
 
     <!-- Select a Policy Decision Point (PDP) implementation. Select either an embedded or a remote PDP. 
          You must not declare both dependencies at the same time. -->
     <dependency>
        <!-- Select this package to bundle an embedded PDP with you application, loading policies 
             from the file-system, or from the resources packaged with your application. 
             This is a good PDP to start with for your first SAPL application as it does not 
             require a dedicated PDP server running in the infrastructure -->
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-pdp-embedded</artifactId>
      </dependency>
     <dependency>
        <!-- Select this package to use a dedicated remote PDP, e.g., sapl-server-lt.  -->
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-pdp-remote</artifactId>
      </dependency>
  </dependencies>

  <!-- If you are using a SNAPSHOT version, pleas add the snapshot repository. Else, this can be omitted. -->
  <repositories>
    <repository>
      <id>ossrh</id>
      <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>
```

## Configuration

### PDP

The package provides auto configuration for the PDP. Depending on the selected PDP implementation, folders, host, credentials can be configured using the matching properties.

TODO: Document configuration

### Method Security

Similar to Spring Security itself, the SAPL integration supports both blocking and reactive method security annotations. These mechanisms hook directly into the respective Spring Security classes and have to be configured accordingly. 

Just like in Spring Security, method security is not active by default and one of the two modes has to be activated. While in Spring security it is sufficient to add the annotations `@EnableGlobalMethodSecurity` or `@EnableReactiveMethodSecurity` on any configuration class, a specific configuration class has to be provided to activate the SPAL Annotations alongside the Spring Security method security annotations.

Important note: If Spring Security annotations like `@PreAuthorize` and `@PostAuthorize` are applied to the same method as SAPL Annotations like `@PreEnforce`, `@PostEnforce`, `@Enforce` only one type of annotation will be applied. Currently legacy annotations override SPAL annotations. However, the SAPL libraries make no guarantees that this will be maintained. Thus, these types of annotations must not be used on the same method. This also holds true for annotations which are applicable to a method through class annotations or inheritance. 


#### Non-Reactive/Blocking Method Security PEP

To activate blocking Spring Security annotations and the SAPL annotations `@PreEnforce`and `@PostEnforce` add the following configuration class to your application. If your application would do customization to the Spring `GlobalMethodSecurityConfiguration` by sub-classing it, use this class as the parent class instead.

```java
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;

@Configuration
@EnableGlobalMethodSecurity
public class MethodSecurityConfiguration extends SaplMethodSecurityConfiguration {

	public MethodSecurityAutoConfiguration(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintHandlerService> constraintHandlerFactory,
			ObjectFactory<ObjectMapper> objectMapperFactory) {
		super(pdpFactory, constraintHandlerFactory, objectMapperFactory);
	}

}
```

#### Reactive Method Security PEP

To activate reactive Spring Security annotations and the SAPL ..l add the following configuration class to your application.

TODO

### Filter

### Voter

## Reactive Webflux Integration

#### Method Security




