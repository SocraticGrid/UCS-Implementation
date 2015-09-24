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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusControllerService;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatControllerService;

/**
 *
 * @author pavan
 */
public class SendDynamicGroupChatMessageTest {
    private TestRunner testRunner; 
    private ChatControllerService controller;
    private ServiceStatusControllerService statusService;
    
    private List<MessageToDynamicRoom> messages = new ArrayList<>();

    @Before
    public void init() throws Exception {  
    	controller = spy(new ChatControllerService());
    	
    	testRunner = TestRunners.newTestRunner(new SendDynamicGroupChatMessage());
    	statusService = new ServiceStatusControllerService();
        testRunner.addControllerService("service-status-controller", statusService);
        testRunner.enableControllerService(statusService);
        
        Map<String, String> chatControllerServiceConfig = new HashMap<>();
        chatControllerServiceConfig.put(ChatControllerService.CHAT_SERVER_URL.getName(), "mock");
        chatControllerServiceConfig.put(ChatControllerService.CHAT_SERVER_USERNAME.getName(), "mock");
        chatControllerServiceConfig.put(ChatControllerService.CHAT_SERVER_PASSWORD.getName(), "mock");
        chatControllerServiceConfig.put(ChatControllerService.SERVICE_STATUS_CONTROLLER_SERVICE.getName(), "service-status-controller");
        
        //we want to store any generated message into 'messages'
        doAnswer((Answer) (InvocationOnMock invocation) -> {
            MessageToDynamicRoom m = new MessageToDynamicRoom();
            m.chatRoomId = invocation.getArgumentAt(0, String.class);
            m.roomSubject = invocation.getArgumentAt(1, String.class);
            m.message = invocation.getArgumentAt(2, String.class);
            m.sender = invocation.getArgumentAt(3, String.class);
            
            m.participants = new ArrayList<>();
            invocation.getArgumentAt(4, List.class).stream()
                    .forEach(p -> m.participants.add((String)p));
            
            messages.add(m);
            return null;
        }).when(controller).sendMessageToDynamicRoom(anyObject(),anyObject(),anyObject(),anyObject(),anyObject()); 
        
        testRunner.setProperty(SendDynamicGroupChatMessage.CHAT_NEW_GROUP_NAME, "${chat.group.name}");
        testRunner.setProperty(SendDynamicGroupChatMessage.CHAT_NEW_GROUP_SUBJECT, "${chat.group.subject}");
        testRunner.setProperty(SendDynamicGroupChatMessage.CHAT_SENDER, "${chat.sender}");
        testRunner.setProperty(SendDynamicGroupChatMessage.CHAT_PARTICIPANTS, "${chat.participants}");
        testRunner.setProperty(SendDynamicGroupChatMessage.CHAT_MESSAGE, "${chat.message}");
        
        testRunner.addControllerService("chat-controller", controller, chatControllerServiceConfig);
        testRunner.enableControllerService(controller);

        testRunner.setProperty(SendDynamicGroupChatMessage.CHAT_CONTROLLER_SERVICE, "chat-controller");
        
    }
    
    @Test
    public void doTestSendChatMessage() throws IOException {
    	Map<String, String> flowFileAttributes = new HashMap<>();
    	flowFileAttributes.put("chat.group.name", "Test@conference.socraticgrid.org");
    	flowFileAttributes.put("chat.group.subject", "Test Group 2");
    	flowFileAttributes.put("chat.message", "Hi... message test");
    	flowFileAttributes.put("chat.sender", "pavantest@socraticgrid.org");
    	flowFileAttributes.put("chat.participants", "[{\"participant\": \"pavan@socraticgrid.org\" },{\"participant\": \"ealiverti@socraticgrid.org\"}]"); 
        
    	testRunner.enqueue("test".getBytes(),flowFileAttributes);  
    	testRunner.run(); 
        
    	testRunner.assertAllFlowFilesTransferred(SendDynamicGroupChatMessage.REL_CHAT_SEND, 1);
    	MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(SendDynamicGroupChatMessage.REL_CHAT_SEND).get(0);
    	assertThat(flowFile, not(nullValue()));
        assertThat(messages, hasSize(1));
        
        assertThat(messages.get(0).chatRoomId, is("Test@conference.socraticgrid.org"));
        assertThat(messages.get(0).roomSubject, is("Test Group 2"));
        assertThat(messages.get(0).message, is("Hi... message test"));
        assertThat(messages.get(0).sender, is("pavantest@socraticgrid.org"));
        assertThat(messages.get(0).participants, containsInAnyOrder("pavan@socraticgrid.org", "ealiverti@socraticgrid.org"));
        
        
        messages.clear();
        
        testRunner.clearTransferState();
        
        //send another message to the same room
        flowFileAttributes.put("chat.message", "This is another test message!");
        
        testRunner.enqueue("test".getBytes(),flowFileAttributes);  
    	testRunner.run(); 
        
    	testRunner.assertAllFlowFilesTransferred(SendDynamicGroupChatMessage.REL_CHAT_SEND, 1);
    	flowFile = testRunner.getFlowFilesForRelationship(SendDynamicGroupChatMessage.REL_CHAT_SEND).get(0);
    	assertThat(flowFile, not(nullValue()));
        assertThat(messages, hasSize(1));
        
        assertThat(messages.get(0).chatRoomId, is("Test@conference.socraticgrid.org"));
        assertThat(messages.get(0).roomSubject, is("Test Group 2"));
        assertThat(messages.get(0).message, is("This is another test message!"));
        assertThat(messages.get(0).sender, is("pavantest@socraticgrid.org"));
        assertThat(messages.get(0).participants, containsInAnyOrder("pavan@socraticgrid.org", "ealiverti@socraticgrid.org"));
        
    }
}

class MessageToDynamicRoom {
    public String chatRoomId;
    public String roomSubject;
    public String message;
    public String sender;
    public List<String> participants;
}