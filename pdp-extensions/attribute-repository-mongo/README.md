# Mongo Attribute Repository Extension

## Overview

The Mongo attribute repository extension stores the attributes of a PDP in a MongoDB database. All SAPL Node or PDP instances that share the database see the same attributes and are notified about changes via Change Streams. It is intended to be used with the Attribute API Server, either embedded in the SAPL Node or as a standalone server.

## Setup

The database must be prepared before its first use. The setup is separated into mandatory and optional steps. The PDP ignores expired attributes and deletes them from the Collection when their TTL expires while it is running. The TTL index also deletes attributes that expire while no PDP is running.

### Mandatory steps

Add a replica set config to the `mongod.conf`:

**mongod.conf**:
```yaml
replication:
  replSetName: rs0
```

Restart MongoDB and initiate the replica set once within `mongosh` with `rs.initiate()`.

### Optional steps

The following steps are optional but should be considered depending on the environment.

1. The indexes are created automatically on startup. If you want to create them manually, you can use the following statements within `mongosh`. Replace `attributes` with your `collectionName` if you changed it.

    ```js
    db.attributes.createIndex(
      { pdpId: 1, name: 1, entity: 1, arguments: 1 },
      { unique: true }
    )
    db.attributes.createIndex(
      { expiresAt: 1 },
      { expireAfterSeconds: 0 }
    )
    ```

    The unique index prevents duplicate attributes when several instances write the same attribute at the same time. The TTL index deletes expired attributes, even if no PDP is running.

2. You can set an expiry for pre- and post-images (MongoDB 6.0 or later, requires admin privileges). This setting is cluster-wide, not per collection. If the cluster is shared with other applications that also rely on change stream pre- and post-images, coordinate this value with them before changing it.

    The extension only needs pre- and post-images for a short period. A shorter period keeps the used
    disk space low. You can set it within `mongosh`:

    ```js
    db.adminCommand({ setClusterParameter: { changeStreamOptions: { preAndPostImages: { expireAfterSeconds: 120 } } } })
    ```


## Configuration

1. Include the extension into the `pom.xml` of the PDP. The SAPL Node has it already included.
   ```xml
   <dependency>
     <groupId>io.sapl</groupId>
     <artifactId>attribute-repository-mongo</artifactId>
     <version>4.2.0-SNAPSHOT</version>
   </dependency>
   ```

2. Place an `ext-attributerepository.json` in the folder of the PDP. Examples can be found in the section [Examples](#examples).

    | Field | Required | Default | Description |
    |-------|----------|---------|-------------|
    | `type` | yes | | Must be `mongo` for this extension |
    | `host` | yes | | Hostname of the MongoDB server |
    | `port` | yes | | Port of the MongoDB server |
    | `username` | no | | User for authentication. |
    | `password` | no | | Password of the user. Only mandatory when user is set. |
    | `database` | yes | | Database that stores the attributes |
    | `authDatabase` | no | | Database that authenticates the user, Only used with `username`. If not set, `database` is used as default. |
    | `collectionName` | no | `attributes` | Collection name, must match `[A-Za-z_][A-Za-z0-9_]*` |

### Examples

#### Extension

A minimal configuration:

**`ext-attributerepository.json`**
```json
{
    "type": "mongo",
    "host": "localhost",
    "port": 27017,
    "database": "sapl"
}
```

A full and recommended configuration. The credentials are stored in the secrets file below.

**`ext-attributerepository.json`**
```json
{
    "type": "mongo",
    "host": "localhost",
    "port": 27017,
    "database": "sapl",
    "authDatabase": "admin",
    "collectionName": "attributes"
}
```

Secrets should be sealed. You can find more here: [PDP Configuration](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/2_2_PDPConfiguration.md).

A cleartext secrets file is only for development use. You need to set `io.sapl.pdp.embedded.secrets.accept-unencrypted` to `true`. Also see: [Spring integration](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/6_3_Spring.md). A cleartext example is given here:

**`ext-attributerepository-secrets.json`**
```json
{
    "username": "sapl",
    "password": "mySecret"
}
```

#### Docker

The official MongoDB image can be used. The extension needs a replica set for change streams. Start `mongod` with a replica set and initiate it once:

```bash
docker run -d --name sapl-mongo -p 27017:27017 mongo:8 --replSet rs0 --bind_ip_all
docker exec sapl-mongo mongosh --quiet --eval 'rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "localhost:27017" }] })'
```

The host in the members block must be reachable by the PDP because the MongoDB driver connects to the address the replica set announces.

The example is using no authentication. For production environments, please add authentication to MongoDB. See: [Deploy a Replica Set with Keyfile Authentication](https://www.mongodb.com/docs/manual/tutorial/deploy-replica-set-with-keyfile-access-control/).