package io.sapl.geo.library;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;

import javax.naming.OperationNotSupportedException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.kml.v22.KML;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.xsd.PullParser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Schema;
import io.sapl.api.validation.Text;
import io.sapl.geo.schemata.GeoJsonSchemata;
import io.sapl.geo.schemata.TraccarSchemata;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = "geo", description = GeographicFunctionLibrary.DESCRIPTION)
public class GeographicFunctionLibrary {

    static final String DESCRIPTION = "A function library to manipulate, inspect, and convert geograpihc data.";

    private static final String GEOMETRY_LITERAL = "Geometry";
    private static final String NAME_LITERAL     = "name";

    private static final String INPUT_NOT_GEO_COLLECTION_WITH_ONLY_ONE_GEOM_ERROR = "Input must be a GeometryCollection containing only one Geometry.";

    private static final GeometryFactory GEOMETRY_FACTORY       = new GeometryFactory();
    private static final JsonNodeFactory JSON                   = JsonNodeFactory.instance;
    private static final int             WGS84                  = 4326;
    private static final GeoJsonWriter   GEOJSON_WRITER         = new GeoJsonWriter();
    private static final GeometryFactory WGS84_GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);
    private static final WKTReader       WGS84_WKT_READER       = new WKTReader(WGS84_GEOMETRY_FACTORY);

    /*
     * Geometry operations
     */

    @Function(name = "equalsExact", docs = """
            equals(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are exactly equal. Two Geometries are exactly equal if:
            they have the same structure
            they have the same values for their vertices, in exactly the same order.""")
    public Val geometryEquals(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(geometryEquals(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean geometryEquals(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject JsonNode geoJsonThis,
            @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .equalsExact(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = "disjoint(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are disjoint from each other (not intersecting each other). ")
    public Val disjoint(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(disjoint(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean disjoint(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .disjoint(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = "touches(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are touching each other.")
    public Val touches(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(touches(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean touches(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .touches(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = "crosses(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are crossing each other (having a intersecting area).")
    public Val crosses(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(crosses(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean crosses(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .crosses(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = """
            within(GEOMETRYTHIS, GEOMETRYTHAT): Tests if the GEOMETRYCOLLECTIONTHIS is fully within GEOMETRYCOLLECTIONTHAT (converse of contains-function).
            GEOMETRY2 can also be of type GeometryCollection.""")
    public Val within(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(within(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean within(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        final var geometryThis = JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString());
        final var geometryThat = JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString());
        return (geometryThat instanceof GeometryCollection) ? geometryThis.within(geometryThat.union())
                : geometryThis.within(geometryThat);
    }

    @Function(docs = """
            contains(GEOMETRYTHIS, GEOMETRYTHAT): Tests if the GEOMETRYCOLLECTIONTHIS fully contains GEOMETRYCOLLECTIONTHAT (converse of within-function).
            GEOMETRY1 can also be of type GeometryCollection.""")
    public Val contains(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(contains(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean contains(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        var geometryThis = JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString());
        var geometryThat = JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString());
        return (geometryThis instanceof GeometryCollection) ? geometryThis.union().contains(geometryThat)
                : geometryThis.contains(geometryThat);
    }

    @Function(docs = "overlaps(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are overlapping.")
    public Val overlaps(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(overlaps(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean overlaps(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .overlaps(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = "intersects(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries have at least one common intersection point.")
    public Val intersects(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(intersects(geoJsonThis.get(), geoJsonThat.get()));
    }

    public Boolean intersects(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat)
            throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .intersects(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = """
            buffer(GEOMETRY, BUFFER_WIDTH): Adds a buffer area of BUFFER_WIDTH around GEOMETRY and returns the new geometry.
            BUFFER_WIDTH is in the units of the coordinates or of the projection (if projection applied)""", schema = GeoJsonSchemata.POLYGON)
    public Val buffer(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry, @Number Val buffer)
            throws ParseException, JsonProcessingException {
        return buffer(jsonGeometry.get(), buffer.get().asDouble());
    }

    public Val buffer(@JsonObject JsonNode jsonGeometry, @Number Double buffer)
            throws ParseException, JsonProcessingException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).buffer(buffer));
    }

    @Function(docs = "boundary(GEOMETRY): Returns the boundary of a geometry.", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public Val boundary(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val jsonGeometry)
            throws ParseException, JsonProcessingException {
        return boundary(jsonGeometry.get());
    }

    public Val boundary(@JsonObject JsonNode jsonGeometry) throws ParseException, JsonProcessingException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getBoundary());
    }

    @Function(docs = "centroid(GEOMETRY): Returns a point that is the geometric center of gravity of the geometry.", schema = GeoJsonSchemata.POINT)
    public Val centroid(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry)
            throws ParseException, JsonProcessingException {
        return centroid(jsonGeometry.get());
    }

    public Val centroid(@JsonObject JsonNode jsonGeometry) throws ParseException, JsonProcessingException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getCentroid());
    }

    @Function(docs = "convexHull(GEOMETRY): Returns the convex hull (smallest convex polygon, that contains all points of the geometry) of the geometry.", schema = GeoJsonSchemata.CONVEX_HULL)
    public Val convexHull(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry)
            throws ParseException, JsonProcessingException {
        return convexHull(jsonGeometry.get());
    }

    public Val convexHull(@JsonObject JsonNode jsonGeometry) throws ParseException, JsonProcessingException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).convexHull());
    }

    @Function(docs = "union(GEOMETRYTHIS, GEOMETRYTHAT): Returns the union of two geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public Val union(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val... jsonGeometries)
            throws ParseException, JsonProcessingException {
        final var geometries = new JsonNode[jsonGeometries.length];
        for (int i = 0; i < jsonGeometries.length; i++) {
            geometries[i] = jsonGeometries[i].get();
        }
        return union(geometries);
    }

    public Val union(@JsonObject JsonNode... jsonGeometries) throws ParseException, JsonProcessingException {
        if (jsonGeometries.length == 1) {
            return Val.of(jsonGeometries[0]);
        }

        var geomUnion = JsonConverter.geoJsonToGeometry(jsonGeometries[0].toPrettyString());
        for (int i = 1; i < jsonGeometries.length; i++) {
            var additionalGeom = JsonConverter.geoJsonToGeometry(jsonGeometries[i].toPrettyString());
            geomUnion = geomUnion.union(additionalGeom);
        }
        return GeometryConverter.geometryToGeoJsonNode(geomUnion);
    }

    @Function(docs = "intersection(GEOMETRYTHIS, GEOMETRYTHAT): Returns the point set intersection of the geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public Val intersection(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat)
            throws ParseException, JsonProcessingException {
        return intersection(geoJsonThis.get(), geoJsonThat.get());
    }

    public Val intersection(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat)
            throws ParseException, JsonProcessingException {
        return GeometryConverter.geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .intersection(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = "difference(GEOMETRYTHIS, GEOMETRYTHAT): Returns the closure of the set difference between two geometries.", schema = GeoJsonSchemata.GEOMETRIES)
    public Val difference(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThat)
            throws ParseException, JsonProcessingException {
        return difference(geoJsonThis.get(), geoJsonThat.get());
    }

    public Val difference(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat)
            throws ParseException, JsonProcessingException {
        return GeometryConverter.geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .difference(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = "symDifference(GEOMETRYTHIS, GEOMETRY2): Returns the closure of the symmetric difference between two geometries.", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public Val symDifference(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val geoJsonThat)
            throws ParseException, JsonProcessingException {
        return symDifference(geoJsonThis.get(), geoJsonThat.get());
    }

    public Val symDifference(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat)
            throws ParseException, JsonProcessingException {
        return GeometryConverter.geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .symDifference(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = """
            distance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geometric (planar) distance between two geometries.
            Does return the value of the unit of the coordinates (or projection if used).""")
    public Val distance(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat) throws ParseException {
        return Val.of(distance(geoJsonThis.get(), geoJsonThat.get()));
    }

    public double distance(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return (JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .distance(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = """
            isWithinDistance(GEOMETRYTHIS, GEOMETRYTHAT, DISTANCE): Tests if two geometries are within the given geometric (planar) distance of each other.
            Uses the unit of the coordinates (or projection if used).""")
    public Val isWithinDistance(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geoJsonThat, @Number Val distInput)
            throws ParseException {
        return Val.of(isWithinDistance(geoJsonThis.get(), geoJsonThat.get(), distInput.get().asDouble()));
    }

    public Boolean isWithinDistance(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat,
            @Number Double distInput) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .isWithinDistance(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()), distInput);
    }

    @Function(docs = """
            geoDistance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geodetic distance of two geometries in [m].
            Coordinate Reference System is the un-projected (source) system (WGS84 recommended).""")
    public Val geoDistance(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThat)
            throws FactoryException, TransformException, ParseException {
        return Val.of(geoDistance(jsonGeometryThis.get(), jsonGeometryThat.get()));
    }

    @Function(docs = """
            TODO: add parameter
            geoDistance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geodetic distance of two geometries in [m].
            Coordinate Reference System is the un-projected (source) system (WGS84 recommended).""")
    public Val geoDistance(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThat,
            Val coordinateReferenceSystem) throws ParseException, FactoryException, TransformException {
        return Val.of(geoDistance(jsonGeometryThis.get(), jsonGeometryThat.get(), coordinateReferenceSystem.getText()));
    }

    public double geoDistance(JsonNode jsonGeometryThis, JsonNode jsonGeometryThat)
            throws ParseException, FactoryException, TransformException {
        return geoDistance(jsonGeometryThis, jsonGeometryThat, CrsConst.WGS84_CRS.getValue());
    }

    public double geoDistance(JsonNode jsonGeometryThis, JsonNode jsonGeometryThat, String coordinateReferenceSystem)
            throws ParseException, FactoryException, TransformException {
        return geodesicDistance(jsonGeometryThis, jsonGeometryThat, coordinateReferenceSystem);
    }

    private double geodesicDistance(JsonNode jsonGeometryThis, JsonNode jsonGeometryThat,
            String coordinateReferenceSystem) throws ParseException, FactoryException, TransformException {
        final var geometryThis     = JsonConverter.geoJsonToGeometry(jsonGeometryThis.toPrettyString());
        final var geometryThat     = JsonConverter.geoJsonToGeometry(jsonGeometryThat.toPrettyString());
        final var crs              = CRS.decode(coordinateReferenceSystem);
        final var distOp           = new DistanceOp(geometryThis, geometryThat);
        final var nearestPoints    = distOp.nearestPoints();
        final var nearestPointThis = nearestPoints[0];
        final var nearestPointThat = nearestPoints[1];
        final var gc               = new GeodeticCalculator(crs);
        gc.setStartingPosition(JTS.toDirectPosition(nearestPointThis, crs));
        gc.setDestinationPosition(JTS.toDirectPosition(nearestPointThat, crs));
        return gc.getOrthodromicDistance();
    }

    @Function(docs = """
            isWithinGeoDistance(GEOMETRYTHIS, GEOMETRYTHAT, DISTANCE): Tests if two geometries are within the given geodetic distance of each other. Uses [m] as unit.
            Coordinate Reference System is the unprojected (source) system (WGS84 recommended).""")
    public Val isWithinGeoDistance(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThis,
            @Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThat, @Number Val distance)
            throws TransformException, FactoryException, ParseException {
        return Val.of(isWithinGeoDistance(jsonGeometryThis.get(), jsonGeometryThat.get(), distance.get().asDouble()));
    }

    public Boolean isWithinGeoDistance(@JsonObject JsonNode jsonGeometryThis, @JsonObject JsonNode jsonGeometryThat,
            @Number double distance) throws TransformException, FactoryException, ParseException {
        return geoDistance(jsonGeometryThis, jsonGeometryThat) <= distance;
    }

    @Function(docs = """
            length(GEOMETRY): Returns the lenth of the geometry (perimeter in case of areal geometries).
            The returned value is in the units of the coordinates or of the projection (if projection applied).""")
    public Val length(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry)
            throws ParseException {
        return Val.of(length(jsonGeometry.get()));
    }

    public Double length(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getLength();
    }

    @Function(docs = """
            area(GEOMETRY): Returns the area of the geometry.
            The returned value is in the units (squared) of the coordinates or of the projection (if projection applied).""")
    public Val area(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry) throws ParseException {
        return Val.of(area(jsonGeometry.get()));
    }

    public Double area(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getArea();
    }

    @Function(docs = "isSimple(GEOMETRY): Returns true if the geometry has no anomalous geometric points (e.g. self interesection, self tangency,...).")
    public Val isSimple(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry)
            throws ParseException {
        return Val.of(isSimple(jsonGeometry.get()));
    }

    public Boolean isSimple(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).isSimple();
    }

    @Function(docs = "isValid(GEOMETRY): Returns true if the geometry is topologically valid according to OGC specifications.")
    public Val isValid(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry)
            throws ParseException {
        return Val.of(isValid(jsonGeometry.get()));
    }

    public Boolean isValid(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).isValid();
    }

    @Function(docs = "isClosed(GEOMETRY): Returns true if the geometry is either empty or from type (Multi)Point or a closed (Multi)LineString.")
    public Val isClosed(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val jsonGeometry)
            throws OperationNotSupportedException, ParseException {
        return Val.of(isClosed(jsonGeometry.get()));
    }

    public Boolean isClosed(@JsonObject JsonNode jsonGeometry) throws ParseException, OperationNotSupportedException {
        final var geometry = JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString());
        if (Geometry.TYPENAME_POINT.equals(geometry.getGeometryType())
                || Geometry.TYPENAME_MULTIPOINT.equals(geometry.getGeometryType())) {
            return true;
        }

        switch (geometry.getGeometryType()) {
        case Geometry.TYPENAME_LINESTRING:
            return ((LineString) geometry).isClosed();
        case Geometry.TYPENAME_MULTILINESTRING:
            return ((MultiLineString) geometry).isClosed();
        default:
            throw new OperationNotSupportedException(
                    "Operation isClosed is not applicable for the type " + geometry.getGeometryType());
        }
    }

    @Function(docs = "bagSize(GOEMETRYCOLLECTION): Returns the number of elements in the GEOMETRYCOLLECTION.")
    public Val bagSize(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val jsonGeometry) throws ParseException {
        return Val.of(bagSize(jsonGeometry.get()));
    }

    public int bagSize(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getNumGeometries();
    }

    @Function(docs = """
            oneAndOnly(GEOMETRYCOLLECTION): If GEOMETRYCOLLECTION only contains one element, this element will be returned.
            In all other cases an error will be thrown.""", schema = GeoJsonSchemata.GEOMETRIES)
    public Val oneAndOnly(@Schema(GeoJsonSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollection)
            throws OperationNotSupportedException, ParseException, JsonProcessingException {
        return oneAndOnly(jsonGeometryCollection.get());
    }

    public Val oneAndOnly(@JsonObject JsonNode jsonGeometryCollection)
            throws ParseException, OperationNotSupportedException, JsonProcessingException {
        final var geometryCollection = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollection.toPrettyString());
        if (geometryCollection.getNumGeometries() == 1) {
            return GeometryConverter.geometryToGeoJsonNode(geometryCollection.getGeometryN(0));
        } else {
            throw new OperationNotSupportedException(INPUT_NOT_GEO_COLLECTION_WITH_ONLY_ONE_GEOM_ERROR);
        }
    }

    @Function(docs = "geometryIsIn(GEOMETRY, GEOMETRYCOLLECTION): Tests if GEOMETRY is included in GEOMETRYCOLLECTION.")
    public Val geometryIsIn(@Schema(GeoJsonSchemata.GEOMETRIES) @JsonObject Val jsonGeometry,
            @Schema(GeoJsonSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollection) throws ParseException {
        return Val.of(geometryIsIn(jsonGeometry.get(), jsonGeometryCollection.get()));
    }

    public Boolean geometryIsIn(@JsonObject JsonNode jsonGeometry, @JsonObject JsonNode jsonGeometryCollection)
            throws ParseException {
        final var geometry           = JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString());
        final var geometryCollection = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollection.toPrettyString());
        for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
            if (geometry.equals(geometryCollection.getGeometryN(i))) {
                return true;
            }
        }
        return false;
    }

    @Function(docs = "geometryBag(GEOMETRY,...): Takes any number of GEOMETRY and returns a GEOMETRYCOLLECTION containing all of them.", schema = GeoJsonSchemata.GEOMETRY_COLLECTION)
    public Val geometryBag(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val... geometryJsonInput)
            throws ParseException, JsonProcessingException {
        final var geometries = new JsonNode[geometryJsonInput.length];
        for (int i = 0; i < geometryJsonInput.length; i++) {
            geometries[i] = geometryJsonInput[i].get();
        }

        return geometryBag(geometries);
    }

    public Val geometryBag(JsonNode... geometryJsonInput) throws ParseException, JsonProcessingException {
        final var geometries = new Geometry[geometryJsonInput.length];
        for (int i = 0; i < geometryJsonInput.length; i++) {
            geometries[i] = JsonConverter.geoJsonToGeometry(geometryJsonInput[i].toPrettyString());
        }
        return GeometryConverter.geometryToGeoJsonNode(GEOMETRY_FACTORY.createGeometryCollection(geometries));
    }

    @Function(docs = """
            resToGeometryBag(RESOURCE_ARRAY): Takes multiple Geometries from RESOURCE_ARRAY and turns them into a GeometryCollection
            (e.g. geofences from a third party system).""")
    public Val resToGeometryBag(@Schema(GeoJsonSchemata.JSON_SCHEME_COMPLETE) @Array Val resourceArray)
            throws ParseException, JsonProcessingException {
        final var nodes = (ArrayNode) resourceArray.get();
        final var vals  = new Val[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            vals[i] = Val.of(nodes.get(i));
        }
        return geometryBag(vals);
    }

    @Function(docs = """
            atLeastOneMemberOf(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2):
            Returns TRUE if at least one member of GEOMETRYCOLLECTIONTHIS is contained in GEOMETRYCOLLECTIONTHAT.""")
    public Val atLeastOneMemberOf(
            @Schema(GeoJsonSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollectionThis,
            @JsonObject Val jsonGeometryCollectionThat) {
        try {
            return Val.of(atLeastOneMemberOf(jsonGeometryCollectionThis.get(), jsonGeometryCollectionThat.get()));
        } catch (ParseException e) {
            return Val.error(e.getMessage());
        }
    }

    public Boolean atLeastOneMemberOf(@JsonObject JsonNode jsonGeometryCollectionThis,
            @JsonObject JsonNode jsonGeometryCollectionThat) throws ParseException {
        final var geometryCollectionThis = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThis.toPrettyString());
        final var geometryCollectionThat = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThat.toPrettyString());

        for (int i = 0; i < geometryCollectionThis.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionThat.getNumGeometries(); j++) {
                if (geometryCollectionThis.getGeometryN(i).equals(geometryCollectionThat.getGeometryN(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Function(docs = "subset(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns true, if GEOMETRYCOLLECTIONTHIS is a subset of GEOMETRYCOLLECTIONTHAT.")
    public Val subset(@Schema(GeoJsonSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollectionThis,
            @Schema(GeoJsonSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollectionThat) {
        try {
            return Val.of(subset(jsonGeometryCollectionThis.get(), jsonGeometryCollectionThat.get()));
        } catch (ParseException e) {
            return Val.error(e.getMessage());
        }
    }

    public Boolean subset(@JsonObject JsonNode jsonGeometryCollectionThis,
            @JsonObject JsonNode jsonGeometryCollectionThat) throws ParseException {
        final var geometryCollectionThis = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThis.toPrettyString());
        final var geometryCollectionThat = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThat.toPrettyString());
        if (geometryCollectionThis.getNumGeometries() > geometryCollectionThat.getNumGeometries()) {
            return false;
        }

        // Use BitSet as more efficient replacement for boolean array
        final var resultSet = new BitSet(geometryCollectionThis.getNumGeometries());

        for (int i = 0; i < geometryCollectionThis.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionThat.getNumGeometries(); j++) {
                if (geometryCollectionThis.getGeometryN(i).equals(geometryCollectionThat.getGeometryN(j))) {
                    resultSet.set(i);
                }
            }
        }
        return (resultSet.cardinality() == geometryCollectionThis.getNumGeometries());
    }

    @Function(docs = "toMeter(VALUE, UNIT): Converts the given VALUE from MILES to [m].")
    public Val milesToMeter(@Number Val jsonValue) {
        return Val.of(milesToMeter(jsonValue.get().asDouble()));
    }

    public double milesToMeter(@Number double value) {
        return value * DistanceUtils.MILES_TO_KM * 1000.0D;
    }

    @Function(docs = "toMeter(VALUE, UNIT): Converts the given VALUE from YARDS to [m].")
    public Val yardToMeter(@Number Val jsonValue) {
        return Val.of(yardToMeter(jsonValue.get().asDouble()));
    }

    public double yardToMeter(double yards) {
        return milesToMeter(yards / 1760.0D);
    }

    @Function(docs = "toMeter(VALUE, UNIT): Converts the given VALUE from DEGREES to [m].")
    public Val degreeToMeter(@Number Val jsonValue) {
        return Val.of(jsonValue.get().asDouble() * DistanceUtils.DEG_TO_KM * 1000);
    }

    /*
     * Converters
     */

    @Function(docs = "converts GML to KML")
    public Val gmlToKml(@Text Val gml) throws SAXException, IOException, ParserConfigurationException {
        return GeometryConverter.geometryToKML(GmlConverter.gmlToGeometry(gml));
    }

    @Function(docs = "converts GML to GeoJSON", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public Val gmlToGeoJson(@Text Val gml) throws SAXException, IOException, ParserConfigurationException {
        return GeometryConverter.geometryToGeoJsonNode(GmlConverter.gmlToGeometry(gml));
    }

    @Function(docs = "converts GML to WKT")
    public Val gmlToWkt(@Text Val gml) throws SAXException, IOException, ParserConfigurationException {
        return GeometryConverter.geometryToWKT(GmlConverter.gmlToGeometry(gml));
    }

    @Function(docs = "converts GeoJSON to KML")
    public Val geoJsonToKml(@Text Val geoJson) throws ParseException {
        return GeometryConverter.geometryToKML(JsonConverter.geoJsonToGeometry(geoJson));
    }

    @Function(docs = "converts KML to GML")
    public Val geoJsonToGml(@Text Val geoJson) throws ParseException {
        return GeometryConverter.geometryToGML(JsonConverter.geoJsonToGeometry(geoJson));
    }

    @Function(docs = "converts GeoJSON to WKT")
    public Val geoJsonToWkt(@Text Val geoJson) throws ParseException {
        return GeometryConverter.geometryToWKT(JsonConverter.geoJsonToGeometry(geoJson));
    }

    @Function(docs = "converts KML to GML")
    public Val kmlToGml(@Text Val kml) throws ParseException {
        return GeometryConverter.geometryToGML(KmlConverter.kmlToGeometry(kml));
    }

    @Function(docs = "converts KML to GeoJSON", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public Val kmlToGeoJson(@Text Val kml) throws ParseException, JsonProcessingException {
        return GeometryConverter.geometryToGeoJsonNode(KmlConverter.kmlToGeometry(kml));
    }

    @Function(docs = "converts KML to WKT")
    public Val kmlToWkt(@Text Val kml) throws ParseException {
        return GeometryConverter.geometryToWKT(KmlConverter.kmlToGeometry(kml));
    }

    @Function(docs = "converts WKT to GML")
    public Val wktToGml(@Text Val wkt) throws ParseException {
        return GeometryConverter.geometryToGML(WktConverter.wktToGeometry(wkt));
    }

    @Function(docs = "converts WKT to KML")
    public Val wktToKml(@Text Val wkt) throws ParseException {
        return GeometryConverter.geometryToKML(WktConverter.wktToGeometry(wkt));
    }

    @SneakyThrows
    @Function(docs = "converts WKT to GeoJSON", schema = GeoJsonSchemata.JSON_SCHEME_COMPLETE)
    public static Val wktToGeoJson(@Text Val wkt) {
        return GeometryConverter.geometryToGeoJsonNode(WktConverter.wktToGeometry(wkt));
    }

    @Function(docs = "parses kml to Geometries")
    public Val parseKML(@Text Val kml) throws XMLStreamException, IOException, SAXException {
        return Val.of(parseKML(kml.getText()));
    }

    public ArrayNode parseKML(String kmlString) throws XMLStreamException, IOException, SAXException {
        final var     features = new ArrayList<SimpleFeature>();
        final var     stream   = new ByteArrayInputStream(kmlString.getBytes(StandardCharsets.UTF_8));
        final var     config   = new KMLConfiguration();
        final var     parser   = new PullParser(config, stream, KML.Placemark);
        SimpleFeature f        = null;
        while ((f = (SimpleFeature) parser.parse()) != null) {
            features.add(f);
        }
        return convertToObjects(features);
    }

    protected ArrayNode convertToObjects(ArrayList<SimpleFeature> placeMarks) {
        final var arrayNode = JSON.arrayNode();
        for (SimpleFeature feature : placeMarks) {
            var       name         = "unnamed geometry";
            final var nameProperty = feature.getAttribute(NAME_LITERAL);
            if (nameProperty != null) {
                name = nameProperty.toString();
            }
            final var geom = (Geometry) feature.getAttribute(GEOMETRY_LITERAL);
            final var geo  = JSON.objectNode();
            if (geom != null) {
                geo.set(NAME_LITERAL, new TextNode(name));
                final var json = new TextNode(GeometryConverter.geometryToKML(geom).getText());
                geo.set(GEOMETRY_LITERAL, json);
                arrayNode.add(geo);
            }
        }
        return arrayNode;
    }

    @SneakyThrows
    @Function(docs = "converts a traccar position to GeoJSON.")
    public static Val traccarPositionToGeoJSON(Val traccarPosition) {
        final var position  = traccarPosition.get();
        final var longitude = position.get(TraccarSchemata.LONGITUDE).asDouble();
        final var latitude  = position.get(TraccarSchemata.LATITUDE).asDouble();
        Geometry  geometry;
        if (position.has(TraccarSchemata.ALTITUDE)) {
            final var altitude = position.get(TraccarSchemata.ALTITUDE).asDouble();
            geometry = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude, altitude));
        } else {
            geometry = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        }
        return Val.ofJson(GEOJSON_WRITER.write(geometry));
    }

    public static class CoordinateFlippingFilter implements CoordinateFilter {
        public void filter(Coordinate coord) {
            double oldX = coord.x;
            coord.x = coord.y;
            coord.y = oldX;
        }
    }

    @SneakyThrows
    @Function(docs = "converts a traccar geofence to GeoJSON.")
    public static Val traccarGeofenceToGeoJson(Val geofence) {
        final var area     = geofence.get().get(TraccarSchemata.AREA);
        final var geometry = new WKTReader().read(area.asText());
        geometry.setSRID(WGS84);
        // GeoJSON needs coordinates in longitude then latitude. Geometry will have it
        // the other way around.
        geometry.apply(new CoordinateFlippingFilter());
        return Val.ofJson(GEOJSON_WRITER.write(geometry));
    }

}
