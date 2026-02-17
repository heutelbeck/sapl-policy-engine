# SAPL-MQTT-PIP

## Overview

The sapl mqtt policy information point allows the user to subscribe to mqtt topics via attribute finders 
in sapl policies. The returned mqtt messages respectively more exactly the returned message payloads can be used to evaluate the policies.

### Setup

To import the whole sapl mqtt pip attribute finder library use ```import mqtt.*```.

## Attribute finders

### Basic scheme

The basic scheme to use the sapl mqtt attribute finders is ```topics.<messages>```. Here, 'topics' can be a single topic or an array of topics where 'messages' is an attribute finder without parameters. The configuration of this pip especially for connecting to the mqtt broker should take place in the pdp configuration file "pdp.json".

### Attribute finder parameters

The attribute finder 'messages' is overloaded. Further possible types are ```topics.<mqtt.messages(qos)>``` and
```topics.<mqtt.messages(qos, config)>```.

The first parameter 'qos' stands for a mqtt quality of service level in the range of 0 to 2. The second parameter 'config' allows to configure the connection to a mqtt broker in the policy. For example, you can specify: ```{"brokerAddress": "localhost", "brokerPort": 1883, "clientId": mqtt_pip, "timeoutDuration": 5000}```. Alternatively you can set a certain broker configuration for this attribute finder specified in the pdp configuration file just by indicating the name, for example ```"broker1"```. Generally, configurations not specified in the 'config' will be looked up the PDP configuration file. If the necessary configuration is not specified there the pip default configuration will be used.

### Return type

The attribute finder will not return the message as it was transmitted. Normally it will try to return the message payload as text for evaluation purposes in the policy. If the content type of the transmitted mqtt message specifies the MIME type "json" the return type of the attribute finder will be a json object. If the payload format indicator is unspecified or the indicator is not specified and the payload contains non-valid UTF-8 characters at the same time, the return type will be an array of bytes specified as integers. Keep this in mind when you subscribe to mqtt messages.

### Default response

If the pip subscribes to a topic at a mqtt broker and there is no message transmitted within a specified amount of time after initial subscription start then the sapl mqtt pip will emit a default response. This response and the timeout duration is configurable through the PDP configuration file.

### Connection loss

In case the sapl mqtt pip loses the connection to the mqtt broker, it will automatically try to reconnect according to the reconnect strategy specified in the pdp configuration file. Normally, on connection loss the pip will also return an undefined value, but it is possible to disable this functionality.

## Config via pdp configuration file

In the pdp.json file you can override the default configurations and set different parameters for broker connections.
A configuration can look like the following example:

```
{
    "algorithm": {
        "votingMode": "PRIORITY_DENY",
        "defaultDecision": "DENY",
        "errorHandling": "PROPAGATE"
    },
    "variables": {
        "mqttPipConfig": {
            "defaultResponse" : "undefined",
            "defaultBrokerConfig": "production",
            "brokerConfig": [
                {
                    "name": "production",
                    "brokerAddress": "localhost",
                    "brokerPort": 1883,
                    "clientId": "mqttPipDefault",
                    "username": "",
                    "password": ""
                },
                {
                    "name": "test",
                    "brokerAddress": "localhost",
                    "brokerPort": 1883,
                    "clientId": "mqttPipTest",
                    "username": "",
                    "password": ""
                }
            ]
        }
    }
}
```
    
In the 'mqttPipConfig' object you can optionally set different values for environment attributes specified in the mqtt pip. The following attribute are possible:



- ```brokerConfig"```:  You can specify different properties of different connections to brokers as an array. Therefore, use the attributes ```name```, ```brokerAddress```, ```brokerPort```, ```clientId```, ```username```, ```password```, ```defaultResponse``` and ```timeoutDuration```. Also, you can just set one single broker connection as an object. In case an array is specified you can use the ```name``` attribute to reference a connection setting via an attribute finder parameter.
- ```defaultBrokerConfigName```: This attribute is used to choose which broker configuration is used per default when no configuration is set via the attribute finder and the ```brokerConfig"``` attribute is an array. Per default, a broker configuration named "default" is tried to be used.
- ```defaultQos```: You can define the quality of service level used in case no quality of service level is specified as a parameter of an attribute finder. If nothing is stated the default quality of service level will be 0.
- ```timeoutDuration```: When the timeout duration specified in milliseconds is reached the default response will be sent. If nothing is stated the default timeout duration will be 2000 milliseconds.
- ```defaultResponse```: The default response can be specified with "undefined" or "error" so that the default response will be an error value or an undefined value. If nothing is specified it will be undefined.
- ```emitAtRetry```: Specifies a boolean value of whether an undefined value is returned from the attribute finder if the pip loses the connection to the mqtt broker or not. Per default, it is set to true.
- ```errorRetryAttempts```: Specifies the maximum number of retry attempts on connection loss of the sapl mqtt pip to the mqtt broker. If nothing is specified the value will be set to 10000000.
- ```minErrorRetryDelay```: When the sapl mqtt pip loses connection to the mqtt broker it will automatically try to reestablish the connection. With each retry attempt the duration between the retries gets exponentially prolonged. This parameter specifies the minimal interval in milliseconds and is set to 5000 milliseconds per default.
- ```maxErrorRetryDelay```: When the sapl mqtt pip loses connection to the mqtt broker it will automatically try to reestablish the connection. With each retry attempt the duration between the retries gets exponentially prolonged. This parameter specifies the maximal interval in milliseconds and is set to 10000 milliseconds per default.

Generally, when there are no parameters set for the different attributes in the pdp configuration file the default values specified in the mqtt pip will be used.