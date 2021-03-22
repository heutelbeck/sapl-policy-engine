/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.Decision
import io.sapl.interpreter.DefaultSAPLInterpreter
import io.sapl.interpreter.EvaluationContext
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import io.sapl.interpreter.pip.GeoPolicyInformationPoint
import java.util.HashMap
import java.util.Map
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class GeoFunctionLibraryTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static Map<String, JsonNode> variables = new HashMap<String, JsonNode>();
	static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
		variables);
	static JsonNode subject;
	static JsonNode resource;

	@BeforeEach
	def void setUp() {
		ATTRIBUTE_CTX.loadPolicyInformationPoint(new GeoPolicyInformationPoint());
		FUNCTION_CTX.loadLibrary(new GeoFunctionLibrary());
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
			"collection": {
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
					}
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
	def void equalsTest() {
		val policyDefinition = '''
			policy "equalsTest" 
			permit
			where
			geo.equals(resource.pointOne, resource.pointOne);
			!geo.equals(resource.pointOne, resource.polyTwo);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void crossesTouchesTest() {
		val policyDefinition = '''
			policy "equalsTest" 
			permit
			where
			geo.crosses(resource.lineOne, resource.lineTwo);
			!geo.touches(resource.lineOne, resource.lineTwo);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void disjointIntersectsTest() {
		val policyDefinition = '''
			policy "disjointIntersectsTest" 
			permit
			where
			geo.intersects(resource.lineOne, resource.lineTwo);
			!geo.disjoint(resource.lineOne, resource.lineTwo);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void containsWithinTest() {
		val policyDefinition = '''
			policy "containsWithinTest" 
			permit
			where
			geo.contains(resource.polyOne, resource.pointOne);
			geo.within(resource.pointOne, resource.polyOne);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void resourceToGeometryBagTest() {
		val policyDefinition = '''
			policy "resourceToGeometryBag" 
			permit
			where
			var bag = geo.resToGeometryBag(resource.collection.*);
			geo.geometryIsIn(resource.polyOne, bag);
			geo.within(resource.pointOne, bag);
			geo.contains(bag, resource.pointOne);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void overlapsTest() {
		val policyDefinition = '''
			policy "overlapsTest" 
			permit
			where
			geo.overlaps(resource.polyTwo, resource.polyThree);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void bufferTest() {
		val policyDefinition = '''
			policy "bufferTest" 
			permit
			where
			geo.within(resource.pointOne, geo.buffer(resource.polyOne, 5.0));
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void centroidTest() {
		val policyDefinition = '''
			policy "centroidTest" 
			permit
			where
			geo.isWithinDistance(resource.pointTwo, geo.centroid(resource.polyOne), 1);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void boundaryConvexhullTest() {
		val policyDefinition = '''
			policy "boundaryConvexhullTest" 
			permit
			where
			var bound = geo.boundary(resource.polyOne);
			geo.within(resource.pointOne, geo.convexHull(resource.polyOne));
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void unionTest() {
		val policyDefinition = '''
			policy "unionTest" 
			permit
			where
			var unionResult = geo.union(resource.polyOne, resource.polyTwo);
			geo.within(resource.polyOne, unionResult);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void intersectionTest() {
		val policyDefinition = '''
			policy "intersectionTest" 
			permit
			where
			var intersectionResult = geo.intersection(resource.polyTwo, resource.polyThree);
			geo.equals(intersectionResult, resource.polyThreeIntFour);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void differenceTest() {
		val policyDefinition = '''
			policy "differenceTest" 
			permit
			where
			var differenceResult = geo.difference(resource.polyTwo, resource.polyThree);
			var symDifferenceResult = geo.symDifference(resource.polyTwo, resource.polyThree);
			geo.area(differenceResult) <= geo.area(symDifferenceResult);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void areaLengthTest() {
		val policyDefinition = '''
			policy "areaLengthTest" 
			permit
			where
			geo.area(resource.polyThreeIntFour) <= geo.area(resource.polyTwo);
			geo.length(resource.lineOne) >= geo.length(resource.lineTwo);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void geoDistanceTest() {
		val policyDefinition = '''
			policy "geoDistanceTest" 
			permit
			where
			geo.isWithinGeoDistance(resource.pointOne, resource.pointTwo, 550000);
			!geo.isWithinGeoDistance(resource.pointOne, resource.pointTwo, 5);
			geo.geoDistance(resource.pointOne, resource.pointTwo) >= 547000;
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void distanceTest() {
		val policyDefinition = '''
			policy "distanceTest" 
			permit
			where
			geo.distance(resource.pointOne, resource.pointTwo) <= geo.distance(resource.pointOne, resource.lineTwo);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

//	@Test
//	def void unitConversionTest()  {
//		val policyDefinition = '''
//			policy "unitConversionTest" 
//			permit
//			where
//			geo.toMeter(2, "yd") == 1.8288;
//			geo.toMeter(1000, "cm") == 10;
//			geo.toSquareMeter(1, "ft^2") == 0.09290304;
//			geo.toSquareMeter(2000, "in^2") == 1.29032;
//		''';
//		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
//	}
	@Test
	def void conversionFromWrongUnitsTest() {
		val policyDefinition = '''
			policy "conversionFromWrongUnitsTest" 
			permit
			where
			geo.toMeter(2, "s") == 1.8;
		''';
		assertThat(getDecision(policyDefinition), is(Decision.INDETERMINATE));
	}

	@Test
	def void conversionFromWrongUnits2Test() {
		val policyDefinition = '''
			policy "conversionFromWrongUnits2Test" 
			permit
			where
			geo.toSquareMeter(1, "min") == 0.09;
		''';
		assertThat(getDecision(policyDefinition), is(Decision.INDETERMINATE));
	}

	@Test
	def void simpleValidTest() {
		val policyDefinition = '''
			policy "simpleValidTest" 
			permit
			where
			geo.isSimple(resource.pointOne);
			geo.isValid(resource.pointOne);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void isClosedTest() {
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
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void geomCollectionTest() {
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
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	@Test
	def void geomCollection2Test() {
		val policyDefinition = '''
			policy "geomCollection2Test" 
			permit
			where
			geo.geometryIsIn(resource.pointOne, resource.geoColl1);
		''';
		assertThat(getDecision(policyDefinition), is(Decision.NOT_APPLICABLE));
	}

	@Test
	def void oneAndOnlyTest() {
		val policyDefinition = '''
			policy "geomCollection3Test" 
			permit
			where
			geo.oneAndOnly(resource.geoColl1);
			
		''';
		assertThat(getDecision(policyDefinition), is(Decision.INDETERMINATE));
	}

	@Test
	def void projectionTest() {
		val policyDefinition = '''
			policy "enableProjectionTest" 
			permit
			where
			var proj = geo.getProjection("EPSG:4326", "EPSG:3857");
			var projInv = geo.getProjection("EPSG:3857","EPSG:4326");
			var projPointOne = geo.project(resource.pointOne, proj);
			!geo.equals(resource.pointOne, projPointOne);
			geo.equals(resource.pointOne, geo.project(projPointOne, projInv));
		''';
		assertThat(getDecision(policyDefinition), is(Decision.PERMIT));
	}

	def Decision getDecision(String policyDefinition) {
		return INTERPRETER.evaluate(new AuthorizationSubscription(subject, null, resource, null), policyDefinition,
			PDP_EVALUATION_CONTEXT).blockFirst().getDecision();
	}
}
