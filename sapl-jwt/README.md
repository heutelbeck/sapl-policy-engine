# SAPL Demo OAuth 2.0 and JWT

This demo shows how to use JSON Web Tokens with OAuth 2.0 in tandem with SAPL. 

Here, SAPL is primarily applied in the resource server. The resource server 
embeds the JWT as an attribute of the subject in the authorization subscription 
sent to the PDP. In this case the resource server uses an embedded PDP.

The PDP makes use of a dedicated JWT function library and policy information point.

To be able to use the resource server an authorization server and a client 
application are required.

These are the two other modules of the demo. They are not using SAPL and are only 
used to demonstrate the resource server in action.

## Running the demo

You have to open three terminals. One for each of the three applications to run.

1. Change your systems host file by adding an alias of `auth-server` for localhost, i.e. `127.0.0.1`.
   * For Windows 10/11 the host file is located in `C:\Windows\System32\drivers\etc\hosts` . 
   * For Linux the host file is located in `/etc/hosts` .
   * Add the following line to the end of the file:
   ```
   127.0.0.1 auth-server
   ```

2. Run the authorization server:
   1. Open a new terminal.
   2. Change to the `sapl-demos\sapl-demo-jwt\sapl-demo-oauth2-jwt-authorization-server`folder.
   3. Run: `mvn spring-boot:run` .
   
3. Run the resource server. It is important that this happens after the authorization server started, 
   as the resource server contacts the authorization server on startup:
   1. Open a new terminal.
   2. Change to the `sapl-demos\sapl-demo-jwt\sapl-demo-oauth2-jwt-resource-server`folder.
   3. Run: `mvn spring-boot:run` .

4. Run the client application:
   1. Open a new terminal.
   2. Change to the `sapl-demos\sapl-demo-jwt\sapl-demo-oauth2-jwt-client-application`folder.
   3. Run: `mvn spring-boot:run` .

5. To access the client application, go to <http://localhost:8080>. 
   The default user name is `user1` and the password is `password`.

# Acknowledgement

The demo is a derivate of the sample projects of the [Spring Authorization Server](https://github.com/spring-projects/spring-authorization-server).


