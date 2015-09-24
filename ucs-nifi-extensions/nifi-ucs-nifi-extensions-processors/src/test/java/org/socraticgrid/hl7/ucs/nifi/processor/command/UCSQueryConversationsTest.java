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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static java.util.stream.Collectors.toSet;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;

/**
 *
 * @author esteban
 */
public class UCSQueryConversationsTest extends UCSControllerServiceBasedTest {

    @Test
    public void emptyConversationsTest() throws MessageSerializationException{
        String conversationId = UUID.randomUUID().toString();
        
        //check that there is no conversation with the specified id on UCSController
        assertThat(this.controller.getConversationById(conversationId).isPresent(), is(false));
        
        Map<String, String> attributes = this.createBasicAttributes();

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSCreateConversation.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSCreateConversation.REL_SUCCESS).get(0);
        
        XMLListWrapper<ConversationWrapper> deserializedConversationWrappers = ConversationSerializer.deserializeConversationWrappers(new ByteArrayInputStream(testRunner.getContentAsByteArray(flowFile)));
        
        assertThat(deserializedConversationWrappers.getItems(), hasSize(0));

    }
    
    @Test
    public void twoConversationsTest() throws MessageSerializationException{

        Conversation c1 = new Conversation();
        c1.setConversationId(UUID.randomUUID().toString());
        controller.saveConversation(c1);
        
        Conversation c2 = new Conversation();
        c2.setConversationId(UUID.randomUUID().toString());
        controller.saveConversation(c2);
        
        Map<String, String> attributes = this.createBasicAttributes();

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSCreateConversation.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSCreateConversation.REL_SUCCESS).get(0);
        
        XMLListWrapper<ConversationWrapper> deserializedConversationWrappers = ConversationSerializer.deserializeConversationWrappers(new ByteArrayInputStream(testRunner.getContentAsByteArray(flowFile)));
        
        assertThat(deserializedConversationWrappers.getItems(), hasSize(2));
        assertThat(
                deserializedConversationWrappers.getItems().stream()
                        .map(cw -> cw.getConversation()
                        .getConversationId())
                        .collect(toSet()), 
                containsInAnyOrder(c1.getConversationId(), c2.getConversationId()));

    }
    
    private Map<String, String> createBasicAttributes(){
        Map<String, String> result = new HashMap<>();
        result.put("command.name", "queryConversations");
        
        return result;
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSQueryConversations());
    }
    
}
