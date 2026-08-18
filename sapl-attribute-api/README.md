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

The current version doesn't support an automatic setup. An automatic integration will follow in a later release. To prepare the backend for the API server do the following steps for:

### Redis

1. Please add the following config to your `redis.conf`:

    ```
    notify-keyspace-events Ex
    ```

    This setting is mandatory for to support Time to live (TTL) expiry events on the server. For Redis it is recommended to have a dedicated instance running because the keyspace events will be received by every client that is subscribed to the keyspace events.

**Important**: Please be aware that the backend is using the key prefixes `sapl:attribute:*` and `sapl:changes:*` for its internal communication.

### MongoDB

1. The collection will be created automatically. For larger setup you should increase the performance by adding indices:

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

2. The database needs only one table
    ```sql
    CREATE TABLE attributes (
        pdp_id     TEXT NOT NULL,
        name       TEXT NOT NULL,
        entity     JSONB,
        arguments  JSONB NOT NULL,
        value      JSONB NOT NULL,
        expires_at TIMESTAMPTZ,
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
    volumes:
      - ./init/postgres-init.sql:/docker-entrypoint-initdb.d/init.sql:ro
```

Create the init folder `mkdir init` and create the file `vim init/postgres-init.sql` within the folder. The file should contain the following:

```sql
CREATE TABLE IF NOT EXISTS attributes (
    pdp_id     TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    entity     JSONB,
    arguments  JSONB       NOT NULL DEFAULT '[]',
    value      JSONB       NOT NULL,
    expires_at TIMESTAMPTZ,
    CONSTRAINT attribute_key_and_pdp_id_not_null
        UNIQUE NULLS NOT DISTINCT (pdp_id, name, entity, arguments)
);
```

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
