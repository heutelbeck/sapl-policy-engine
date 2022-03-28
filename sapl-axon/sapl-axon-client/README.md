# SAPL Axon Client Integration
This package provides integration into the Axon Client. It provides a SAPL specific QueryGateway to establish the communication with the Policy Enforcement Point within the QueryBus. 
Additionally, it provides CommandInterceptor and QueryInterceptor to allow the correct setting of the subject for the AuthorizationSubscription.

## Configuration
### SAPL Query Gateway
To configure the usage of the SAPL Query Gateway in the Axon Configuration a Bean must be added to represent the SAPL QueryGateway. 
The SAPL Query Gateway needs a SAPL Query Bus or AxonServerBus on the server side of the Axon configuration to allow the correct usage of the SAPL annotations.

Configuration of SAPL QueryGateway and SAPL QueryBus

```java
@Bean
@Profile("client")
public SaplQueryGateway registerQueryGateWay(QueryBus queryBus) {
	return SaplQueryGateway.builder().queryBus(queryBus).build();
}

@Bean
@Profile("backend")
public QueryBus registerQueryBus(SaplQueryUpdateEmitter queryUpdateEmitter) {
	return SaplQueryBus.builder().queryUpdateEmitter(queryUpdateEmitter).build();
}
```

### Setting Subject Information in Message Metadata
#### Default Settings
In case Spring Security is used the default settings from the integration can be used to set the authentication of the SecurityContext as a subject to the query and command messages. This allows the usage of the authentication information in the policy definition.
To use the default interceptor to map the security context in the metadata the following configuration is needed.

```java
@Profile("client")
@Autowired
public void registerQueryDispatchInterceptor(QueryBus queryBus) {
	queryBus.registerDispatchInterceptor(new DefaultSaplQueryInterceptor(mapper()));
}
        
@Profile("client")
@Autowired
public void registerCommandDispatchInterceptors(CommandBus commandBus) {
	commandBus.registerDispatchInterceptor(new DefaultSaplCommandInterceptor(mapper()));
}
```

#### Custom Settings
If another security context is used or the subject information should be taken from another location it is possible to allow custom interceptors to be defined and to set the subject in the metadata differently. For this the abstract implementation of `AbstractSaplCommandInterceptor` and `AbstractSaplQueryInterceptor` can be extended. In this custom interceptor the method `getSubjectMetadata` needs to be implemented to return a Map of "subject" and the object which should be set in the authorization subscription.
To enable Axon the usage of these custom interceptors, these needs to configured similarly to the default interceptors mentioned above.


