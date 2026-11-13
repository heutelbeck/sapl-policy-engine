# Mongo Attribute Repository Extension

## Overview

The Mongo Attribute Repository Extension support a MongoDB database as an attribute repository. 

## Setup

The collection and the pre- and post-images for the collection will be created automatically. The following step needs to be done manually:

1. Add a replica set config to the `mongod.conf`:
    ```
    replication:
      replSetName: rs0
    ```

The other two steps are optional but should be considered depending on your environment:

1. For larger setups you should increase the performance by adding indices:

    ```js
    db.attributes.createIndex(
    { pdpId: 1, name: 1, entity: 1, arguments: 1 },
    { unique: true }
    )
    ```

2. You can set an expiry for pre- and post-images. This setting is cluster-wide, not per collection.  If the cluster is shared with other application that also rely on change stream pre- and post-images coordinate this value with them before changing it.
  
   The attribute repository for Mongo only needs a short period of pre- and post-images. A shorter period keeps the used space on disk low. You can set it within `mongosh`:
   ```js
   db.adminCommand({ setClusterParameter: { changeStreamOptions: { preAndPostImages: { expireAfterSeconds: 120 } } } })
   ```

## Configuration

### Examples