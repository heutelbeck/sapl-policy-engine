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
import io.sapl.geo.connection.traccar.TraccarRestManager;
import io.sapl.geo.connection.traccar.TraccarSocketManager;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;


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
        
        var st = """
                {
                "user":"longhair089@yahoo.de",
                "password":"Aig1979.",
            	"server":"127.0.0.1:8082",
            	"protocol":"http",
            	"responseFormat":"GML",
            	"deviceId":1
            }
            """;
        var node = Val.ofJson(st).get();
//        var trc = TraccarSocketManager.connectToTraccar( node, mapper);
//		trc.subscribe(
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

        
        
        
        //var rest = new TraccarRestManager("JSESSIONID=node01mjbmvv7g2kae174r5drjrz7b20.node0; Path=/", "localhost:50538", "http", mapper);

        //var mono = rest.getGeofences("1");
        
        //var block = mono.block();
        
        
        
        
        var test = TraccarSocketManager.getNew("test@fake.de", "1234", "localhost:50538", "http", 1, mapper);
        var testflux = test.connect(GeoPipResponseFormat.WKT);
        testflux.subscribe(
	      		 content ->{ 
    			 var a = content.toString();
    			 
    			 System.out.println("testflux content: " + a);
    			 
    		 },
  	      error -> System.out.println(String.format("Error receiving socket: {%s}", error)),
  	      () -> System.out.println("Completed!!!")
  	      );
        
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
			Thread.sleep(500000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		System.out.println("End");

    }
   
	
}
