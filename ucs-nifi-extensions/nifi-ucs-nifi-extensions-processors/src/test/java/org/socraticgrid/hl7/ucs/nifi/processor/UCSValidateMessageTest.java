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
import java.util.Set;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toSet;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSValidateMessageTest extends UCSControllerServiceBasedTest {

    @Test
    public void testSingleSMS() throws MessageSerializationException, IOException {

        String message = new MessageBuilder()
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSValidateMessage.REL_SUCCESS).get(0);

        assertThat(ff.getAttribute(UCSValidateMessage.VALID_ATTRIBUTE_KEY), is("true"));

        MessageWrapper messageWrapper = MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));

        assertThat(messageWrapper.getMessage(), not(nullValue()));

        assertThat(messageWrapper.getMessage().getHeader().getRecipientsList().stream()
                .filter(r -> r.getRecipientId() != null)
                .collect(Collectors.toSet()).size(),
                is(2));
    }

    @Test
    public void testDuplicatedIdsOnMessageFAIL() throws MessageSerializationException, IOException {

        testRunner.setProperty(UCSValidateMessage.ON_DUPLICATED_MESSAGE_ID, UCSValidateMessage.FAIL);

        String messageId = "A";

        MessageBuilder nestedMessageBuilder = new MessageBuilder()
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Nested Message")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        String message = new MessageBuilder()
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .addOnFailureToReachAll(nestedMessageBuilder)
                .addOnFailureToReachAny(nestedMessageBuilder)
                .addOnNoResponseAll(nestedMessageBuilder)
                .addOnNoResponseAny(nestedMessageBuilder)
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_FAILURE, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSValidateMessage.REL_FAILURE).get(0);

        assertThat(ff.getAttribute(UCSCreateException.TYPE_DEFAULT_ATTRIBUTE_NAME), is(ExceptionType.InvalidMessage.name()));
        assertThat(ff.getAttribute(UCSCreateException.FAULT_DEFAULT_ATTRIBUTE_NAME), is("Duplicated Message Ids found: A,A,A,A,A"));

    }

    @Test
    public void testDuplicatedIdsOnMessageUPDATE() throws MessageSerializationException, IOException {

        testRunner.setProperty(UCSValidateMessage.ON_DUPLICATED_MESSAGE_ID, UCSValidateMessage.UPDATE_ID);

        String messageId = "A";

        MessageBuilder nestedMessageBuilder = new MessageBuilder()
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Nested Message")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        String message = new MessageBuilder()
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .addOnFailureToReachAll(nestedMessageBuilder)
                .addOnFailureToReachAny(nestedMessageBuilder)
                .addOnNoResponseAll(nestedMessageBuilder)
                .addOnNoResponseAny(nestedMessageBuilder)
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSValidateMessage.REL_SUCCESS).get(0);

        MessageWrapper mw = MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));

        Set<Message> allMessages = UCSValidateMessage.collectNestedMessages(mw.getMessage());
        allMessages.add(mw.getMessage());

        Set<String> ids = allMessages.stream().map(m -> m.getHeader().getMessageId()).collect(toSet());

        assertThat(ids, hasSize(5));
    }

    @Test
    public void testDuplicatedIdsOnUCSFAIL() throws MessageSerializationException, IOException {

        testRunner.setProperty(UCSValidateMessage.ON_DUPLICATED_MESSAGE_ID, UCSValidateMessage.FAIL);

        String messageId = "A";

        String message = new MessageBuilder()
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .buildSerializedMessageWrapper();

        //persist a message with the same Id in UCSControllerService
        controller.saveMessage(new MessageBuilder()
                .withMessageId(messageId)
                .withSender("ealiverti")
                .withSubject("Original Message")
                .withBody("Original Message Body")
                .addRecipient(new MessageBuilder.Recipient("eafry", "EMAIL"))
                .buildMessage()
        );

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_FAILURE, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSValidateMessage.REL_FAILURE).get(0);

        assertThat(ff.getAttribute(UCSCreateException.TYPE_DEFAULT_ATTRIBUTE_NAME), is(ExceptionType.InvalidMessage.name()));
        assertThat(ff.getAttribute(UCSCreateException.FAULT_DEFAULT_ATTRIBUTE_NAME), is("Duplicated Message Ids found: A"));

    }
    
    @Test
    public void testDuplicatedIdsOnUCSUPDATE() throws MessageSerializationException, IOException {

        testRunner.setProperty(UCSValidateMessage.ON_DUPLICATED_MESSAGE_ID, UCSValidateMessage.UPDATE_ID);

        String messageId = "A";

        String message = new MessageBuilder()
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .buildSerializedMessageWrapper();

        //persist a message with the same Id in UCSControllerService
        controller.saveMessage(new MessageBuilder()
                .withMessageId(messageId)
                .withSender("ealiverti")
                .withSubject("Original Message")
                .withBody("Original Message Body")
                .addRecipient(new MessageBuilder.Recipient("eafry", "EMAIL"))
                .buildMessage()
        );

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSValidateMessage.REL_SUCCESS).get(0);

        MessageWrapper mw = MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));

        Set<Message> allMessages = UCSValidateMessage.collectNestedMessages(mw.getMessage());
        allMessages.add(mw.getMessage());

        Set<String> ids = allMessages.stream().map(m -> m.getHeader().getMessageId()).collect(toSet());

        assertThat(ids, hasSize(1));
        assertThat(ids.iterator().next(), is(not(messageId)));

    }
    
    @Test
    public void testAlertMessageStatusChange() throws MessageSerializationException, IOException {

        String message = new AlertMessageBuilder()
                .withStatus(AlertStatus.New)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "ALERT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "ALERT"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSValidateMessage.REL_SUCCESS).get(0);

        assertThat(ff.getAttribute(UCSValidateMessage.VALID_ATTRIBUTE_KEY), is("true"));

        MessageWrapper messageWrapper = MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));

        assertThat(messageWrapper.getMessage(), instanceOf(AlertMessage.class));
        assertThat(((AlertMessage)messageWrapper.getMessage()).getHeader().getAlertStatus(), is(AlertStatus.Pending));

    }
    
    @Test
    public void testUnknownConversationIdOnMessage() throws MessageSerializationException, IOException {

        testRunner.setProperty(UCSValidateMessage.ON_DUPLICATED_MESSAGE_ID, UCSValidateMessage.UPDATE_ID);

        String messageId = "A";
        String conversationId = "Unkwnown";
        
        MessageBuilder nestedMessageBuilder = new MessageBuilder()
                .withConversationId(conversationId)
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Nested Message")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        String message = new MessageBuilder()
                .withConversationId(conversationId)
                .withMessageId(messageId)
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .addOnFailureToReachAll(nestedMessageBuilder)
                .addOnFailureToReachAny(nestedMessageBuilder)
                .addOnNoResponseAll(nestedMessageBuilder)
                .addOnNoResponseAny(nestedMessageBuilder)
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSValidateMessage.REL_FAILURE, 1);
        
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSValidateMessage());
    }
}
