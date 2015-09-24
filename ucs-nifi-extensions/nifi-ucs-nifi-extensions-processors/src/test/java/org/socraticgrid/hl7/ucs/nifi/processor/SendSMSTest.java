/* 
 * Copyright 2015 Cognitive Medical Systems, Inc (http://www.cognitivemedciine.com).
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusControllerService;

/**
 *
 * @author pavan
 */
public class SendSMSTest {
    private TestRunner testRunner; 
    private ServiceStatusControllerService statusService;
    private ClientAndServer mockServer; // mock server

    private final String predefinedPOSTResponse = "{\"SendMessageExtendedResult\": {\"ErrorMessage\": \"\",\"MessageID\": 42476881,\"MessagesRemaining\": 482,\"QueuedSuccessfully\": true}}";
    
    @Before
    public void init() throws Exception {
    	this.mockServer = startClientAndServer(8585);
        this.configureServerResource(mockServer, "/services/message.svc/XAO706fy87/918056515755/Extended", "POST", predefinedPOSTResponse);
        
        testRunner = TestRunners.newTestRunner(new SendSMS());  

        statusService = new ServiceStatusControllerService();
        testRunner.addControllerService("service-status-controller", statusService);
        testRunner.enableControllerService(statusService);
        testRunner.setProperty(SendSMS.SERVICE_STATUS_CONTROLLER_SERVICE, "service-status-controller");
        
        testRunner.setProperty(SendSMS.SMS_NUMBER, "sms.number");
        testRunner.setProperty(SendSMS.SMS_SERVER_URL, "http://localhost:8585/services/message.svc/");
        testRunner.setProperty(SendSMS.SMS_SERVER_ACCOUNT_KEY, "XAO706fy87");
        testRunner.setProperty(SendSMS.SMS_REFERENCE, "sms.reference");
        testRunner.setProperty(SendSMS.SMS_TEXT, "sms.text");
    }
    @After
    public void doAfter(){
        this.mockServer.stop();
    }
    
    @Test
    public void doTestGetSMS() throws IOException {
    	Map<String, String> flowFileAttributes = new HashMap<>();
    	flowFileAttributes.put("sms.number", "918056515755");
    	flowFileAttributes.put("sms.reference", "123");
    	flowFileAttributes.put("sms.text", "Good Evening..."); 
    	testRunner.enqueue("test".getBytes(),flowFileAttributes);  
    	testRunner.run(); 
    	testRunner.assertAllFlowFilesTransferred(SendSMS.REL_SMS_SEND, 1);
    	MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(SendSMS.REL_SMS_SEND).get(0);
    	Assert.assertNotNull(flowFile); 
    	//String content = new String(flowFile.toByteArray()); 
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
