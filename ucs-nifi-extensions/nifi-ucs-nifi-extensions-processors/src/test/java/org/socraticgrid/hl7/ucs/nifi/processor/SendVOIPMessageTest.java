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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 * @author pavan
 */
public class SendVOIPMessageTest {
    private TestRunner testRunner;
    private ServiceStatusControllerService statusService;
    private ClientAndServer mockServer; // mock server
    
    @Before
    public void init() throws Exception {  
    	this.mockServer = startClientAndServer(8585);
        
        testRunner = TestRunners.newTestRunner(new SendVOIPMessage()); 
        
        statusService = new ServiceStatusControllerService();
        testRunner.addControllerService("service-status-controller", statusService);
        testRunner.enableControllerService(statusService);
        testRunner.setProperty(SendVOIPMessage.SERVICE_STATUS_CONTROLLER_SERVICE, "service-status-controller");

        testRunner.setProperty(SendVOIPMessage.VOIP_MSG_NUMBER, "voip.msg.number");
        testRunner.setProperty(SendVOIPMessage.VOIP_MSG_TEXT, "voip.msg.text");
        testRunner.setProperty(SendVOIPMessage.VOIP_SERVER_URL, "http://localhost:8585/1.0/sessions");//https://api.tropo.com/1.0/sessions
        testRunner.setProperty(SendVOIPMessage.VOIP_SERVER_TOKEN, "06c722edc8ace142aa61a147ee6d79237c59dfea4da548b6095063640cde76a36b1001c0887ef52f1b7e6464"); 
        
    } 
    @After
    public void doAfter(){
        this.mockServer.stop();
    }
    
    @Test
    public void doTestSendVoipMsg() throws IOException {
        String predefinedPOSTResponse = "{ success: true, token: \"06c722edc8ace142aa61a147ee6d79237c59dfea4da548b6095063640cde76a36b1001c0887ef52f1b7e6464\" ,id: \"1bf4677582dfda6b932df8d281376785 \" }";
        this.configureServerResource(mockServer, "/1.0/sessions", "POST", predefinedPOSTResponse);
        
    	Map<String, String> flowFileAttributes = new HashMap<>();
    	flowFileAttributes.put("voip.msg.number", "esteban.aliverti@sip2sip.info");// set phone number or SIP id
    	flowFileAttributes.put("voip.msg.number", "+4981614923621");// set phone number or SIP id
    	flowFileAttributes.put("voip.msg.text", "Hello...Test Voip message..."); 
    	testRunner.enqueue("test".getBytes(),flowFileAttributes);  
    	testRunner.run(); 
    	testRunner.assertAllFlowFilesTransferred(SendVOIPMessage.REL_MSG_SEND, 1);
    	MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(SendVOIPMessage.REL_MSG_SEND).get(0);
    	Assert.assertNotNull(flowFile); 
    	//parse json input 
        JsonParser jsonParser = new JsonParser();
        String jsonString = new String(flowFile.toByteArray()); 
        JsonElement jsonElement = (JsonElement) jsonParser.parse(jsonString); 
        final JsonObject jsonObj = jsonElement.getAsJsonObject();
        boolean status = jsonObj.get("success").getAsBoolean();
        Assert.assertTrue(status); 
    	System.out.println(jsonString);
    }
    
    @Test
    public void doTestSendVoipMsgFail() throws IOException {
        String predefinedPOSTResponse = "{ success: false, token: \"06c722edc8ace142aa61a147ee6d79237c59dfea4da548b6095063640cde76a36b1001c0887ef52f1b7e646455\" ,reason: \"Invalid token\" }";
    	this.configureServerResource(mockServer, "/1.0/sessions", "POST", predefinedPOSTResponse);
        
    	Map<String, String> flowFileAttributes = new HashMap<>();
    	flowFileAttributes.put("voip.msg.number", "chenduluru@sip2sip.info");
    	flowFileAttributes.put("voip.msg.text", "Hello...Test Voip message..."); 
    	
    	//set wrong token to fail
    	testRunner.setProperty(SendVOIPMessage.VOIP_SERVER_TOKEN, "06c722edc8ace142aa61a147ee6d79237c59dfea4da548b6095063640cde76a36b1001c0887ef52f1b7e646456"); 
    	
    	testRunner.enqueue("test".getBytes(),flowFileAttributes);  
    	testRunner.run(); 
    	testRunner.assertAllFlowFilesTransferred(SendVOIPMessage.REL_MSG_SEND, 1);
    	MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(SendVOIPMessage.REL_MSG_SEND).get(0);
    	Assert.assertNotNull(flowFile); 
    	//parse json input 
        JsonParser jsonParser = new JsonParser();
        String jsonString = new String(flowFile.toByteArray()); 
        JsonElement jsonElement = (JsonElement) jsonParser.parse(jsonString); 
        final JsonObject jsonObj = jsonElement.getAsJsonObject();
        boolean status = jsonObj.get("success").getAsBoolean();
        Assert.assertFalse(status); 
        String reason = jsonObj.get("reason").getAsString();
        Assert.assertTrue(reason.equals("Invalid token")); 
    	System.out.println(jsonString);
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
