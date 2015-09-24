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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;

/**
 *
 * @author esteban
 */
public class UCSCreateConversationTest extends UCSControllerServiceBasedTest {
    
    @Test
    public void successTest() throws MessageSerializationException{
        String conversationId = UUID.randomUUID().toString();
        
        //check that there is no conversation with the specified id on UCSController
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(false));
        
        Conversation c = new Conversation();
        c.setConversationId(conversationId);

        Map<String, String> attributes = this.createBasicAttributes(c);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSCreateConversation.REL_SUCCESS);
        
        //the conversation was persisted in the store.
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(true));

    }
    
    @Test
    public void emptyConversationIdTest() throws MessageSerializationException{
        
        //check that there is no conversation on UCSController
        assertThat(this.controller.queryConversations(null, null), hasSize(0));
        
        Conversation c = new Conversation();
        c.setConversationId(null);

        Map<String, String> attributes = this.createBasicAttributes(c);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSCreateConversation.REL_SUCCESS);
        
        //the conversation was persisted in the store.
        List<Conversation> conversations = this.controller.queryConversations(null, null);
        assertThat(conversations, hasSize(1));
        assertThat(conversations.get(0).getConversationId(), not(nullValue()));

    }
    
    @Test
    public void duplicatedConversationIdTest() throws MessageSerializationException{
        String conversationId = UUID.randomUUID().toString();
        
        //check that there is no conversation with the specified id on UCSController
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(false));
        
        Conversation c = new Conversation();
        c.setConversationId(conversationId);
        
        //persist the conversation in the store
        this.controller.saveConversation(c);

        Map<String, String> attributes = this.createBasicAttributes(c);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSCreateConversation.REL_FAILURE);
        
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSCreateConversation());
    }
    
    
    private Map<String, String> createBasicAttributes(Conversation conversation) throws MessageSerializationException {
        Map<String, String> result = this.createBasicAttributes();
        if (conversation != null){
            result.put("command.args", Base64.encodeBase64String(ConversationSerializer.serializeConversationWrapper(new ConversationWrapper(conversation)).getBytes()));
        }
        
        return result;
    }
    
    private Map<String, String> createBasicAttributes(){
        Map<String, String> result = new HashMap<>();
        result.put("command.name", "createConversation");
        
        return result;
    }
}
