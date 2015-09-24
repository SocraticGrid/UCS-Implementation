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

import java.io.IOException;
import java.util.UUID;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessage;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSConvertChatResponseToMessageTest extends UCSControllerServiceBasedTest{

    @Test
    public void testSuccess1() throws MessageSerializationException, IOException {
        
        String conversationId = "conv1";
        
        MessageWrapper messageWrapper = new MessageBuilder()
                .withConversationId(conversationId)
                .buildMessageWrapper();
        
        controller.saveMessage(messageWrapper.getMessage());
        
        ChatMessage chatMessage = ChatMessageBuilder.fromValues(UUID.randomUUID().toString(), "me", "message 1", conversationId);
        
        testRunner.enqueue(ChatMessageSerializer.serializeChatMessage(chatMessage).getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSConvertChatResponseToMessage.REL_SUCCESS, 1);
        
        Message newMessage = MessageSerializer.deserializeMessageWrapper(
                new String(testRunner.getFlowFilesForRelationship(UCSConvertChatResponseToMessage.REL_SUCCESS).get(0).toByteArray())
        ).getMessage();
        
        assertThat(newMessage.getHeader().getRelatedConversationId(), is(conversationId));
        
    }
    
    @Test
    public void testSuccess2() throws MessageSerializationException, IOException {
        
        String groupSufix = "@socraticgrid.org";
        testRunner.setProperty(UCSConvertChatResponseToMessage.GROUP_SUFIX, groupSufix);
        
        String conversationId = "conv1";
        
        MessageWrapper messageWrapper = new MessageBuilder()
                .withConversationId(conversationId)
                .buildMessageWrapper();
        
        controller.saveMessage(messageWrapper.getMessage());
        
        ChatMessage chatMessage = ChatMessageBuilder.fromValues(UUID.randomUUID().toString(), "me", "message 1", conversationId+groupSufix);
        
        testRunner.enqueue(ChatMessageSerializer.serializeChatMessage(chatMessage).getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSConvertChatResponseToMessage.REL_SUCCESS, 1);
        
        Message newMessage = MessageSerializer.deserializeMessageWrapper(
                new String(testRunner.getFlowFilesForRelationship(UCSConvertChatResponseToMessage.REL_SUCCESS).get(0).toByteArray())
        ).getMessage();
        
        assertThat(newMessage.getHeader().getRelatedConversationId(), is(conversationId+groupSufix));
        
    }
    
    @Test
    public void testSuccess3() throws MessageSerializationException, IOException {
        
        String groupSufix = "@socraticgrid.org";
        testRunner.setProperty(UCSConvertChatResponseToMessage.GROUP_SUFIX, groupSufix);
        
        String conversationId = "conv1";
        
        MessageWrapper messageWrapper = new MessageBuilder()
                .withConversationId(conversationId+groupSufix)
                .buildMessageWrapper();
        
        controller.saveMessage(messageWrapper.getMessage());
        
        ChatMessage chatMessage = ChatMessageBuilder.fromValues(UUID.randomUUID().toString(), "me", "message 1", conversationId);
        
        testRunner.enqueue(ChatMessageSerializer.serializeChatMessage(chatMessage).getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSConvertChatResponseToMessage.REL_SUCCESS, 1);
        
        Message newMessage = MessageSerializer.deserializeMessageWrapper(
                new String(testRunner.getFlowFilesForRelationship(UCSConvertChatResponseToMessage.REL_SUCCESS).get(0).toByteArray())
        ).getMessage();
        
        assertThat(newMessage.getHeader().getRelatedConversationId(), is(conversationId));
        
    }
    
    @Test
    public void testNoMatch() throws MessageSerializationException {
        
        ChatMessage chatMessage = ChatMessageBuilder.fromValues(UUID.randomUUID().toString(), "me", "message 1", "room A");
        
        testRunner.enqueue(ChatMessageSerializer.serializeChatMessage(chatMessage).getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSConvertChatResponseToMessage.REL_NO_MATCH, 1);
        
        Message newMessage = MessageSerializer.deserializeMessageWrapper(
                new String(testRunner.getFlowFilesForRelationship(UCSConvertChatResponseToMessage.REL_NO_MATCH).get(0).toByteArray())
        ).getMessage();
        
        assertThat(newMessage.getHeader().getRelatedConversationId(), is(nullValue()));
        
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSConvertChatResponseToMessage());
    }
    
}
