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
package com.example.demo;


import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.geo.functionlibraries.GeoConverter;
import io.sapl.geo.functionlibraries.GeoFunctions;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.server.GeoPolicyInformationPoint;
import io.sapl.server.MySqlPolicyInformationPoint;
import io.sapl.server.PostGisPolicyInformationPoint;
import picocli.CommandLine.Option;
import java.nio.file.Paths;
import java.util.List;

@Component
public class Client implements ApplicationListener<ApplicationReadyEvent> {

	
	//private final  AtomicInteger MESSAGE_ID = new AtomicInteger(0);
	//private final  Logger logger = LoggerFactory.getLogger(Client.class);
    //@Autowired
    //private ConfigurableApplicationContext applicationContext;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    final String resourceDirectory = Paths.get("src","main","resources").toFile().getAbsolutePath();
    
    @Option(names = { "-p", "--path" },
			description = "Sets the path for looking up policies and PDP configuration if the -f parameter is set. Defaults to '~/sapl/policies'")
	//String path = "~/sapl/policies";

	String path = "~/sapl/policies";
	
	@Option(names = { "-f", "--filesystem" },
			description = "If set, policies and PDP configuration are loaded from the filesystem instead of the bundled resources. Set path with -p.")
	boolean filesystem = true;
	
    
	public void onApplicationEvent(ApplicationReadyEvent event) {
		 			
		EmbeddedPolicyDecisionPoint pdp = null;
		if (filesystem) {
			/*
			 * The factory method PolicyDecisionPointFactory.filesystemPolicyDecisionPoint
			 * creates a PDP witch is retrieving the policies and its configuration form
			 * the file system.
			 *
			 * It takes a parameter with the path and lists of extensions to load.
			 *
			 * The first list contains policy information points. The second list contains
			 * function libraries.
			 *
			 * The PDP will monitor the path at runtime for any changes made to the
			 * policies an update any subscribed PEPs accordingly.
			 */
//			pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(path, () -> List.of(new HttpPolicyInformationPointFluxComplete(new ObjectMapper())), List::of, 
//                    List::of,  List::of);
			try {
				pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(path, () -> List.of(new GeoPolicyInformationPoint(MAPPER),
						new MySqlPolicyInformationPoint(MAPPER), new PostGisPolicyInformationPoint(MAPPER)),
						List::of, 
						() -> List.of(new GeoFunctions(), new GeoConverter()), 
						List::of);
			} catch (InitializationException e) {

				e.printStackTrace();
			}
		}
		else {
			/*
			 * The factory method PolicyDecisionPointFactory.resourcesPolicyDecisionPoint
			 * creates a PDP witch is retrieving the policies from the resources bundled
			 * with the application.
			 *
			 * In a typical project structure, policies are then located in the folder
			 * 'src/main/resources/policies'.
			 *
			 * The first list contains policy information points. The second list contains
			 * function libraries.
			 *
			 * The PDP will monitor the path at runtime for any changes made to the
			 * policies an update any subscribed PEPs accordingly.
			 */

			//pdp = PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(List::of, () -> List.of(HttpPolicyInformationPointFluxComplete.class),
            //        List::of, List::of);

		}

		var user1 = new User("username", 1);
		var authzSubscription1 = AuthorizationSubscription.of(user1, "login", "1");
		if(pdp != null) {
			var sub = pdp.decideTraced(authzSubscription1).doOnNext(decision -> System.out.println("Decision Test1 allow: "+ decision.getAuthorizationDecision()))
			.doOnNext(d-> System.out.println("Decision Test1 allow: " + d.getTrace().toPrettyString() + "---" + d.getAuthorizationDecision()))
			.subscribe();
			
	//    	 Mono
	//         .delay(Duration.ofSeconds(30))
	//         .publishOn(Schedulers.boundedElastic())
	//         .subscribe(value -> {
	//             //socket.disconnect();
	//        	 //pip.disconnectTraccar(1);
	//             SpringApplication.exit(applicationContext, () -> 0);
	//         });
			
			try {
				Thread.sleep(40000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				e.printStackTrace();
			}
		 
			sub.dispose();
			
			try {
				Thread.sleep(20000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				e.printStackTrace();
			}
			
			System.out.println("End");
		}
	}
}
