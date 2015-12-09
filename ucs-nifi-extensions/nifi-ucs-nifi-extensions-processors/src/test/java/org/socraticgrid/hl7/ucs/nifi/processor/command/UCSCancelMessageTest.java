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
package org.socraticgrid.hl7.ucs.nifi.processor.command;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSCancelMessageTest extends UCSControllerServiceBasedTest {

    @Test
    public void doTestMissingArgs() throws MessageSerializationException, IOException {
        //Create a new command without any argument
        Map<String, String> attributes = this.createBasicAttributes();

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSCancelMessage.REL_FAILURE);

    }

    @Test
    public void doTestUnknownMessage() throws MessageSerializationException, IOException {
        //Create a new command with a messageId that is not present in UCSControllerService
        Map<String, String> attributes = this.createBasicAttributes("i-do-not-exist");

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSCancelMessage.REL_UNKNOWN_MESSAGE);

    }

    @Test
    public void doTestBadMessageType() throws MessageSerializationException, IOException {
        //Create a new Message (not AlertMessage) and persist it in UCSControllerService
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new MessageBuilder()
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        //Create a new command with the messageId of the persisted Message.
        Map<String, String> attributes = this.createBasicAttributes(messageId);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSCancelMessage.REL_BAD_MESSAGE_TYPE);

    }

    @Test
    public void doTestNoUpdate() throws MessageSerializationException, IOException {
        //Create a new AlertMessage with 'Retracted' status  and persist 
        //it in UCSControllerService
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new AlertMessageBuilder()
                .withStatus(AlertStatus.Retracted)
                .withConversationId("testC")
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealivert", "EMAIL"))
                .addOnFailureToReachAll(
                        new MessageBuilder()
                        .withConversationId("testC")
                        .withSender("eafry")
                        .withSubject("Where are you?")
                        .withBody("I coulnd't reach you!")
                        .addRecipient(new MessageBuilder.Recipient("eliverti", "SMS"))
                )
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        //Create a new command with the messageId of the persisted Message.
        Map<String, String> attributes = this.createBasicAttributes(messageId);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSCancelMessage.REL_NO_UPDATE, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSCancelMessage.REL_NO_UPDATE).get(0);

        ff.assertContentEquals(MessageSerializer.serializeMessageWrapper(mw));
        
        //the status of the message in UCSControllerService didn't change
        assertThat(
            ((AlertMessage)controller.getMessageById(messageId).get()).getHeader().getAlertStatus(),
            is(AlertStatus.Retracted)
        );
    }

    @Test
    public void doTestInvalidState() throws MessageSerializationException, IOException {

        for (AlertStatus status : AlertStatus.values()) {
            if (status == AlertStatus.Pending || status == AlertStatus.Retracted) {
                continue;
            }

            //Create a new AlertMessage with status 'status' and persist 
            //it in UCSControllerService
            String messageId = UUID.randomUUID().toString();
            MessageWrapper mw = new AlertMessageBuilder()
                    .withStatus(status)
                    .withConversationId("testC")
                    .withMessageId(messageId)
                    .withSender("eafry")
                    .withSubject("Some Subject")
                    .withBody("Some Body")
                    .addRecipient(new MessageBuilder.Recipient("ealivert", "EMAIL"))
                    .addOnFailureToReachAll(
                            new MessageBuilder()
                            .withConversationId("testC")
                            .withSender("eafry")
                            .withSubject("Where are you?")
                            .withBody("I coulnd't reach you!")
                            .addRecipient(new MessageBuilder.Recipient("eliverti", "SMS"))
                    )
                    .buildMessageWrapper();

            controller.saveMessage(mw.getMessage());

            //Create a new command with the messageId of the persisted Message.
            Map<String, String> attributes = this.createBasicAttributes(messageId);

            testRunner.enqueue(new byte[]{}, attributes);
            testRunner.run();

            testRunner.assertAllFlowFilesTransferred(UCSCancelMessage.REL_INVALID_STATE, 1);
            
            testRunner.clearTransferState();
            
            //the status of the message in UCSControllerService didn't change
            assertThat(
                ((AlertMessage)controller.getMessageById(messageId).get()).getHeader().getAlertStatus(),
                is(status)
            );
        }
    }
    
    @Test
    public void doTestCancel() throws MessageSerializationException, IOException {
        //Create a new AlertMessage with 'Pending' status  and persist 
        //it in UCSControllerService
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new AlertMessageBuilder()
                .withStatus(AlertStatus.Pending)
                .withConversationId("testC")
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealivert", "EMAIL"))
                .addOnFailureToReachAll(
                        new MessageBuilder()
                        .withConversationId("testC")
                        .withSender("eafry")
                        .withSubject("Where are you?")
                        .withBody("I coulnd't reach you!")
                        .addRecipient(new MessageBuilder.Recipient("eliverti", "SMS"))
                )
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        //Create a new command with the messageId of the persisted Message.
        Map<String, String> attributes = this.createBasicAttributes(messageId);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSCancelMessage.REL_CANCELLED, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSCancelMessage.REL_CANCELLED).get(0);

        MessageWrapper mw2 = MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));
        assertThat(((AlertMessage)mw2.getMessage()).getHeader().getAlertStatus(), is(AlertStatus.Retracted));
        
        //the status of the message in UCSControllerService has changed
        assertThat(
            ((AlertMessage)controller.getMessageById(messageId).get()).getHeader().getAlertStatus(),
            is(AlertStatus.Retracted)
        );
    }

    private Map<String, String> createBasicAttributes(String callbackURL) {
        Map<String, String> result = this.createBasicAttributes();
        result.put("command.args", callbackURL);

        return result;
    }

    private Map<String, String> createBasicAttributes() {
        Map<String, String> result = new HashMap<>();
        result.put("command.name", "cancelMessage");

        return result;
    }

    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSCancelMessage());
    }
}
