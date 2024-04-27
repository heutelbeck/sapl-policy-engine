package io.sapl.geo.functionlibraries;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.kml.v22.KML;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.xsd.PullParser;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeometryConverter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@FunctionLibrary(name = "geoParser", description = "")
public class GeoParser {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final String PARSE_KML     = "parses kml to Geometries";
	private static final String ERROR 		  = "Error while parsing kml";
    private static final String NAME 		  = "name";
    private static final String GEOM 		  = "Geometry";
	
    private final ObjectMapper mapper;
    
	@Function(name = "parseKml", docs = PARSE_KML)
    public Val parseKML(Val kml) {

        try {
            return Val.of(parseKML(kml.getText()));
        } catch (Exception e) {
            return Val.error(e);
        }
    }
	
	public ArrayNode parseKML(String kmlString) {
		
		var features = new ArrayList<SimpleFeature>();
		try {
			var stream = new ByteArrayInputStream(kmlString.getBytes(StandardCharsets.UTF_8));
			var config = new KMLConfiguration();
			PullParser parser = new PullParser(config, stream, KML.Placemark);
			SimpleFeature f = null;		    
		    
			while ((f = (SimpleFeature) parser.parse()) != null) {

		        features.add(f);
		      }
	        
		}catch (Exception e) {
			
			throw new PolicyEvaluationException(ERROR, e);
		}
		return  convertToObjects(features);
	}
	
	protected ArrayNode convertToObjects(Collection<?> placeMarks) {
        ArrayNode arrayNode = mapper.createArrayNode();

        for (Object obj : placeMarks) {

            if (!(obj instanceof SimpleFeature feature)) {
                throw new PolicyEvaluationException(ERROR);
            } else {
            	String name = "unnamed geometry";
            	var nameProperty = feature.getAttribute(NAME);
            	if(nameProperty != null) {
            		name = nameProperty.toString();
            	}
                Geometry geom = (Geometry) feature.getAttribute(GEOM);
                ObjectNode geo = JSON.objectNode();
                
                if (geom != null) {
                	geo.set(NAME, new TextNode(name));
                	geo.set(GEOM, GeometryConverter.geometryToKML(geom).get());
                	arrayNode.add(geo);
                }
            }
        }
        return arrayNode;
    }
	
}
