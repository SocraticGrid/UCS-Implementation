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
package org.socraticgrid.hl7.ucs.nifi.processor.command;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationInfoWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationInfoSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;

/**
 *
 * @author esteban
 */
public class UCSRetrieveConversationTest extends UCSControllerServiceBasedTest {

    @Test
    public void unknownConversationTest() throws MessageSerializationException{
        String conversationId = UUID.randomUUID().toString();
        
        //check that there is no conversation with the specified id on UCSController
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(false));
        
        Map<String, String> attributes = this.createBasicAttributes(conversationId);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSRetrieveConversation.REL_NOT_FOUND, 1);
        
    }
    
    @Test
    public void knownConversationNoMessagesTest() throws MessageSerializationException{
        String conversationId = UUID.randomUUID().toString();
        
        //check that there is no conversation with the specified id on UCSController
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(false));
        
        Conversation c = new Conversation();
        c.setConversationId(conversationId);
        
        this.controller.saveConversation(c);
        
        Map<String, String> attributes = this.createBasicAttributes(conversationId);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSRetrieveConversation.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSRetrieveConversation.REL_SUCCESS).get(0);
        
        ConversationInfoWrapper miw = ConversationInfoSerializer.deserializeConversationInfoWrapper(new ByteArrayInputStream(testRunner.getContentAsByteArray(flowFile)));
        
        assertThat(miw.getConversationInfo().getConversation().getConversationId(), is(conversationId));
        assertThat(miw.getConversationInfo().getMessages(), is(nullValue()));
        
    }
    
    @Test
    public void knownConversationTest() throws MessageSerializationException, IOException{
        String conversationId = UUID.randomUUID().toString();
        String message1Id = UUID.randomUUID().toString();
        String message2Id = UUID.randomUUID().toString();
        String message3Id = UUID.randomUUID().toString();
        
        //check that there is no conversation with the specified id on UCSController
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(false));
        
        Conversation c = new Conversation();
        c.setConversationId(conversationId);
        this.controller.saveConversation(c);
        
        MessageBuilder nestedMessageBuilder = new MessageBuilder()
                .withConversationId(conversationId)
                .withMessageId(message3Id)
                .withSender("eafry")
                .withSubject("Nested Message")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        MessageWrapper message1 = new MessageBuilder()
                .withConversationId(conversationId)
                .withMessageId(message1Id)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .addOnFailureToReachAll(nestedMessageBuilder)
                .addOnFailureToReachAny(nestedMessageBuilder)
                .addOnNoResponseAll(nestedMessageBuilder)
                .addOnNoResponseAny(nestedMessageBuilder)
                .buildMessageWrapper();
        
        this.controller.saveMessage(message1.getMessage());
        
        MessageWrapper message2 = new MessageBuilder()
                .withConversationId(conversationId)
                .withMessageId(message2Id)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .addOnFailureToReachAll(nestedMessageBuilder)
                .addOnFailureToReachAny(nestedMessageBuilder)
                .addOnNoResponseAll(nestedMessageBuilder)
                .addOnNoResponseAny(nestedMessageBuilder)
                .buildMessageWrapper();
        this.controller.saveMessage(message2.getMessage());
        
        
        Map<String, String> attributes = this.createBasicAttributes(conversationId);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSRetrieveConversation.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSRetrieveConversation.REL_SUCCESS).get(0);
        
        ConversationInfoWrapper miw = ConversationInfoSerializer.deserializeConversationInfoWrapper(new ByteArrayInputStream(testRunner.getContentAsByteArray(flowFile)));
        
        assertThat(miw.getConversationInfo().getConversation().getConversationId(), is(conversationId));
        assertThat(miw.getConversationInfo().getMessages(), hasSize(2));
        
        assertThat(miw.getConversationInfo().getMessages(), containsInAnyOrder(message1Id, message2Id));
        
    }
    
    private Map<String, String> createBasicAttributes(String conversationId){
        Map<String, String> result = this.createBasicAttributes();
        result.put("command.args", conversationId);
        
        return result;
    }
    
    private Map<String, String> createBasicAttributes(){
        Map<String, String> result = new HashMap<>();
        result.put("command.name", "retrieveConversation");
        
        return result;
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSRetrieveConversation());
    }
    
}
