/* 
 * Copyright 2015 Cognitive Medical Systems, Inc (http://www.cognitivemedicine.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socraticgrid.hl7.ucs.nifi.processor;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import java.util.List;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.socraticgrid.hl7.ucs.nifi.controller.SMSControllerService;

/**
 *
 * @author pavan
 */
public class GetSMSTest {
    private TestRunner testRunner;
    private SMSControllerService controller;
    private ClientAndServer mockServer; // mock server

    private final String predefinedPOSTResponse = "[{\"AccountKey\": GDE442Hvv2,\"Message\": \"Info\",\"MessageNumber\": 2591508,\"OutgoingMessageID\": 62566,"
    		+ "\"PhoneNumber\": \"15085556527\","
      +" \"ReceivedDate\": \"/Date(1341335593000-0400)/\","
      +" \"Reference\": \"Job Interview\"},{\"AccountKey\": GDE442Hvv2,"
      +" \"Message\": \"Info\",\"MessageNumber\": 2568135,\"OutgoingMessageID\": 62567, \"PhoneNumber\": \"17015559776\",\"ReceivedDate\":"
      + " \"/Date(1340751528000-0400)/\", \"Reference\": \"Job Interview\"}]";

    @Before
    public void init() throws Exception {  

        this.mockServer = startClientAndServer(8585);
        this.configureServerResource(mockServer, "/services/incoming.svc/XAO706fy87/count/10", "GET", predefinedPOSTResponse);
        
        controller = new SMSControllerService();

        testRunner = TestRunners.newTestRunner(new GetSMS());

        testRunner.addControllerService("sms-controller", controller);
        testRunner.enableControllerService(controller);
        
        testRunner.setProperty(GetSMS.SMS_CONTROLLER_SERVICE, "sms-controller");
        testRunner.setProperty(GetSMS.SMS_MESSAGE_COUNT, "10");
        testRunner.setProperty(GetSMS.SMS_SERVER_URL, "http://localhost:8585/services/incoming.svc/");//http://smsgateway.ca/services/incoming.svc/
        testRunner.setProperty(GetSMS.SMS_SERVER_ACCOUNT_KEY, "XAO706fy87");
    }
    
    @After
    public void doAfter(){
        this.mockServer.stop();
    }
    
    @Test
    public void doTestGetSMS() throws Exception {
        
    	  testRunner.run();
          
          testRunner.assertAllFlowFilesTransferred(GetSMS.REL_SMS_RECEIVED, 2);
          List<MockFlowFile> flowFiles = testRunner.getFlowFilesForRelationship(GetSMS.REL_SMS_RECEIVED);
          Assert.assertNotNull(flowFiles);
          Assert.assertTrue(flowFiles.size()>0);
         // String content = new String(flowFile.toByteArray()); 
    }
    
    private void configureServerResource(ClientAndServer server, String url, String operation, String body) {
        server.when(
                HttpRequest.request()
                .withMethod(operation.toUpperCase())
                .withPath(url)
        )
                .respond(
                        HttpResponse.response()
                        .withStatusCode(200)
                        .withHeader(new Header("Content-Type", "application/json; charset=utf-8"))
                        .withBody(body));
    }
}
