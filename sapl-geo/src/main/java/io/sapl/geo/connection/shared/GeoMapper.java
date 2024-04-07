package io.sapl.geo.connection.shared;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GeoMapper {

	private int deviceId;
	private String latitude;
	private String longitude;
	private String altitude;
	private String lastUpdate;
	private String accuracy;
	
	
	public GeoPipResponse mapPosition(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper) {
       
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        
        var             position        = geometryFactory.createPoint(
                new Coordinate(in.findValue(latitude).asDouble(), in.findValue(longitude).asDouble()));
        JsonNode        posRes          = mapper.createObjectNode();
        switch (format) {
        case GEOJSON:

            posRes = GeometryConverter.geometryToGeoJsonNode(position).get();
            break;

        case WKT:
            posRes = GeometryConverter.geometryToWKT(position).get();
            break;

        case GML:
            posRes = GeometryConverter.geometryToGML(position).get();
            break;

        case KML:
            posRes = GeometryConverter.geometryToKML(position).get();
            break;

        default:

            break;
        }

        return GeoPipResponse.builder().deviceId(deviceId).position(posRes)
              .altitude(in.findValue(altitude).asDouble()).lastUpdate(in.findValue(lastUpdate).asText())
              .accuracy(in.findValue(accuracy).asDouble()).build();
  }


}
