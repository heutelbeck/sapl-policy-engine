# SAPL-GEO

## Overview

This project provides a PIP which provides information from geo-trackers (Traccar and OwnTracks), Databases (PostGIS and MySQL). 
Also, there is a function library containing geospatial operations like "within" or "touches". Finally, there's a converter-library which can be used to convert geometries to a different notation.

### Setup

To use the pip and the libraries, add them to the PDP:

```java
var pdp = PolicyDecisionPointBuilder.withDefaults(objectMapper, clock)
    .withFunctionLibrary(GeographicFunctionLibrary.class)
    .withFunctionLibrary(GeoConverterLibrary.class)
    .withPolicyInformationPoint(new GeoPolicyInformationPoint(objectMapper))
    .withPolicyInformationPoint(new PostGisPolicyInformationPoint(objectMapper))
    .withPolicyInformationPoint(new MySqlPolicyInformationPoint(objectMapper))
    .build()
    .pdp();
```
## Policy Information Point

### Traccar

## Configure TLS trust

The Traccar PIP reads its endpoint from the public PDP configuration. For a private CA, start with the following `pdp.json` settings and install the referenced profile before deployment. Multiple endpoints and PIPs can reference the same profile.

```json
{
  "variables": {
    "traccar": {
      "default": "depot",
      "servers": {
        "depot": {
          "baseUrl": "https://fleet.example.org",
          "trustProfile": "site-ca"
        }
      }
    }
  }
}
```

Read the named server with `<traccar.devices("depot")>`. Without a name, the PIP selects the configured `default`, or the server named `default` if the setting is absent. An unknown server is an error.

The operator supplies a token or a `userName` and `password` pair under the exact server name. Replace the sample token with a Traccar API token.

```json
{
  "traccar": {
    "depot": {
      "token": "REPLACE_WITH_TRACCAR_TOKEN"
    }
  }
}
```

The operator secrets file is `secrets.json` for explicitly enabled cleartext development or `secrets.sealed.json` for sealed deployment. Broker and server credentials are selected only by their configured name.

### Obtain and verify the CA certificate

Ask the administrator of the Traccar server or its HTTPS reverse proxy for its public CA certificate and confirm its SHA-256 fingerprint through your organization's PKI or another authenticated channel. A PEM file contains a `-----BEGIN CERTIFICATE-----` block. If the administrator provides a binary DER certificate, convert it first.

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

### OwnTracks

The PIP connects to the api of the OwnTracks-recorder and polls. As OwnTracks has no built-in authentication, the recorder is usually hosted by a webserver and protected by http basic auth.

#### Example policy
```
permit
  var response = <geo.ownTracks({"httpUser":"httpUser", "password":"test123", "user":"deviceUser", "server":"owntracks.somewhere/owntracks", "protocol":"http", "responseFormat":"GEOJSON", "deviceId":1})>;
  "home" in response.geoFences..name;
```

#### Parameters

* "httpUser": Username vor http basic auth (optional)
* "password": the password of the httpUser (optional)
* "user": the OwnTracks device-user
* "server": the ip/dns-name of the traccar server
* "protocol": "http"/"https". (default is "http")
* "responseFormat": Possible values: "GEOJSON", "WKT", "GML", "KML" (default is "GEOJSON" which is reccomended as the GeoFunction-library needs GeoJson as parameters)
* "deviceId": the id of the device (int)
* "latitudeFirst": true: latitude is first coordinate of geometries, false: longitude is first (Default is true)


#### Response

```json
{
	"deviceId":1,
	"position":{"type":"Point","coordinates":[38.6304265,22.8517104],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}},
	"altitude":409.0,"lastUpdate":"1714043759",
	"accuracy":100.0,
	"geoFences":[
		{"name":"home"},
		{"name":"home2"}
	]
}
```

As you can see OwnTracks delivers its coordinates in WGS84 (EPSG:4326). It provides all geofences the device is inside, but only their name.

### PostGIS/MySQL

#### Parameters
Authentication:
* "user": the PostGIS/MySQL user
* "password": the password of the user (optional)
* "server": name/ip of the database server
* "port": the port of the database server (int, Default is 5432 for PostGIS and 3306 for MySQL)
* "database": the name of the database
  
