# SAPL-GEO

## Overview

This project provides a PIP which provides information from geo-trackers (Traccar and OwnTracks), Databases (PostGIS and MySQL) or from files containing geographies in GeoJSON, WKT, GML and KML. 
Also there is a function library containing geo-spatial operations like "within" or "touches". Finally there's a converter-libary which can be used to convert geometries to a different notation.

### Setup

To the pip and the libraries add them to the PDP:

```java
EmbeddedPolicyDecisionPoint pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(path, () -> List.of(new GeoPolicyInformationPoint(new ObjectMapper())),
						List::of, 
						() -> List.of(new GeoFunctions(), new GeoConverter()), 
						List::of);
}
```