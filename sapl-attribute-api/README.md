# SAPL Attribute API

SAPL Attribute API is a server that provides a restful HTTP API for the Streaming Attribute Policy Language (SAPL) attribute repository. It supports a multi-tenant setup. 

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later (when building from source)
- One of:
  - PostgreSQL 17+
  - MongoDB 8.0+
  - Redis 7+

## Setup for the backend

Most of the backends require a minimal manual setup. To prepare the backend for the API server do the following steps for:

### Redis

1. Please add the following config to your `redis.conf`:

    ```
    notify-keyspace-events Ex
    ```

    This setting is mandatory for to support Time to live (TTL) expiry events on the server. For Redis it is recommended to have a dedicated instance running because the keyspace events will be received by every client that is subscribed to the keyspace events.

**Important**: Please be aware that the backend is using the key prefixes `sapl:attribute:*` and `sapl:changes:*` for its internal communication.

### MongoDB

1. The collection will be created automatically. For larger setups you should increase the performance by adding indices:

    ```js
    db.attributes.createIndex(
    { pdpId: 1, name: 1, entity: 1, arguments: 1 },
    { unique: true }
    )
    ```

2. Add a replica set config to the `mongod.conf`:
   ```
   replication:
     replSetName: rs0
   ```

### PostgreSQL

1. Create a database e.g. `CREATE DATABASE sapl_attributes;`

2. The database table will be automatically created if missing during startup. If you want to create the table manually, you can use the following statement:

    ```sql
    CREATE TABLE IF NOT EXISTS attributes (
      pdp_id     TEXT        NOT NULL,
      name       TEXT        NOT NULL,
      entity     JSONB,
      arguments  JSONB       NOT NULL DEFAULT '[]',
      value      JSONB       NOT NULL,
      expires_at TIMESTAMPTZ,
      CONSTRAINT attributes_pdp_id_name_entity_arguments_key
          UNIQUE NULLS NOT DISTINCT (pdp_id, name, entity, arguments)
    );
    ```

### Docker

#### Redis

The given `docker-redis.yml` file contains a minimum version to run the api with a Redis attribute store. Adjust the file to your environment needs but make sure that the `notify-keyspace-events` are set to `Ex`:

```yaml
services:
  redis:
    image: redis:8
    command: ["redis-server", "--notify-keyspace-events", "Ex"]
    ports:
      - "6379:6379"
```
`docker compose -f docker-redis.yml up -d`

#### Mongo

The given `docker-mongo.yml` file contains a minimum version to run the api with a MongoDB attribute store. Adjust the file to your environment needs but make sure that the replicat sets are activated:

```yaml
services:
  mongo:
    image: mongo:8.0
    command: ["--replSet", "rs0"]
    ports:
      - "27017:27017"
    healthcheck:
      test: mongosh --eval "try { rs.status() } catch (e) { rs.initiate() }" --quiet
      interval: 5s
      timeout: 5s
      retries: 10
```

`docker compose -f docker-mongo.yml up -d`

#### PostgreSQL

The given `docker-postgres.yml` file contains a minimum version to run the api with a PostgreSQL attribute store. Adjust the file to your environment needs but make sure that the replicat sets are activated:

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: sapl
      POSTGRES_USER: sapl
      POSTGRES_PASSWORD: sapl
    ports:
      - "5432:5432"
```

The table within the database will automatically be created. 

`docker compose -f docker-postgres.yml up -d`

## Building from Source

To build the JAR file from the source execute the following commands

1. Clone the repository

   ```
   git clone https://github.com/heutelbeck/sapl-policy-engine.git
   ```

2. Change the directory

   ```
   cd sapl-policy-engine
   ```

3. Create the JAR file

    ```
    mvn install -pl sapl-attribute-api -am -DskipTests
    ```

4. The executable file is in the `./target/` folder. Plase be aware to have a proper `application.yml` within the working directory. To find a valid configuration, please have a look at the section [Quick Start](#quick-start) or [Server Configuration](#server-configuration)

## Quick Start

Assuming you have set up Redis as a backend (see [Redis](#redis) above) and
the repository is cloned to `/opt/sapl/`, you can start a minimal API server
with the following commands:

```bash
mkdir sapl-attribute-api-server
cd sapl-attribute-api-server
vim application.yml
```

The file should contain:
```yaml
io:
  sapl:
    attribute-api:
      enabled: true
      allow-no-auth: true
    attributes:
      storage: redis
      redis:
        host: localhost
        port: 6379
```
Then start the API server:
```bash
java -jar /opt/sapl/sapl-attribute-api/target/sapl-attribute-api-4.2.0-SNAPSHOT-exec.jar
```

Verify the API server is running:

```bash
curl -X PUT http://localhost:8090/api/attributes/sapl.test.attribute \
  -H "Content-Type: application/json" -d '{"value": "test"}'
