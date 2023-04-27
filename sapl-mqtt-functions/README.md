# SAPL-MQTT-FUNCTIONS

## Overview

Within sapl policies the sapl-mqtt-functions library allows a user to evaluate whether certain mqtt topics are matching a mqtt topic containing wildcards or not.

### Setup

To import all functions of the sapl mqtt functions library use ```import mqtt.functions.*```.

## Functions

### Basic schema

The basic schema to use the sapl mqtt functions is ```isMatchingAtLeastOneTopic(wildcardTopic, topics)```. Here, 'topics' can be a single topic or an array of topics where 'wildcardTopic' is a topic containing a wildcard.

### Function descriptions

The sapl mqtt functions library specifies the functions ```isMatchingAllTopics(wildcardTopic, topics)``` and ```isMatchingAtLeastOneTopic(wildcardTopic, topics)```. The first mentioned function checks whether all given mqtt topics are matching the specified wildcard topic. The latter function checks whether at least one of the given mqtt topics is matching the specified wildcard topic. Are these conditions true then both functions will return a Val containing a positive boolean.

For both functions the first specified parameter needs to be the mqtt topic containing the wildcards. Doing this, it is possible to specify just a single (+) or multi (#) level wildcard or to use both of them. If this topic does not contain any wildcard at all then the functions will check whether the topics are exactly the same or not. For the second parameter the functions are taking a textual mqtt topic or an array of mqtt topics for matching.