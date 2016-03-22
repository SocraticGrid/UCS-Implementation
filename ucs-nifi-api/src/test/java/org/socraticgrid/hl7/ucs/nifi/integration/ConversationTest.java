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
package org.socraticgrid.hl7.ucs.nifi.integration;

import java.util.UUID;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Ignore;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidConversationException;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.ConversationInfo;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;

/**
 *
 * @author esteban
 */
public class ConversationTest extends BaseIntegrationTest {
    
    @Test
    public void createConversationTest() throws Exception {
        
        Conversation c = new Conversation();
        c.setConversationId(UUID.randomUUID().toString());
        
        String conversationId = this.conversation.createConversation(c);
        
        //The conversation kept its id
        assertThat(conversationId, is(c.getConversationId()));
    }
    
    @Test
    public void createConversationWithNoIdTest() throws Exception {
        
        Conversation c = new Conversation();
        c.setConversationId(null);
        
        String conversationId = this.conversation.createConversation(c);
        
        //The conversation kept its id
        assertThat(conversationId, not(nullValue()));
    }
    
    @Test
    public void createConversationWithDuplicatedIdTest() throws Exception {
        
        String conversationId = UUID.randomUUID().toString();
        
        Conversation c = new Conversation();
        c.setConversationId(conversationId);
        
        this.conversation.createConversation(c);
        
        try{
            this.conversation.createConversation(c);
            fail("Exception expected!");
        } catch (InvalidConversationException e){
            //expected
        }
    }
    
    @Test
    public void retrieveConversationTest() throws Exception {
        
        Conversation c = new Conversation();
        c.setConversationId(UUID.randomUUID().toString());
        
        this.conversation.createConversation(c);
        
        ConversationInfo convFromUCS = this.conversation.retrieveConversation(c.getConversationId());
        
        assertThat(convFromUCS.getConversation().getConversationId(), is (c.getConversationId()));
        assertThat(convFromUCS.getMessages(), is (nullValue()));
        
    }
    
    @Test
    public void retrieveConversationWithMessagesTest() throws Exception {
        String message1Id = UUID.randomUUID().toString();
        String message2Id = UUID.randomUUID().toString();
        
        Conversation c = new Conversation();
        c.setConversationId(UUID.randomUUID().toString());
        
        this.conversation.createConversation(c);
        
        
        MessageWrapper messageWrapper1 = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withConversationId(c.getConversationId())
                .withMessageId(message1Id)
                .withSubject("Subject 1")
                .withBody("Body 1")
                .withSender("eafry")
                .buildMessageWrapper();
        
        MessageWrapper messageWrapper2 = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withConversationId(c.getConversationId())
                .withMessageId(message2Id)
                .withSubject("Subject 2")
                .withBody("Body 2")
                .withSender("eafry")
                .buildMessageWrapper();
        
        client.sendMessage(new MessageModel(messageWrapper1.getMessage()));
        client.sendMessage(new MessageModel(messageWrapper2.getMessage()));
        this.sleep();
        
        this.assertListsSizes(2, 0, 0, 0);
        
        ConversationInfo convFromUCS = this.conversation.retrieveConversation(c.getConversationId());
        
        assertThat(convFromUCS.getConversation().getConversationId(), is (c.getConversationId()));
        assertThat(convFromUCS.getMessages(), hasSize(2));
        assertThat(convFromUCS.getMessages(), containsInAnyOrder(message1Id, message2Id));
        
    }
    
    @Test
    public void retrieveNonExistentConversationTest() throws Exception {
        
        Conversation c = new Conversation();
        c.setConversationId(UUID.randomUUID().toString());
        
        try{
            this.conversation.retrieveConversation(c.getConversationId());
            fail("Exception expected!");
        } catch (InvalidConversationException e){
            //expected
        }
    }
    
}
