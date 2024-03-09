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


import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.postgis.PostGisConnection;
import io.sapl.geo.connection.traccar.TraccarRestManager;
import io.sapl.geo.connection.traccar.TraccarSocketManager;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import java.time.Duration;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;



import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;


public class Program {


	public static void main( String[] args ) throws Exception
    {
		
		ObjectMapper mapper = new ObjectMapper();
		                      
        ///////
        
        var httpCl = new ReactiveWebClient(new ObjectMapper());
        

//		var traccarSocket =  TraccarSocketManager.getNew("longhair089@yahoo.de", "Aig1979.", "128.0.0.1:8082", "http", 1, new ObjectMapper());
//		
//		var trc = traccarSocket.connect(GeoPipResponseFormat.WKT );
////		
        
        
        var pg = """
                {
                "user":"postgres",
                "password":"Aig1979.",
            	"server":"localhost",
            	"port": 5432,
            	"dataBase":"nyc",
            	"table":"geometries",
            	"column":"geom",
            	"responseFormat":"GEOJSON",
            	"defaultCRS": 4326
            }
            """;
        var node = Val.ofJson(pg).get();
        
        var postgis = PostGisConnection.connect(node, mapper);
        var dis = postgis.subscribe(
	      		 content ->{ 
    			 
    			 System.out.println("postgis content: " + content.get().toString());
    			 
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
        
        var st = """
                {
                "user":"longhair089@yahoo.de",
                "password":"Aig1979.",
            	"server":"127.0.0.1:8082",
            	"protocol":"http",
            	"responseFormat":"GEOJSON",
            	"deviceId":1
            }
            """;
//        var node1 = Val.ofJson(st).get();
//        var trc = TraccarSocketManager.connect( node1, mapper);
//		var dis = trc.subscribe(
//	      		 content ->{ 
//     			 var a = content.get().toString();
//     			 var b = mapper.convertValue(content.get(), GeoPipResponse.class);
//     			 System.out.println("res: " + b.getDeviceId());
//     			 System.out.println("traccar content: " + a);
//     			 
//     		 },
//   	      error -> System.out.println(String.format("Error receiving socket: {%s}", error)),
//   	      () -> System.out.println("Completed!!!")
//   	      );

		
		
		// testcontainer
        
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
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		dis.dispose();
		
		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("End");

    }
   
	
}
