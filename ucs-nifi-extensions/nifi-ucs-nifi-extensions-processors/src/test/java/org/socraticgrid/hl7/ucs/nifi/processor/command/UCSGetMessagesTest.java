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

import java.io.IOException;
import java.util.stream.Collectors;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSGetMessagesTest extends UCSControllerServiceBasedTest {
    
    @Test
    public void doTestGetAllMessagesAtOnce() throws MessageSerializationException, IOException{
        
        assertThat(controller.listMessages().isEmpty(), is(true));

        String messageId1 = "ff26b0af-0b7d-47ee-89f9-06ed13cf7fee";
        String messageId2 = "aaa6b0af-0b7d-47ee-89f9-06ed13cf7ccc";
        
        //Persist 2 messages
        MessageWrapper messageWrapper1 = new MessageBuilder()
                .withConversationId("testC")
                .withMessageId(messageId1)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .buildMessageWrapper();
        
        MessageWrapper messageWrapper2 = new MessageBuilder()
                .withConversationId("testC")
                .withMessageId(messageId2)
                .withSender("ealiverti")
                .withSubject("Some Other Subject")
                .withBody("Some Other Body")
                .addRecipient(new MessageBuilder.Recipient("eafry", "EMAIL"))
                .buildMessageWrapper();
        
        
        controller.saveMessage(messageWrapper1.getMessage());
        controller.saveMessage(messageWrapper2.getMessage());
        
        assertThat(controller.listMessages().size(), is(2));
        
        //we want all the messages in one FlowFile
        testRunner.setProperty(UCSGetMessages.UNIQUE_FLOWFILE, "true");
        
        testRunner.enqueue();
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSGetMessages.REL_SUCCESS, 1);
        
        MockFlowFile result = testRunner.getFlowFilesForRelationship(UCSGetMessages.REL_SUCCESS).get(0);
        
        String resultContent = new String(result.toByteArray());

        XMLListWrapper<MessageWrapper> deserializeMessageWrappers = MessageSerializer.deserializeMessageWrappers(resultContent);
        
        assertThat(deserializeMessageWrappers.getItems().size(), is(2));
        
        assertThat(deserializeMessageWrappers.getItems().stream()
                .map(mw -> mw.getMessage().getHeader().getMessageId())
                .collect(Collectors.toSet()),
                containsInAnyOrder(messageId1, messageId2)
        );
        
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSGetMessages());
    }
}
