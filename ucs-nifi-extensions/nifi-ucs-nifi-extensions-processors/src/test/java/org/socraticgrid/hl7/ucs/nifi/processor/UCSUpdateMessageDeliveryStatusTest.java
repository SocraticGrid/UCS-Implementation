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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.DeliveryStatus;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSUpdateMessageDeliveryStatusTest extends UCSControllerServiceBasedTest {

    @Test
    public void testNoReference() throws MessageSerializationException {

        String reference = UUID.randomUUID().toString();
        
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.REFERENCE_ATTRIBUTE_NAME, "ref");
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.DELIVERY_STATUS_ACTION, "Deliver");
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.DELIVERY_STATUS, "OK");
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ref", reference);
        
        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_NO_MATCH, 1);
    }
    
    @Test
    public void testHappyPath() throws MessageSerializationException, IOException {

        MessageWrapper originalMessageWrapper = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("eafry", "SMS"))
                .buildMessageWrapper();
        
        String recipientId = UUID.randomUUID().toString();
        String reference = UUID.randomUUID().toString();
        
        originalMessageWrapper.getMessage().getHeader().getRecipientsList().iterator().next().setRecipientId(recipientId);
        controller.saveMessage(originalMessageWrapper.getMessage());
        controller.saveMessageReference(originalMessageWrapper.getMessage(), recipientId, reference);
        
        
        
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.REFERENCE_ATTRIBUTE_NAME, "ref");
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.DELIVERY_STATUS_ACTION, "Deliver");
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.DELIVERY_STATUS, "OK");
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ref", reference);
        
        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_SUCCESS, 1);
        
        //check that the message was updated in the controller
        assertMessage(controller.getMessageByReference(reference).get(), recipientId, "Deliver", "OK");
        
        //check that the content of the FlowFile is the updated message
        assertMessage(
                MessageSerializer.deserializeMessageWrapper(
                        new String(testRunner.getFlowFilesForRelationship(UCSUpdateMessageDeliveryStatus.REL_SUCCESS).get(0).toByteArray())
                    ).getMessage(),
                    recipientId,
                    "Deliver",
                    "OK"
        );
        
    }
    
    @Test
    public void testHappyPathWithMultiReferences() throws MessageSerializationException, IOException {

        String recipient1Id = UUID.randomUUID().toString();
        String reference1 = UUID.randomUUID().toString();
        String recipient2Id = UUID.randomUUID().toString();
        String reference2 = UUID.randomUUID().toString();
        
        
        MessageWrapper originalMessageWrapper = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient(recipient1Id, "ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient(recipient2Id, "eafry", "CHAT"))
                .buildMessageWrapper();
        
        
        controller.saveMessage(originalMessageWrapper.getMessage());
        controller.saveMessageReference(originalMessageWrapper.getMessage(), recipient1Id, reference1);
        controller.saveMessageReference(originalMessageWrapper.getMessage(), recipient2Id, reference2);
        
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.REFERENCE_ATTRIBUTE_NAME, "ref");
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.DELIVERY_STATUS_ACTION, "Deliver");
        testRunner.setProperty(UCSUpdateMessageDeliveryStatus.DELIVERY_STATUS, "OK");
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ref", reference1+","+reference2);
        
        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSUpdateMessageDeliveryStatus.REL_SUCCESS, 1);
        
        //check that the message was updated in the controller
        assertMessage(controller.getMessageByReference(reference1).get(), recipient1Id, "Deliver", "OK");
        assertMessage(controller.getMessageByReference(reference2).get(), recipient2Id, "Deliver", "OK");
        
        //check that the content of the FlowFile is the updated message
        assertMessage(
                MessageSerializer.deserializeMessageWrapper(
                        new String(testRunner.getFlowFilesForRelationship(UCSUpdateMessageDeliveryStatus.REL_SUCCESS).get(0).toByteArray())
                    ).getMessage(),
                    recipient1Id,
                    "Deliver",
                    "OK"
        );
        
    }
    
    private void assertMessage(Message message, String recipientId, String action, String status){
        DeliveryStatus deliveryStatus = message.getHeader().getDeliveryStatusList().stream()
                .filter(ds -> ds.getRecipient().getRecipientId().equals(recipientId))
                .findFirst().get();
        
        assertThat(deliveryStatus.getAction(), is(action));
        assertThat(deliveryStatus.getStatus(), is(status));
        assertThat(deliveryStatus.getTimestamp(), not(nullValue()));
        
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSUpdateMessageDeliveryStatus());
    }
}
