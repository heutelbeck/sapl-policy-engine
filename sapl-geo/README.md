# SAPL-GEO

## Overview

This project provides a PIP which provides information from geo-trackers (Traccar and OwnTracks), Databases (PostGIS and MySQL) or from files containing geographies in GeoJSON, WKT, GML and KML. 
Also there is a function library containing geo-spatial operations like "within" or "touches". Finally there's a converter-libary which can be used to convert geometries to a different notation.

### Setup

To the pip and the libraries add them to the PDP:

```java
EmbeddedPolicyDecisionPoint pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(path, 
						() -> List.of(new GeoPolicyInformationPoint(new ObjectMapper())),
						List::of, 
						() -> List.of(new GeoFunctions(), new GeoConverter()), 
						List::of);
}
```
## Policy Information Point

### Traccar

After establishing a session with the traccar server the PIP uses the traccar socket endpoint to provide the current position of an device. Additionally all of the devices geofences are responded too.
You can use the "within" function from the GeoFunctions-library to check if the device is inside a fence.

#### Example policy
```
permit
where
  var response = <geo.traccar({"user":"TraccarUser", "password":"123Secret", "server":"123.45.67.1:8082", "protocol":"http", "responseFormat":"GEOJSON", "deviceId":1})>;
  var pos = response.position;
  var fence = response.geoFences[0].area;
  var res = geoFunctions.within(pos, fence);
  res == true;
```

#### Parameters

* "user": the traccar user account
* "password": the password of the account
* "server": the ip/dns-name of the traccar server
* "protocol": "http"/"https". (default is "http")
* "responseFormat": Possible values: "GEOJSON", "WKT", "GML", "KML" (default is "GEOJSON" which is reccomended as the GeoFunction-library needs GeoJson as parameters)
* "deviceId": the id of the device (int)
* "latitudeFirst": true: latitude is first coordinate of geometries, false: longitude is first


#### response

```json
{
	"deviceId":1,
	"position":{"type":"Point","coordinates":[22.8518509,36.6304242],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}},
	"altitude":409.1,
	"lastUpdate":"2024-04-17T17:54:09.000+00:00",
	"accuracy":3.24399995803833,
	"geoFences":[
		{"id":"1","attributes":{},"calendarId":"0","name":"home","description":"null","area":{"type":"LineString","coordinates":[[22.8515682,36.63058385],[22.85154205,36.63036821],[22.85185214,36.63035504],[22.85187082,36.63055175],[22.85156945,36.6305855]],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}}},
		{"id":"2","attributes":{},"calendarId":"0","name":"notHome","description":"null","area":{"type":"LineString","coordinates":[[22.8511593,36.63125866],[22.8511169,36.63086963],[22.85217445,36.6308004],[22.85223681,36.63126196],[22.85115681,36.63126196]],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}}}
		]
}
```

As you can see traccar delivers its coordinates in WGS84 (EPSG:4326).


### OwnTracks

The PIP connects to the api of the OwnTracks-recorder and polls. As OwnTracks no built-in authentication, the recorder is usually hosted by a webserver and protected by http basic auth.

#### Example policy
```
permit
where
  var a = <geo.ownTracks({"httpUser":"httpUser", "password":"test123", "user":"deviceUser", "server":"owntracks.somewhere/owntracks", "protocol":"http", "responseFormat":"GEOJSON", "deviceId":1})>;
  var pos = a.position;
  var fence = a.geoFences[0].name;
  var res = (fence=="home");
  res == tru	
```

#### Parameters

* "httpUser": Username vor http basic auth (optional)
* "password": the password httpUser (optional)
* "user": the OwnTracks device-user
* "server": the ip/dns-name of the traccar server
* "protocol": "http"/"https". (default is "http")
* "responseFormat": Possible values: "GEOJSON", "WKT", "GML", "KML" (default is "GEOJSON" which is reccomended as the GeoFunction-library needs GeoJson as parameters)
* "deviceId": the id of the device (int)
* "latitudeFirst": true: latitude is first coordinate of geometries, false: longitude is first


#### response

```json
{
	"deviceId":1,
	"position":{"type":"Point","coordinates":[48.6304265,12.8517104],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}},
	"altitude":409.0,"lastUpdate":"1714043759",
	"accuracy":100.0,
	"geoFences":[
		{"name":"home"},
		{"name":"home2"}
	]
}
```

As you can see OwnTracks delivers its coordinates in WGS84 (EPSG:4326). It provides all geofences the device is inside, but only their name.