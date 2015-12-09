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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusControllerService;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatControllerService;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessage;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessageSerializer;

/**
 *
 * @author pavan
 */
public class ReceiveChatMessageTest {

    private TestRunner testRunner;
    private ChatControllerService controller;
    private ServiceStatusControllerService statusService;

    @Before
    public void init() throws Exception {

        controller = spy(new ChatControllerService());
        
        testRunner = TestRunners.newTestRunner(new ReceiveChatMessage());
        statusService = new ServiceStatusControllerService();
        testRunner.addControllerService("service-status-controller", statusService);
        testRunner.enableControllerService(statusService);

        Map<String, String> chatControllerServiceConfig = new HashMap<>();
        chatControllerServiceConfig.put(ChatControllerService.CHAT_SERVER_URL.getName(), "mock");
        chatControllerServiceConfig.put(ChatControllerService.CHAT_SERVER_USERNAME.getName(), "mock");
        chatControllerServiceConfig.put(ChatControllerService.CHAT_SERVER_PASSWORD.getName(), "mock");
        chatControllerServiceConfig.put(ChatControllerService.SERVICE_STATUS_CONTROLLER_SERVICE.getName(), "service-status-controller");

        //we don't want to connect to a real chat server
        doNothing().when(controller).onEnabled(anyObject());

        //these are the messages we want to return
        when(controller.consumeMessages())
            .thenReturn(Arrays.asList(
                ChatMessageBuilder.fromValues("1", "me", "This is message 1", "chat room 1"),
                ChatMessageBuilder.fromValues("2", "me", "This is message 2", "chat room 2")
        ).stream().collect(Collectors.toSet()))
            .thenReturn(Arrays.asList(
                ChatMessageBuilder.fromValues("3", "me", "This is message 3", "chat room 1"),
                ChatMessageBuilder.fromValues("4", "me", "This is message 4", "chat room 2")
        ).stream().collect(Collectors.toSet()))
            .thenReturn(new HashSet<>());

        testRunner.addControllerService("chat-controller", controller, chatControllerServiceConfig);
        testRunner.enableControllerService(controller);

        testRunner.setProperty(ReceiveChatMessage.CHAT_CONTROLLER_SERVICE, "chat-controller");

    }

    @Test
    public void doTestSendChatMessage() throws IOException {

        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(ReceiveChatMessage.REL_SUCCESS, 2);
        Set<ChatMessage> messages = testRunner.getFlowFilesForRelationship(SendDynamicGroupChatMessage.REL_CHAT_SEND).stream()
                .map(ff -> {
                    try {
                        return ChatMessageSerializer.deserializeChatMessage(new String(ff.toByteArray()));
                    } catch (MessageSerializationException ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .collect(Collectors.toSet());

        assertThat(messages.stream()
                .map(m -> m.getId())
                .collect(Collectors.toSet()),
                containsInAnyOrder("1", "2")
        );
        
        
        //let's invoke the processor again
        testRunner.clearTransferState();
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(ReceiveChatMessage.REL_SUCCESS, 2);
        messages = testRunner.getFlowFilesForRelationship(SendDynamicGroupChatMessage.REL_CHAT_SEND).stream()
                .map(ff -> {
                    try {
                        return ChatMessageSerializer.deserializeChatMessage(new String(ff.toByteArray()));
                    } catch (MessageSerializationException ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .collect(Collectors.toSet());

        assertThat(messages.stream()
                .map(m -> m.getId())
                .collect(Collectors.toSet()),
                containsInAnyOrder("3", "4")
        );
        
        //and again
        testRunner.clearTransferState();
        testRunner.run();
        
        testRunner.assertTransferCount(ReceiveChatMessage.REL_SUCCESS, 0);
        testRunner.assertTransferCount(ReceiveChatMessage.REL_FAILURE, 0);

    }
}
