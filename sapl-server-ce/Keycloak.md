## Configuring Keycloak

The SAPL Server CE can also be used with its own Keycloak realm. This means that existing users can be used for login.
Keycloak still needs to be set up so that Keycloak can be used for the SAPL Server CE. The following steps show the necessary configurations for using OAuth2 with Keycloak.

In most cases, a Keycloak realm in which the SAPL client is to be created will already exist. If not, then the realm must first be created in Keycloak via 'Create Realm'.

### Create a new realm

The first step is to create a realm if one does not already exist. With existing setups, it is possible that a realm has already been created for other clients that are shared:

![Create the realm](images/1.png)

![Create the realm](images/2.png)

### Create the ADMIN role

Now we can create the realm roles that are necessary so that users can be assigned the "ADMIN" role.

![Adding a role](images/3.png)

![Add the admin role](images/4.png)

### Creating the client and adding the client scope

In the next step, we create the client in the realm, which we use to authenticate ourselves on the realm. We also change the client scope so that the correct attributes are sent when the token is transmitted.

![Create the OAuth2 client](images/5.png)

![Client id and name](images/6.png)

![Client authentication flow](images/7.png)

![Client login settings](images/8.png)

![Checking the client scope](images/9.png)

Under the Credentials tab, we can copy the client secret, which we will need later for the configuration of the SAPL client in application.yml.

![Check the client credentials](images/10.png)

Now we add the ADMIN role to the client scope "roles":

![Adding the role to the client](images/11.png)

![Adding the role to the client](images/12.png)

![Adding the role to the client](images/13.png)

### Creating a new user

We can now create a user in Keycloak for test purposes. We need this user to log in to the SAPL Server CE. We must explicitly set a password and assign the appropriate role to the user:

![Create an OAuth2 user](images/14.png)

![Add credentials for the OAuth2 user](images/15.png)

![Set the password](images/16.png)

![Assign a role to the user](images/17.png)

![Assign a role to the user](images/18.png)

### Display the realm config

There is an overview of the realm configuration in Keycloak. This configuration is important as we need some values in our application.yml:

![View the realm config](images/19.png)

![View the realm config](images/19-1.png)

### Configuring Keycloak for

Now we have to configure the application.yml so that the SAPL Server CE also uses our Keycloak Client for authentication. The configuration looks like this:

```shell
spring.security.oauth2.client:
  registration.keycloak:
    client-id: <Your SAPL client id e.g. sapl-client>
    client-secret: <Your SAPL client secret>
    client-authentication-method: client_secret_basic
    authorization-grant-type: authorization_code
    redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"
    scope: openid, profile, email, roles
    provider: keycloak
  provider.keycloak:
    issuer-uri: <Issuer URI under issuer:>
    user-name-attribute: preferred_username
    jwk-set-uri: <JWK Set URI under jwks_uri:>
    authorization-uri: <Authorization URI under authorization_endpoint:>
    token-uri: <Token URI under token_endpoint:>
    user-info-uri: <User Info URI under userinfo_endpoint:>
```

The last necessary step is to set the parameter `allowOAuth2Login: True` in the application.yml. Now your SAPL Server CE will show an OAuth2 Login page.

### Starting the server

If the SAPL Server CE is now restarted, a new login page appears, which forwards you to the OAuth2 provider. We can now log in with our newly created user:

![SAPL Server CE login page](images/20.png)

![Keycloak login](images/21.png)
![SAPL Server CE overview](images/22.png)

The user session now appears in the Keycloak Realm:

![User session](images/23.png)