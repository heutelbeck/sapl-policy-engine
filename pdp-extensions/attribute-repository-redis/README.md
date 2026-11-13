# Redis Attribute Repository Extension

## Overview

The Redis Attribute Repository Extension support a Redis database as an attribute repository. 

## Setup

1. Please add the following config to your `redis.conf`:

    ```
    notify-keyspace-events Ex
    ```

    This setting is mandatory for to support Time to live (TTL) expiry events on the server. For Redis it is recommended to have a dedicated instance running because the keyspace events will be received by every client that is subscribed to the keyspace events.

**Important**: Please be aware that the backend is using the key prefixes `sapl:attribute:*` and `sapl:changes:*` for its internal communication.

## Configuration

### Examples