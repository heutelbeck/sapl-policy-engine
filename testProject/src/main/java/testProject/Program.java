/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package testProject;


import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.mysql.MySqlConnection;
import io.sapl.geo.connection.owntracks.OwnTracksConnection;
import io.sapl.geo.connection.postgis.PostGisConnection;
import io.sapl.geo.connection.traccar.TraccarConnection;
import io.sapl.geo.fileimport.FileLoader;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.pip.http.ReactiveWebClient;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;





import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("all")
public class Program {


	public static void main( String[] args ) throws Exception
    {
		
		ObjectMapper mapper = new ObjectMapper();
		                      
        ///////
        
        var httpCl = new ReactiveWebClient(new ObjectMapper());
   
        
        
        ///owntracks
        
        var uri = new URI("http://owntracks.localhost/api/0/last");
        var valueToEncode = "mane:test";
        var h = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
        
        
//        HttpRequest req = null;
//
//        req = HttpRequest.newBuilder().uri(uri).header("Authorization", h).GET()
//                .build();
//
//        var client = HttpClient.newBuilder()
//        		.connectTimeout(Duration.ofSeconds(10))
//        		.build();
//
//        var res = client.send(req, BodyHandlers.ofString());
//        System.out.println(res.body());
//        
        
		 var html1        = """
	                {
	                    "baseUrl" : "%s",
	                    "accept" : "%s",
	                    "headers" : {
                        	"Authorization": "%s"
                    },
                 	"pollingIntervalMs" : 1000,
                 	"repetitions" : 5
	                }
	                """;
		 
		 var val1 = Val.ofJson(String.format(html1, "http://owntracks.localhost/owntracks/api/0/last?user=mane&device=1", MediaType.APPLICATION_JSON_VALUE, h));
//		var flux1 = httpCl.httpRequest(HttpMethod.GET, val1).map(Val::toString)
//				.doOnNext(a->{
//					System.out.println("---"+a);
//				}).subscribe();
//		 
		 var template  =  """
	                {
                 "baseUrl" : "%s",
                 "accept" : "%s",
                 "headers" : {
                 	"Authorization": "%s"
		 		}
             }
             """;
//		 var req = Val.ofJson(String.format(template, "ws://localhost:8083/ws", MediaType.APPLICATION_JSON_VALUE, h));
//	        var stream = new ReactiveWebClient(new ObjectMapper()).consumeWebSocket(req);  
//	        
//	        stream.map(Val::toString)
//	        .doOnNext(a->{
//				System.out.println("---"+a);
//			})
//	        .doOnError(a ->{
//	        	System.out.println("---------");
//	        	System.out.println(a.getMessage());
//	        })
//	        .subscribe();

////		
        
        
        var pg = """
                {
                "user":"postgres",
                "password":"Aig1979.",
            	"server":"localhost",
            	"port": 5432,
            	"dataBase":"nyc",
            	"table":"geometries",
            	"geoColumn":"geom",
            	"responseFormat":"GEOJSON",
            	"defaultCRS": 3857,
            	"pollingIntervalMs":1000,
            	"repetitions":5,
            	"singleResult": true,
            	"where": "name = 'Point'",
            	"columns": ["name", "text"]
            }
            """;
        var node = Val.ofJson(pg).get();
        
        
        var postgis = PostGisConnection.connect(node, mapper);
        var dis = postgis.subscribe(
	      		 content ->{ 
    			 
    			 System.out.println("postgis content: " + content.get().toString());
    			 System.out.println("--");
    			 
    		 },
  	      error -> System.out.println(String.format("Error receiving postgis: {%s}", error)),
  	      () -> System.out.println("Completed!!!")
  	      );
        
        
        
//        var postgis = new PostgresqlConnectionFactory(
//        		PostgresqlConnectionConfiguration .builder()
//        		 .username("postgres")
//	                .password("Aig1979.")    
//        		.host("localhost")
//	                .port(5432)
//	               
//	                .database("nyc")
//	                .build());
//	        
//        Mono.from(postgis.create())
//        .flatMapMany(connection -> {
//            return Flux.from(connection.createStatement("SELECT ST_AsGml(geom) AS gml FROM geometries").execute())
//                       .flatMap(result -> result.map((row, rowMetadata) -> row.get("gml", String.class)))           
//                       .repeatWhen((Repeat.times(5-1).fixedBackoff(Duration.ofMillis(1000))));
//        }).doOnError(error -> {
//            System.err.println("Error occurred: " + error.getMessage());
//        })
//        .subscribe(result -> {
//            
//            System.out.println(result);
//        });
        
        
        
        var pg1 = """
                {
                "user":"mane",
                "password":"Aig1979.",
            	"server":"localhost",
            	"port": 3306,
            	"dataBase":"test",
            	"table":"geometry",
            	"geoColumn":"geom",
            	"responseFormat":"GEOJSON",
            	"defaultCRS": 3857,
            	"pollingIntervalMs":1000,
            	"repetitions":5,
            	"singleResult": false,
            	
            	"columns": ["text"]
            }
            """;
        var nod = Val.ofJson(pg1).get();
        
        
//        var mysql = MySqlConnection.connect(nod, mapper);
//        var dis = mysql.subscribe(
//	      		 content ->{ 
//    			 
//    			 System.out.println("mysql content content: " + content.get().toString());
//    			 System.out.println("--");
//    			 
//    		 },
//  	      error -> System.out.println(String.format("Error receiving mysql: {%s}", error)),
//  	      () -> System.out.println("Completed!!!")
//  	      );
        
        
        
        
        
        
//        var fi = """
//                {
//                "path":"D:\\\\Bachelorarbeit\\\\Fileimport\\\\geojsonMultiple.json",
//                "responseFormat":"GEOJSON",
//                "crs":4326
//            	
//            }
//            """;
//
//        var nodefi = Val.ofJson(fi).getJsonNode();
//       var fileimport = FileLoader.connect(nodefi, mapper);
//       
//       fileimport.subscribe(
//	      		 content ->{ 
//   			 var b = content.get().toString();
//   			 System.out.println("fileImport content: " + b);
//   			 
//   		 },
// 	      error -> System.out.println(String.format("Error receiving file: {%s}", error)),
// 	      () -> System.out.println("Completed!!!")
// 	      );

        
        var st = """
                {
                "user":"longhair089@yahoo.de",
                "password":"Aig1979.",
            	"server":"127.0.0.1:8082",
            	"protocol":"http",
            	"responseFormat":"GEOJSON",
            	"deviceId":1,
            	"protocol":"http"
            }
            """;
//        var node1 = Val.ofJson(st).get();
//        var trc = TraccarConnection.connect( node1, mapper);
//		var dis = trc.subscribe(
//	      		 content ->{ 
//     			 var a = content.get().toString();
//     			 var b = mapper.convertValue(content.get(), GeoPipResponse.class);
//     			 System.out.println("traccar res: " + b.getDeviceId());
//     			 System.out.println("traccar content: " + a);
//     			 
//     		 },
//   	      error -> System.out.println(String.format("Error receiving socket: {%s}", error)),
//   	      () -> System.out.println("Completed!!!")
//   	      );

		
		
//		var settings = """
//                {
//                "httpBasicAuthUser":"mane",
//                "user":"mane",
//                "password":"test",
//            	"server":"owntracks.localhost/owntracks",
//            	"protocol":"http",
//            	"responseFormat":"GEOJSON",
//            	"deviceId":1,
//            	"protocol":"http"
//            }
//            """;
        var settings = """
                {
                "user":"mane",
            	"server":"owntracks.localhost/owntracks",
            	"protocol":"http",
            	"responseFormat":"GEOJSON",
            	"deviceId":1,
            	"protocol":"http"
            }
            """;
//		var node2 = Val.ofJson(settings).get();
//		var owntracks = OwnTracksConnection.connect(node2, mapper);
//		var ot = owntracks.subscribe(
//	      		 content ->{ 
//    			 var a = content.get().toString();
//
//    			 System.out.println("owntracks content: " + a);
//    			 
//    		 },
//  	      error -> System.out.println(String.format("Error receiving socket: {%s}", error)),
//  	      () -> System.out.println("Completed!!!")
//  	      );
		 
		
		//testcontainer
        
//        var rest = new TraccarRestManager("JSESSIONID=node075ntxtx3f30w1h33e3bw1f94d0.node0; Path=/", "localhost:51114", "http", mapper);
//
//        var mono = rest.getGeofences("1");
//        
//        var block = mono.block();
        
        
		// testcontainer
        
//        var test = TraccarSocketManager.getNew("test@fake.de", "1234", "localhost:51005", "http", 1, mapper);
//        var testflux = test.connect(GeoPipResponseFormat.WKT);
//        testflux.subscribe(
//	      		 content ->{ 
//    			 var a = content.toString();
//    			 
//    			 System.out.println("testflux content: " + a);
//    			 
//    		 },
//  	      error -> System.out.println(String.format("Error receiving socket: {%s}", error)),
//  	      () -> System.out.println("Completed!!!")
//  	      );
//        
        
        
        
		 var html        = """
	                {
	                    "baseUrl" : "%s",
	                    "accept" : "%s",
                    	"pollingIntervalMs" : 1000,
                    	"repetitions" : 2
	                }
	                """;
		 
		 var val = Val.ofJson(String.format(html, "https://jsonplaceholder.typicode.com/posts/1", MediaType.APPLICATION_JSON_VALUE));
//		var flux = httpCl.httpRequest(HttpMethod.GET, val).map(Val::toString)
//				.doOnNext(a->{
//					System.out.println("---"+a);
//				}).subscribe();
		
		//var b = flux.blockFirst();
		
//		flux.doOnNext(r ->{
//			System.out.println("---");
//			
//		}).subscribe(
//	    	      		 content ->{ 
//	    	      			 var a = content.get().toString();
//	    	      			 System.out.println("content: " + a);
//	    	      		 },
//	    	    	      error ->{ 
//	    	    	    	 
//	    	    	    	  System.out.println("Error receiving: "+ error.getMessage());
//	    	    	    	  System.out.println("Error receiving: "+ error.getStackTrace());
//	    	    	      },
//	    	    	      () -> {
//	    	    	    	  System.out.println("Completed!!!");});
//		

			
		try {
			Thread.sleep(50000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//dis.dispose();
		
		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("End");

    }
   
	
}
