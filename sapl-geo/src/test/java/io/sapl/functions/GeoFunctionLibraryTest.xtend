package io.sapl.functions

/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.interpreter.PolicyEvaluationException
import io.sapl.api.pdp.Decision
import io.sapl.api.pdp.Request
import io.sapl.interpreter.DefaultSAPLInterpreter
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import io.sapl.interpreter.pip.GeoAttributeFinder
import java.util.HashMap
import java.util.Map
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals

class GeoFunctionLibraryTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	private static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	private static final FunctionContext FUNCTION_CTX_WITH_PROJECTION = new AnnotationFunctionContext();
	private static Map<String, JsonNode> variables = new HashMap<String, JsonNode>();
	private static JsonNode subject;
	private static JsonNode resource;

	@Before
	def void init() {
		ATTRIBUTE_CTX.loadPolicyInformationPoint(new GeoAttributeFinder());
		FUNCTION_CTX.loadLibrary(new GeoFunctionLibrary());
		FUNCTION_CTX_WITH_PROJECTION.loadLibrary(
			new GeoFunctionLibrary(new GeoProjection(GeoProjection.WGS84_CRS, GeoProjection.WEB_MERCATOR_CRS)));
		subject = MAPPER.readValue('''
		{
			"subject":{
				"id": "1234"
			}
		}''', JsonNode);

		resource = MAPPER.readValue('''
		{
			"pointOne":{ 
				"type": "Point",
				"coordinates":
					[10.0, 15.0]
			},
			"pointTwo":{ 
				"type": "Point",
				"coordinates": 
					[10.0, 10.0]
			},
			"multiPointOne":{ 
				"type": "MultiPoint",
				"coordinates": [
					[10.0, 15.0],
					[15.0, 15.0],
					[20.0, 15.0]
				]
			},
			"lineOne":{ 
				"type": "LineString",
				"coordinates": [
					[0.0, 0.0],
					[0.0, 15.0]
				]
			},
			"lineTwo":{ 
				"type": "LineString",
				"coordinates": [
					[-5.0, 5.0],
					[5.0, 5.0]
				]
			},
			"multiLineOne":{ 
				"type": "MultiLineString",
				"coordinates": [
					[[-5.0, 5.0], [5.0, 15.0]],
					[[-1.0, 1.0], [1.0, 0.0]]
				]
			},
			"polyOne":{ 
				"type": "Polygon",
				"coordinates": [
					[0.0, 0.0],
					[20.0, 0.0],
					[20.0, 20.0],
					[0.0, 20.0],
					[0.0, 0.0]
				]
			},
			"polyTwo":{ 
				"type": "Polygon",
				"coordinates": [
					[40.0, 5.0],
					[60.0, 5.0],
					[60.0, 10.0],
					[40.0, 10.0],
					[40.0, 5.0]
				]
			},
			"polyThree":{ 
				"type": "Polygon",
				"coordinates": [
					[40.0, 0.0],
					[50.0, 0.0],
					[50.0, 10.0],
					[40.0, 10.0],
					[40.0, 0.0]
				]
			},
			"polyThreeIntFour":{ 
				"type": "Polygon",
				"coordinates": [
					[40.0, 5.0],
					[50.0, 5.0],
					[50.0, 10.0],
					[40.0, 10.0],
					[40.0, 5.0]
				]
			},
			 "geoColl1" :{ 
			 	"type": "GeometryCollection",
			 	  "geometries": [
			 	    { "type": "Point",
			 	      "coordinates": [20.0, 15.0]
			 	      },
			 	    { "type": "LineString",
			 	      "coordinates": [ [51.0, 0.0], [52.0, 1.0] ]
			 	      }
			 	  ]
			}
		}''', JsonNode);

		variables.put("traccarConfig", MAPPER.readValue('''
		{
			"deviceID": "866714",
			"url": "http://localhost:8082/api/",
			"credentials": "YWRtaW46YWRtaW4=" 
		}''', JsonNode));

		variables.put("postGisConfig", MAPPER.readValue('''
		{
			"serverAdress": "localhost",
			"port": "5432",
			"db": "postgis_24_sample",
			"table": "towns",
			"username": "postgres",
			"password": "postgres",
			"geometryColName": "geom",
			"idColName": "town",
			"pkColName": "gid",
			"from": 0,
			"until": 2
		}''', JsonNode));
	}

	@Test
	def void equalsTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "equalsTest" 
			permit
			where
			geo.equals(resource.pointOne, resource.pointOne);
			!geo.equals(resource.pointOne, resource.polyTwo);
		''';
		assertEquals("geo.equals() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void crossesTouchesTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "equalsTest" 
			permit
			where
			geo.crosses(resource.lineOne, resource.lineTwo);
			!geo.touches(resource.lineOne, resource.lineTwo);
		''';
		assertEquals("geo.crosses() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void disjointIntersectsTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "disjointIntersectsTest" 
			permit
			where
			geo.intersects(resource.lineOne, resource.lineTwo);
			!geo.disjoint(resource.lineOne, resource.lineTwo);
		''';
		assertEquals("geo.disjoint() or geo.intersects() does not work as expected",
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void containsWithinTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "containsWithinTest" 
			permit
			where
			geo.contains(resource.polyOne, resource.pointOne);
			geo.within(resource.pointOne, resource.polyOne);
		''';
		assertEquals("geo.contains() or geo.within() does not work as expected",
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void overlapsTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "overlapsTest" 
			permit
			where
			geo.overlaps(resource.polyTwo, resource.polyThree);
		''';
		assertEquals("geo.overlaps() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void bufferTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "bufferTest" 
			permit
			where
			geo.within(resource.pointOne, geo.buffer(resource.polyOne, 5.0));
		''';
		assertEquals("geo.buffer() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void centroidTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "centroidTest" 
			permit
			where
			geo.isWithinDistance(resource.pointTwo, geo.centroid(resource.polyOne), 1);
		''';
		assertEquals("geo.centroid() or geo.isWithinDistance() work not as expected", 
			getDecision(policyDefinition), Decision.PERMIT
		);
	}

	@Test
	def void centroidProjectionTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "centroidProjectionTest" 
			permit
			where
			geo.isWithinDistance(resource.pointTwo, geo.centroid(resource.polyOne), 20000);
		''';
		assertEquals("geo.centroid() or geo.isWithinDistance() work not as expected when applying projections",
			INTERPRETER.evaluate(new Request(subject, null, resource, null), policyDefinition, 
			ATTRIBUTE_CTX,  FUNCTION_CTX_WITH_PROJECTION, variables).getDecision(), Decision.PERMIT);
	}

	@Test
	def void boundaryConvexhullTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "boundaryConvexhullTest" 
			permit
			where
			var bound = geo.boundary(resource.polyOne);
			geo.within(resource.pointOne, geo.convexHull(resource.polyOne));
		''';
		assertEquals("geo.boundary() or geo.convexHull() does not work as expected",
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void unionTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "unionTest" 
			permit
			where
			var unionResult = geo.union(resource.polyOne, resource.polyTwo);
			geo.within(resource.polyOne, unionResult);
		''';
		assertEquals("geo.union() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void intersectionTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "intersectionTest" 
			permit
			where
			var intersectionResult = geo.intersection(resource.polyTwo, resource.polyThree);
			geo.equals(intersectionResult, resource.polyThreeIntFour);
		''';
		assertEquals("geo.intersection() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void differenceTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "differenceTest" 
			permit
			where
			var differenceResult = geo.difference(resource.polyTwo, resource.polyThree);
			var symDifferenceResult = geo.symDifference(resource.polyTwo, resource.polyThree);
			geo.area(differenceResult) <= geo.area(symDifferenceResult);
		''';
		assertEquals("geo.difference() or geo.symDifference() does not work as expected",
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void areaLengthTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "areaLengthTest" 
			permit
			where
			geo.area(resource.polyThreeIntFour) <= geo.area(resource.polyTwo);
			geo.length(resource.lineOne) >= geo.length(resource.lineTwo);
		''';
		assertEquals("geo.area() or geo.length() does not work as expected",
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void geoDistanceTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "geoDistanceTest" 
			permit
			where
			geo.isWithinGeoDistance(resource.pointOne, resource.pointTwo, 550000);
			!geo.isWithinGeoDistance(resource.pointOne, resource.pointTwo, 5);
			geo.geoDistance(resource.pointOne, resource.pointTwo) >= 547000;
		''';
		assertEquals("geo.geoDistance() or geo.isWithinGeoDistance() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void distanceTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "distanceTest" 
			permit
			where
			geo.distance(resource.pointOne, resource.pointTwo) <= geo.distance(resource.pointOne, resource.lineTwo);
		''';
		assertEquals("geo.distance() does not work as expected",
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void unitConversionTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "unitConversionTest" 
			permit
			where
			geo.toMeter(2, "yd") == 1.8288;
			geo.toMeter(1000, "cm") == 10;
			geo.toSquareMeter(1, "ft^2") == 0.09290304;
			geo.toSquareMeter(2000, "in^2") == 1.29032;
		''';
		assertEquals("Unit conversions work not as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void conversionFromWrongUnitsTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "conversionFromWrongUnitsTest" 
			permit
			where
			geo.toMeter(2, "s") == 1.8;
		''';
		assertEquals("Unit conversions work not as expected",
			getDecision(policyDefinition), Decision.INDETERMINATE);
	}

	@Test
	def void conversionFromWrongUnits2Test() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "conversionFromWrongUnits2Test" 
			permit
			where
			geo.toSquareMeter(1, "min") == 0.09;
		''';
		assertEquals("Unit conversions work not as expected",
			getDecision(policyDefinition), Decision.INDETERMINATE);
	}

	@Test
	def void simpleValidTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "simpleValidTest" 
			permit
			where
			geo.isSimple(resource.pointOne);
			geo.isValid(resource.pointOne);
		''';
		assertEquals("geo.isSimple() or geo.isValid() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void isClosedTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "isClosedTest" 
			permit
			where
			geo.isClosed(resource.pointOne);
			geo.isClosed(resource.multiPointOne);
			!geo.isClosed(resource.lineOne);
			!geo.isClosed(resource.multiLineOne);
			!geo.isClosed(resource.polyOne);
			!geo.isClosed(resource.geoColl1);
		''';
		assertEquals("geo.isClosed() does not work as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void geomCollectionTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "geomCollectionTest" 
			permit
			where
			var bag1 = geo.geometryBag(resource.lineTwo, resource.polyOne, resource.pointOne);
			var bag2 = geo.geometryBag(resource.pointOne, resource.pointTwo);
			var bag3 = geo.geometryBag(resource.pointOne);
			var bag4 = geo.geometryBag(resource.pointTwo, resource.lineTwo);
			geo.geometryIsIn(resource.lineTwo, bag1);
			geo.atLeastOneMemberOf(bag2, bag1);
			geo.bagSize(bag1) == 3;
			geo.equals(geo.oneAndOnly(bag3), resource.pointOne);
			geo.subset(bag3, bag1);
			!geo.subset(bag2, bag3);
			!geo.subset(bag3, bag4);
		''';
		assertEquals("GeometryCollections work not as expected", 
			getDecision(policyDefinition), Decision.PERMIT);
	}

	@Test
	def void geomCollection2Test() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "geomCollection2Test" 
			permit
			where
			geo.geometryIsIn(resource.pointOne, resource.geoColl1);
		''';
		assertEquals("GeometryCollections work not as expected", 
			getDecision(policyDefinition), Decision.NOT_APPLICABLE);
	}

	@Test
	def void oneAndOnlyTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "geomCollection3Test" 
			permit
			where
			geo.oneAndOnly(resource.geoColl1);
			
		''';
		assertEquals("geo.oneAndOnly() does not work as expected",
			getDecision(policyDefinition) , Decision.INDETERMINATE);
	}
	
	@Test
	def void enableProjectionTest() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "enableProjectionTest" 
			permit
			where
			geo.enableProjection("EPSG:4326", "EPSG:3857"); 
			var pointOneProj = geo.project(resource.pointOne);
			geo.disableProjection()
			!geo.equals(resource.pointOne, pointOneProj);
			
		''';
		assertEquals("geo.projection() does not work as expected",
			getDecision(policyDefinition) , Decision.INDETERMINATE);
	}
	
	def Decision getDecision(String policyDefinition) {
		return INTERPRETER.evaluate(new Request(subject, null, resource, null), policyDefinition, 
			ATTRIBUTE_CTX, FUNCTION_CTX, variables).getDecision();
	}
}