```

```bash
curl http://localhost:8090/api/attributes/sapl.test.attribute

"test"
```

See [Usage](#usage) for the full set of publish/get/delete examples and
authentication options.

## Usage

All examples are using the cURL client and need to be adjusted for different clients.

### Authentication

Assuming you have an attribute `sapl.test.attribute` published without an entity. Depending on your set authentication method you can authentication like that:

| Auth method | Example |
|-------------|---------|
| No authentication | `curl http://localhost:8090/api/attributes/sapl.test.attribute` |
| Basic authentication | `curl -u sapl-api-user-01:sapl-api-user-01 -c cookies.txt http://localhost:8090/api/attributes/sapl.test.attribute`|
| API key authentication | `curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" http://localhost:8090/api/attributes/sapl.test.attribute`|
| OIDC auth | `curl -H "Authorization: Bearer <TOKEN>" http://localhost:8090/api/attributes/sapl.test.attribute` |

Please read for basic authentication the additional section of how to use the API with CSRF protection [CSRF Protection](#using-basic-auth-with-csrf).

### Publish an attribute

The following examples are using as default the api key authentication.

Publish an attribute without an entity (global attribute) and TTL. The attribute will never expire:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X PUT \
  http://localhost:8090/api/attributes/sapl.test.attribute \
  -H "Content-Type: application/json" \
  -d '{ "value": "test" }'
```

Publish an attribute for the entity `alice` with a set expiry of 60 seconds:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X PUT \
  http://localhost:8090/api/attributes/alice/sapl.test.attribute \
  -H "Content-Type: application/json" \
  -d '{ "value": "test", "ttl": 60 }'
```

Publish an attribute without an entity and TTL but with additional arguments:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X PUT \
  http://localhost:8090/api/attributes/sapl.test.attribute \
  -H "Content-Type: application/json" \
  -d '{ "value": "test", "arguments": ["dept-x", "region-eu"] }'
```

Publish an attribute with entity, TTL and additional arguments:

```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X PUT \
  http://localhost:8090/api/attributes/alice/sapl.test.attribute \
  -H "Content-Type: application/json" \
  -d '{ "value": "test", "ttl": 60, "arguments": ["dept-x", "region-eu"] }'
```

### Delete an attribute

Deletion of an attribute is similar to publish an attribute. Just the HTTP method is different and it's not necessary to enter the value or TTL.

Delete an attribute without an entity and arguments:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X DELETE \
  http://localhost:8090/api/attributes/sapl.test.attribute
```
Delete an attribute with an entity but without arguments:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X DELETE \
  http://localhost:8090/api/attributes/alice/sapl.test.attribute
```
Delete an attribute without an entity but with arguments. The DELETE requests needs the list of arguments are parameter:

```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X DELETE \
  "http://localhost:8090/api/attributes/sapl.test.attribute?arg=dept-x&arg=region-eu"
```

Delete an attribute with an entity and arguments:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" -X DELETE \
  "http://localhost:8090/api/attributes/alice/sapl.test.attr?arg=dept-x&arg=region-eu"
```

### Get an attribute

To get an attribute you just need to send a HTTP GET and it's also not necessary to enter the value or TTL. You can search for a single attribute or get all attributes for the given pdp id.

Get the value of a single attribute:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" \
  http://localhost:8090/api/attributes/sapl.test.attribute

"test"
```

Get all atributes for the authenticated user associated with a specific pdp id:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" \
  http://localhost:8090/api/attributes | jq

[
  {
    "entity": "alice",
    "name": "sapl.test.attribute",
    "arguments": [
      42,
      "test"
    ],
    "value": "bob"
  },
  {
    "entity": null,
    "name": "sapl.test.attribute",
    "arguments": [],
    "value": "test"
  },
  {
    "entity": null,
    "name": "sapl.test.attribute10",
    "arguments": [],
    "value": "test"
  },
...
```

Count all attributes of the authenticated user associated with a specific pdp id:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" \
  "http://localhost:8090/api/attributes?count=true"

11
```

Using limit and offset by getting two attributes starting with an offset of 2. So the examples shows attribute stored at position 2 and 3:
```bash
curl -H "Authorization: Bearer sapl_a1b2c3_verySecretPartOfTheKey" \
  "http://localhost:8090/api/attributes?limit=2&offset=2" | jq

  [
  {
    "entity": null,
    "name": "sapl.test.attribute10",
    "arguments": [],
    "value": "test"
  },
  {
    "entity": null,
    "name": "sapl.test.attribute2",
    "arguments": [],
    "value": "test"
  }
]

```

### HTTP Status Codes

The API returns the following HTTP status codes:

| Action | Status code | Reason | Description |
|--------|-------------|--------|-------------|
Publish (new key) | 201 | Created | Key didn't exist in the repository |
Publish (overwrite) | 200 | OK | Key was overwritten in the repository |
Publish (invalid request) | 400 | Bad request | The data in the body couldn't be parsed |
Delete (attribute existed) | 204 | No content | The attribute was deleted from the repository |
Delete (attribute didn't exist) | 404 | Not found | The attribute didn't exist in the repository |
Get (single attribute) | 200 | OK | The attribute did exist and the value is returned |
Get (attribute missing) | 404 | Not found | The attribute did not exist in the repository | 
Get all | 200 | OK | Returns always 200. If a repository is empty it returns an empty set [] |
Get all (limit/offset invalid) | 400 | Bad request | The given limit or offset were wrong and leaded to an invalid request. Limit must be > 0 and offset must be >= 0.
Count | 200 | OK | Returns always 200. If a repository is empty it return a count of 0 |
Authentication failed | 401 | Unauthorized | The given credentials were incorrect or the used authentication method is not active |
Basic authentication failed (CSRF) | 403 | Forbidden | Basic authentication required a CSRF cookie/header on state changing requests. API key and OIDC are exempt.

### Using basic auth with CSRF

The basic authentication has cross-site request forgery (CSRF) protection active because a browser automatically caches basic authentication credentials per origin and automatically reattach them to every subsequent request. Similar to a session cookie. A malicious page could therefore trigger a state changing request, like publishing or deleting an attribute, against the API from a victim's browser without ever knowing the actual credentials, relying on the browser sending them automatically. The SAPL attribute api server should also be usable by a web application that implements a graphical interface for the API. So the CSRF is by default activated for basic authentication. 

API key authentication and OIDC authentication are using bearer tokens within the authorization header instead. The authorization header is not attached automatically by a browser because the client applications needs to set it explicitly on every request. Since there a no such ambient credentials to piggyback on, these two authentication modes are exempt from CSRF protection. It's recommended to use one of these two authentication methods.

If you're using the API server with a CLI client like 'curl' and you want to use basic authentication please do the following steps:

1. Obtain the cookie by sending a state changing request to the API server that will fail. Spring security's CSRF filter creates the token lazily, so that a GET request wouldn't be enough:
   ```bash
   curl -s -u sapl-api-user-01:sapl-api-user-01 -c cookies.txt \
              -X PUT "http://localhost:8090/api/attributes/sapl.test.attribute" \
              -H "Content-Type: application/json" \
              -d '{"value": "test"}'
   ```

2. Extract the token value from the XSRF-TOKEN cookie in the file `cookies.txt` by copying it manually or using `awk` to set a variable:
   ```bash
   CSRF=$(awk -F'\t' '$6=="XSRF-TOKEN"{print $7}' cookies.txt)
   ```
   Please be aware that depending on your shell the method to set a variable can differ.

3. Now, you can use the token within the request and read the cookie from the `cookies.txt` file:
    ```bash
    curl -u sapl-api-user-01:sapl-api-user-01 -b cookies.txt \
            -X PUT "http://localhost:8090/api/attributes/sapl.test.attribute" \
            -H "Content-Type: application/json" \
            -H "X-XSRF-TOKEN: $CSRF" \
            -d '{"value": "test"}'
    ```
    Unlike you're using `-c cookies.txt' again the token won't change. For testing purposed this should be enough.

You can also combine all the steps into one step and use:
```bash
touch cookies.txt # If the file doesn't exist
curl -u sapl-api-user-01:sapl-api-user-01 -c cookies.txt -b cookies.txt \
        -X PUT "http://localhost:8090/api/attributes/sapl.test.attribute" \
        -H "Content-Type: application/json" \
        -H "X-XSRF-TOKEN: $(awk -F'\t' '$6=="XSRF-TOKEN"{print $7}' cookies.txt)" \
        -d '{"value": "test"}'
```

## Server Configuration

### Authentication

#### General

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attribute-api.enabled` | boolean | — | Enables the attribute API module (required) |
| `io.sapl.attribute-api.default-pdp-id` | string | `default` | The pdpId that is used when it can't be resolved from the request |

#### No-Auth

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attribute-api.allow-no-auth` | boolean | `false` | Starts the API server without any authentication |

#### Basic Auth

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attribute-api.allow-basic-auth` | boolean | `false` | Enables basic authentication with an username and a password |
| `io.sapl.attribute-api.users[].pdp-id` | string | — | The pdp id of the given user |
| `io.sapl.attribute-api.users[].username` | string | — | The username of the given user |
| `io.sapl.attribute-api.users[].secret` | string | — | The basic auth and argon2 decrypted password of the user |

An example of a single user setup:

```
io:
  sapl:
    attribute-api:
      enabled: true
      allow-basic-auth: true
      users:
        - username: sapl-api-user-01
          secret: "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHQ$aGFzaGhhc2hoYXNoaGFzaA"
          pdp-id: sapl-api-user-01
```

An example of a multi user setup:
```
io:
  sapl:
    attribute-api:
      enabled: true
      allow-basic-auth: true
      users:
        - username: sapl-api-user-01
          secret: "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHQ$aGFzaGhhc2hoYXNoaGFzaA"
          pdp-id: sapl-api-user-01
        - username: sapl-api-user-02
          secret: "$argon2id$v=19$m=19456,t=2,p=1$YW5vdGhlcnNhbHQ$YW5vdGhlcmhhc2g"
          pdp-id: sapl-api-user-02
```

To generate a valid password, there are two ways:

- If you have access to a SAPL node, you can generate a password with the CLI tools. The tools will generate a random password:

    ```
    java -jar sapl-node/target/sapl-node-4.2.0-SNAPSHOT.jar generate basic --id sapl-user-01 --pdp-id sapl-user-01
    ```

- If you want to create a specific password without the SAPL Node, you can use CLI tools of your system. An example for the bash shell is:

    ```
    SALT=$(openssl rand -hex 8)
    echo -n "a-user-password" | argon2 "$SALT" -id -t 2 -m 14 -p 1 -e
    ```

#### API-Key Auth

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attribute-api.allow-api-key-auth` | boolean | `false` | Enables API key authentication |
| `io.sapl.attribute-api.users[].id` | string | — | The identifier for the API key |
| `io.sapl.attribute-api.users[].pdp-id` | string | — | The pdp id for the given API key |
| `io.sapl.attribute-api.users[].api-key-hash` | string | — | The API key as a hash value |

To generate valid API keys, there are two ways:

- If you have access to a SAPL node, you can generate a password with the CLI tools. The tool will show in it's output the user id, the pdp id, the key itself and the api key id.
  ```
  java -jar sapl-node/target/sapl-node-4.2.0-SNAPSHOT.jar generate apikey --id service-api-01 --pdp-id sapl-api-user-01
  ```

  The `User ID` of the output belongs into the property `io.sapl.attribute-api.users[].id`. The `PDP ID` of the output belongs into t he property `io.sapl.attribute-api.users[].pdp-id` and the hash is visible in the example and  belongs into the property `io.sapl.attribute-api.users[].api-key-hash`.

#### OAuth2

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attribute-api.allow-oauth2-auth` | boolean | `false` | |
| `io.sapl.attribute-api.oauth2.oidc-pdp-id-claim` | string | `tenantId` | JWT claim used as pdpId |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | string | *(empty)* | Required when `allow-oauth2-auth=true` |

### Backend

#### General

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attributes.storage` | string | — | `postgres`, `mongo`, `redis`, or `none` (required) |

#### PostgreSQL (`io.sapl.attributes.postgres.*`)

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attributes.postgres.host` | string | `localhost` | The IP, host or domain name of the Postgres host|
| `io.sapl.attributes.postgres.port` | int | `5432` | The port to connect to the Postgres database |
| `io.sapl.attributes.postgres.database` | string | `sapl` | The database name of the attribute store|
| `io.sapl.attributes.postgres.username` | string | `sapl` | The Postgres user to connect to the given database|
| `io.sapl.attributes.postgres.password` | string | — | The password for the Postgres database user |
| `io.sapl.attributes.postgres.table-name` | string | `attributes` | The table name for the attributes schema |

#### MongoDB (`io.sapl.attributes.mongo.*`)

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attributes.mongo.host` | string | `localhost` | The IP, host or domain name of the MongoDB host |
| `io.sapl.attributes.mongo.port` | int | `27017` | The port to connect to the Mongo database|
| `io.sapl.attributes.mongo.database` | string | `sapl` | The database name of the attribute store |
| `io.sapl.attributes.mongo.username` | string | — | The MongoDB user to connect to the given database |
| `io.sapl.attributes.mongo.password` | string | — | The MongoDB user's password to connect to the given database |
| `io.sapl.attributes.mongo.auth-database` | string | `admin` | The name of the auth database to find the given user |
| `io.sapl.attributes.mongo.collection-name` | string | `attributes` | The name of the collection within the MongoDB|

#### Redis (`io.sapl.attributes.redis.*`)

| Property | Type | Default | Description |
|----------|------|---------|--------------|
| `io.sapl.attributes.redis.host` | string | `localhost` | The IP, host or domain name of the Redis host |
| `io.sapl.attributes.redis.port` | int | `6379` | The port to connect to the Redis store |
| `io.sapl.attributes.redis.password` | string | — | The password to connect to the Redis store|
| `io.sapl.attributes.redis.database` | int | `0` | The id of the Redis database |