Query:
* "table": the name of the table
* "geoColumn": the name of the column containing the geometries
* "defaultCRS": if you don't have a SRID specified in the database you can set the coordinate reference system for the response (int Default is 4326 for WGS84, EPSG:4326); otherwise the one from the database is used 
* "responseFormat": Possible values: "GEOJSON", "WKT", "GML", "KML" (default is "GEOJSON" which is reccomended as the GeoFunction-library needs GeoJson as parameters)
* "where": a where clause in sql, optional
* "columns": additional columns to select (Array, ["column_1", "column_2",..., "column_x"], optional
* "singleResult": if you expect only one result, set it to true to get the result without beeing wrapped in an array (boolean, Default is false)
* "latitudeFirst": true: latitude is first coordinate of geometries, false: longitude is first (Default is true)
* "pollingIntervalMs": the interval to poll from the database in ms (Default is 1000)
* "repetitions": the count of repetitions (Default is Long.MAX_VALUE)

#### Example policy
```
policy "postgis"
permit
  var position = <postGis.geometry({"user":"postgres", "password":"anotherPassword", "server":"localhost", "dataBase":"MyDatabase"}, {"table":"position", "geoColumn":"geom", "defaultCRS": 3857, "responseFormat":"GEOJSON", "singleResult": true, "where": "name = 'position1'"})>;
  var geofences = <postGis.geometry({"user":"postgres", "password":"anotherPassword", "server":"localhost", "dataBase":"MyDatabase"}, "table":"geofences", "geoColumn":"geom", "defaultCRS": 3857, "responseFormat":"GEOJSON", "columns": ["name", "text"]})>;
 geoFunctions.within(position.geo, geofences[0].geo);
```

To use MySQL replace `postGis.geometry` with `mySql.geometry`.

The parameters used for the authentication can be stored in an environment variable called
 "POSTGIS_DEFAULT_CONFIG"/"MYSQL_DEFAULT_CONFIG"
```
{
  "algorithm": {
    "votingMode": "PRIORITY_DENY",
    "defaultDecision": "DENY",
    "errorHandling": "PROPAGATE"
  },
  "variables":
  {
	"POSTGIS_DEFAULT_CONFIG": 
	 {
		"user":"postgres",
		"password":"anotherPassword",
		"server":"localhost",
		"port": 5432,
		"dataBase":"MyDatabase"
	 }  
  }
```


#### Response

```json
[
	{
		"srid":0,
		"geo":{"type":"Point","coordinates":[0.0,0.0],"crs":{"type":"name","properties":{"name":"EPSG:3857"}}},
		"name":"Point",
		"text":"textValue"
	},
	{
		"srid":0,
		"geo":{"type":"Polygon","coordinates":[[[0.0,0.0],[1,0.0],[1,1],[0.0,1],[0.0,0.0]]],"crs":{"type":"name","properties":{"name":"EPSG:3857"}}},
		"name":"Polygon",
		"text":"textValue"
	}
]
```

with "singleResult": true
```json
{
	"srid":4326,
	"geo":{"type":"Point","coordinates":[0.0,0.0],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}},
	"name":"Point",
	"text":"textValue"
}
```

The property "srid" is the crs/srid set in the database. If there is none, it is 0 and the set "defaultCrS" is used for the geometries


## Function libraries

### GeoFunctions

All function parameters are `ObjectValue` instances containing the GeoJSON representation of a geometry. All functions return `Value`.

#### Functions

* `equalsExact(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `disjoint(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `touches(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `crosses(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `within(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `contains(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `overlaps(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `intersects(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns a boolean
* `buffer(ObjectValue jsonGeometry, NumberValue buffer)` returns a new geometry with added buffer
* `boundary(ObjectValue jsonGeometry)` returns the boundary geometry, or an empty geometry
* `centroid(ObjectValue jsonGeometry)` returns the centroid point
* `convexHull(ObjectValue jsonGeometry)` returns the convex hull as geometry
* `union(ObjectValue... jsonGeometries)` returns a geometry representing the union
* `intersection(ObjectValue... jsonGeometries)` returns a geometry representing the intersection
* `difference(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns the closure of points in this geometry not contained in the other
* `symDifference(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns the symmetric difference of both geometries
* `distance(ObjectValue thisGeometry, ObjectValue thatGeometry)` returns the distance
* `isWithinDistance(ObjectValue thisGeometry, ObjectValue thatGeometry, NumberValue distance)` returns a boolean
* `geoDistance(ObjectValue thisGeometry, ObjectValue thatGeometry, TextValue crs)` returns the geodesic distance in a coordinate reference system
* `isWithinGeodesicDistance(ObjectValue thisGeometry, ObjectValue thatGeometry, NumberValue distance)` returns a boolean
* `length(ObjectValue jsonGeometry)` returns the length of the geometry
* `area(ObjectValue jsonGeometry)` returns the area of the geometry
* `isSimple(ObjectValue jsonGeometry)` returns whether the geometry is a simple feature
* `isValid(ObjectValue jsonGeometry)` returns whether the geometry is valid per the OGC SFS specification
* `isClosed(ObjectValue jsonGeometry)` returns a boolean
* `milesToMeter(NumberValue value)` converts miles to meters
* `yardToMeter(NumberValue value)` converts yards to meters
* `degreeToMeter(NumberValue value)` converts degrees to meters
* `bagSize(ObjectValue jsonGeometry)` returns the number of geometries contained
* `oneAndOnly(ObjectValue jsonGeometryCollection)` returns the only geometry from a collection or an error
* `geometryIsIn(ObjectValue jsonGeometry, ObjectValue jsonGeometryCollection)` returns a boolean
* `geometryBag(ObjectValue... geometryJsonInput)` returns a geometry collection
* `flattenGeometryBag(ArrayValue arrayOfGeometries)` returns a flattened geometry collection
* `atLeastOneMemberOf(ObjectValue collectionThis, ObjectValue collectionThat)` returns whether at least one member of collectionThis is contained in collectionThat
* `subset(ObjectValue collectionThis, ObjectValue collectionThat)` returns a boolean

### GeoConverter

#### Functions

* gmlToGeoJson
* gmlToKml
* gmlToWkt
* geoJsonToKml
* geoJsonToGml
* geoJsonToWkt
* kmlToGml
* kmlToGeoJson
* kmlToWkt
* wktToGml
* wktToKml
* wktToGeoJson

#### Example policy
```
permit
  var p = <geo.postGIS({"user":"postgres", "password":"anotherPassword", "server":"localhost", "dataBase":"MyDatabase", "table":"position", "geoColumn":"geom", "responseFormat":"WKT", "singleResult": true, "where": "name = 'position1'"})>;
  var fences = <geo.mySQL({"user":"mysql", "password":"abcdefg", "server":"localhost", "dataBase":"test", "table":"fences", "geoColumn":"geom", "responseFormat":"GML"})>;
  var pos = geoConverter.wktToGeoJsonString(p.geo);
  var fence = geoConverter.gmlToGeoJsonString(fences[0].geo);
  var res = geoFunctions.within(pos, fence);
  res == true;
 ```
