# Redis Attribute Repository Extension

## Overview

The Redis attribute repository extension stores the attributes of a PDP in a Redis database. All SAPL Node or PDP instances that share the database see the same attributes and are notified about changes via Redis Pub/Sub. It is intended to be used with the Attribute API Server, either embedded in the SAPL Node or as a standalone server.

## Setup

### Mandatory steps

1. Enable keyspace events for expired keys in the `redis.conf`:

    ```
    notify-keyspace-events Ex
    ```

    The extension checks this setting with `CONFIG GET` on startup and fails if it is missing.

### Recommended steps

1. Enable persistence in the `redis.conf`. Otherwise all attributes are lost when Redis restarts:
   
   ```
   appendonly yes
   appendfsync everysec
   ```

**Important**: The extension uses the key prefixes `sapl:attribute:*`, `sapl:changes:*` and `sapl:batch:*`.

## Configuration

1. Include the extension into the `pom.xml` of the PDP. The SAPL Node has it already included.
   ```xml
   <dependency>
     <groupId>io.sapl</groupId>
     <artifactId>attribute-repository-redis</artifactId>
     <version>4.2.0-SNAPSHOT</version>
   </dependency>
   ```

2. Place an `ext-attributerepository.json` in the folder of the PDP. Examples can be found in the section [Examples](#examples).

    | Field | Required | Default | Description |
    |-------|----------|---------|-------------|
    | `type` | yes | | Must be `redis` for this extension |
    | `host` | yes | | Hostname of the Redis server |
    | `port` | yes | | Port of the Redis server |
    | `password` | no | | Password, ignored if blank |
    | `database` | no | `0` | Redis database index |

### Examples

#### Extension

A minimal configuration:

**`ext-attributerepository.json`**
```json
{
    "type": "redis",
    "host": "localhost",
    "port": 6379
}
```

A full and recommended configuration. The password is stored in the secrets file below.

**`ext-attributerepository.json`**
```json
{
    "type": "redis",
    "host": "localhost",
    "port": 6379,
    "database": 1
}
```

Secrets should be sealed. You can find more here: [PDP Configuration](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/2_2_PDPConfiguration.md).

A cleartext secrets file is only for development use. You need to set `io.sapl.pdp.embedded.secrets.accept-unencrypted` to `true`. Also see: [Spring integration](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/6_3_Spring.md). A cleartext example is given here:

**`ext-attributerepository-secrets.json`**
```json
{
    "password": "mySecret"
}
```

#### Docker

The official Redis image can be used:

```bash
docker run -d --name sapl-redis -p 6379:6379 redis:8 \
  redis-server --notify-keyspace-events Ex --appendonly yes --appendfsync everysec
```

The example uses no password. For production environments add authentication, see [Redis security](https://redis.io/docs/latest/operate/oss_and_stack/management/security/).