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

The first parameter selects QoS 0, 1 or 2. The second selects an operator-configured broker by name, for example `"plant"`.

### Return type

The attribute finder will not return the message as it was transmitted. Normally it will try to return the message payload as text for evaluation purposes in the policy. If the content type of the transmitted mqtt message specifies the MIME type "json" the return type of the attribute finder will be a json object. Binary payloads are rejected with an error value. Publish text, JSON, or encode binary data explicitly before publishing.

### Default response

If the pip subscribes to a topic at a mqtt broker and there is no message transmitted within a specified amount of time after initial subscription start then the sapl mqtt pip will emit a default response. This response and the timeout duration is configurable through the PDP configuration file.

### Connection loss

In case the sapl mqtt pip loses the connection to the mqtt broker, it will automatically try to reconnect according to the reconnect strategy specified in the pdp configuration file. Normally, on connection loss the pip will also return an undefined value, but it is possible to disable this functionality.

## Configure TLS trust

The MQTT PIP reads its endpoint from the public PDP configuration. For a private CA, start with the following `pdp.json` settings and install the referenced profile before deployment. Multiple endpoints and PIPs can reference the same profile.

```json
{
  "variables": {
    "mqtt": {
      "default": "plant",
      "brokers": {
        "plant": {
          "brokerAddress": "mqtt.example.org",
          "brokerPort": 8883,
          "tls": true,
          "trustProfile": "site-ca"
        }
      }
    }
  }
}
```

Select the broker with `"factory/door".<mqtt.messages(1, "plant")>`. Without a name, the PIP selects the configured `default`, or the broker named `default` if the setting is absent. An unknown broker is an error. Set a TLS port appropriate for your broker.

For password authentication, put this entry in the operator secrets and supply the actual credentials. A broker that allows anonymous access needs no credential entry.

```json
{
  "mqtt": {
    "plant": {
      "username": "door-reader",
      "password": "REPLACE_WITH_PASSWORD"
    }
  }
}
```

The operator secrets file is `secrets.json` for explicitly enabled cleartext development or `secrets.sealed.json` for sealed deployment. Broker and server credentials are selected only by their configured name.

### Obtain and verify the CA certificate

Ask the administrator of the MQTT broker for its public CA certificate and confirm its SHA-256 fingerprint through your organization's PKI or another authenticated channel. A PEM file contains a `-----BEGIN CERTIFICATE-----` block. If the administrator provides a binary DER certificate, convert it first.

```sh
openssl x509 -inform DER -in issuer.cer -out ca.pem
```

If the CA is already in an approved Java truststore, export its trusted-certificate alias. `keytool` prompts for the store password.

```sh
keytool -exportcert -rfc -keystore truststore.p12 -alias issuing-ca -file ca.pem
```

Check the identity and fingerprint of each certificate before installing it.

```sh
openssl x509 -in ca.pem -noout -subject -issuer -fingerprint -sha256
```

### Install the named profile

In the following command, `pdp.input.json` is the complete configuration with the endpoint settings above. `jq --rawfile` reads the public certificate including its PEM delimiters and line breaks. The resulting `pdp.json` keeps the other configuration fields.

```sh
jq --rawfile ca ca.pem '.variables.tlsTrustProfiles["site-ca"] = {trustedCaPem: $ca, includeDefaultTrustRoots: false}' pdp.input.json > pdp.json
```

| Endpoint setting | Trusted roots |
| --- | --- |
| Omit `trustProfile` | Runtime defaults. |
| Select a profile with `includeDefaultTrustRoots` absent or `false` | Only the CA certificates in that profile. |
| Select a profile with `includeDefaultTrustRoots: true` | Profile certificates and runtime defaults. |

A named profile requires a nonempty `trustedCaPem` string. Each PEM block must contain a public X.509 certificate. The Java client uses the JVM trust configuration by default. The certificate must match the server's hostname. An invalid bundle, an unknown profile, or a profile on a plaintext endpoint prevents the new PDP configuration from taking effect.

For a CA rollover, deploy the old and new CA certificates together as consecutive PEM blocks in the profile. After the service switches certificates, remove the retired CA and deploy the configuration again.

## Broker options

In the 'mqtt' object you can optionally set different values for environment attributes specified in the mqtt pip. The following attribute are possible:



- ```defaultQos```: You can define the quality of service level used in case no quality of service level is specified as a parameter of an attribute finder. If nothing is stated the default quality of service level will be 0.
- ```timeoutDuration```: When the timeout duration specified in milliseconds is reached the default response will be sent. If nothing is stated the default timeout duration will be 2000 milliseconds.
- ```defaultResponse```: The default response can be specified with "undefined" or "error" so that the default response will be an error value or an undefined value. If nothing is specified it will be undefined.
- ```emitAtRetry```: Specifies a boolean value of whether an undefined value is returned from the attribute finder if the pip loses the connection to the mqtt broker or not. Per default, it is set to true.
- ```maxTopicFilters```: Maximum number of topic filters accepted per subscription. If nothing is specified the value will be set to 32.
- ```maxTopicFilterBytes```: Maximum total UTF-8 bytes across all topic filters accepted per subscription. If nothing is specified the value will be set to 8192.
- ```errorRetryAttempts```: Specifies the maximum number of retry attempts on connection loss of the sapl mqtt pip to the mqtt broker. If nothing is specified the value will be set to 10000000.
- ```minErrorRetryDelay```: When the sapl mqtt pip loses connection to the mqtt broker it will automatically try to reestablish the connection. With each retry attempt the duration between the retries gets exponentially prolonged. This parameter specifies the minimal interval in milliseconds and is set to 5000 milliseconds per default.
- ```maxErrorRetryDelay```: When the sapl mqtt pip loses connection to the mqtt broker it will automatically try to reestablish the connection. With each retry attempt the duration between the retries gets exponentially prolonged. This parameter specifies the maximal interval in milliseconds and is set to 10000 milliseconds per default.

Generally, when there are no parameters set for the different attributes in the pdp configuration file the default values specified in the mqtt pip will be used.
