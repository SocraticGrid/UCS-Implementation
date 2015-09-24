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
import java.util.Arrays;
import java.util.UUID;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSConvertSMSResponseToMessageTest extends UCSControllerServiceBasedTest{

    @Test
    public void testNoReference() throws MessageSerializationException {

        //A response without 'Reference' attribute
        String smsResponse = "{\n"
                + "       \"AccountKey\": GDE442Hvv2,\n"
                + "       \"Message\": \"Info\",\n"
                + "       \"MessageNumber\": 2568135,\n"
                + "       \"OutgoingMessageID\": 62567,\n"
                + "       \"PhoneNumber\": \"17015559776\",\n"
                + "       \"ReceivedDate\": \"/Date(1340751528000-0400)/\"\n"
                + "   }";

        testRunner.enqueue(smsResponse.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_NO_MATCH, 1);
    }

    @Test
    public void testEmptyReference() throws MessageSerializationException {

        //A response with an empty 'Reference' attribute
        String smsResponse = "{\n"
                + "       \"AccountKey\": GDE442Hvv2,\n"
                + "       \"Message\": \"Info\",\n"
                + "       \"MessageNumber\": 2568135,\n"
                + "       \"OutgoingMessageID\": 62567,\n"
                + "       \"PhoneNumber\": \"17015559776\",\n"
                + "       \"ReceivedDate\": \"/Date(1340751528000-0400)/\",\n"
                + "       \"Reference\": \"\"\n"
                + "   }";

        testRunner.enqueue(smsResponse.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_NO_MATCH, 1);
    }

    @Test
    public void testNonExistingReference() throws MessageSerializationException {

        //A response with a non existing 'Reference' attribute
        String smsResponse = "{\n"
                + "       \"AccountKey\": GDE442Hvv2,\n"
                + "       \"Message\": \"Info\",\n"
                + "       \"MessageNumber\": 2568135,\n"
                + "       \"OutgoingMessageID\": 62567,\n"
                + "       \"PhoneNumber\": \"17015559776\",\n"
                + "       \"ReceivedDate\": \"/Date(1340751528000-0400)/\",\n"
                + "       \"Reference\": \"Non-Existing\"\n"
                + "   }";

        testRunner.enqueue(smsResponse.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_NO_MATCH, 1);
    }

    @Test
    public void testHappyPath() throws MessageSerializationException, IOException {
        
        MessageWrapper originalMessageWrapper = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .buildMessageWrapper();

        String recipientId = UUID.randomUUID().toString();
        String reference = UUID.randomUUID().toString();

        originalMessageWrapper.getMessage().getHeader().getRecipientsList().iterator().next().setRecipientId(recipientId);
        controller.saveMessage(originalMessageWrapper.getMessage());
        controller.saveMessageReference(originalMessageWrapper.getMessage(), recipientId, reference);

        //A response without 'Reference' attribute
        String smsResponse = "{\n"
                + "       \"AccountKey\": GDE442Hvv2,\n"
                + "       \"Message\": \"Yes, please\",\n"
                + "       \"MessageNumber\": 2568135,\n"
                + "       \"OutgoingMessageID\": 62567,\n"
                + "       \"PhoneNumber\": \"17015559776\",\n"
                + "       \"ReceivedDate\": \"/Date(1340751528000-0400)/\",\n"
                + "       \"Reference\": \"" + reference + "\"\n"
                + "   }";

        testRunner.enqueue(smsResponse.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_SUCCESS, 1);

        Message message = MessageSerializer.deserializeMessageWrapper(
                new String(testRunner.getFlowFilesForRelationship(UCSUpdateMessageDeliveryStatus.REL_SUCCESS).get(0).toByteArray())
        ).getMessage();
        
        //The sender is the original recipient. In a real case scneario, the address should be resolved.
        assertThat(message.getHeader().getSender().getPhysicalAddress().getAddress(), is("ealiverti"));
        
        //The recipient is the original sender.
        assertThat(message.getHeader().getRecipientsList().iterator().next().getDeliveryAddress().getPhysicalAddress().getAddress(), is("eafry"));
        
        //The body of the message is the SMS response
        assertThat(Arrays.asList(message.getParts()), hasSize(1));
        assertThat(message.getParts()[0].getType(), is("text/plain"));
        assertThat(message.getParts()[0].getContent(), is("Yes, please"));
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSConvertSMSResponseToMessage());
    }
}
