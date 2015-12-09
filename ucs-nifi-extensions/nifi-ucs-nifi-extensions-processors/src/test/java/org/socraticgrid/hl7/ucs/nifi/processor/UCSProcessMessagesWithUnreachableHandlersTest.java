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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.processor.model.MessageWithUnreachableHandlers;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSProcessMessagesWithUnreachableHandlersTest extends UCSControllerServiceBasedTest {

    @Test
    public void doTestNoMessages() {

        testRunner.enqueue(new byte[]{});
        testRunner.run();

        testRunner.assertTransferCount(UCSProcessMessagesWithUnreachableHandlers.REL_ALL_HANDLERS, 0);
        testRunner.assertTransferCount(UCSProcessMessagesWithUnreachableHandlers.REL_SOME_HANDLERS, 0);
        testRunner.assertTransferCount(UCSProcessMessagesWithUnreachableHandlers.REL_NO_ALTERNATIVE_MESSAGE, 0);
        testRunner.assertTransferCount(UCSProcessMessagesWithUnreachableHandlers.REL_FAILURE, 0);

    }

    @Test
    public void doTestNO_ALTERNATIVE_MESSAGE() throws IOException, MessageSerializationException {

        Message message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .buildMessage();

        //persist a new MessageWithUnreachableHandlers
        controller.notifyAboutMessageWithUnreachableHandlers(
                new MessageWithUnreachableHandlers(
                        message,
                        MessageWithUnreachableHandlers.Reason.ALL_HANDLERS)
        );

        testRunner.enqueue(new byte[]{});
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSProcessMessagesWithUnreachableHandlers.REL_NO_ALTERNATIVE_MESSAGE, 1);
    }

    @Test
    public void doTestALL_HANDLERS() throws IOException, MessageSerializationException {

        MessageBuilder escalationMessageBuilder1 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("This is an escalation message")
                .withBody("Escalation Message sent by CHAT")
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"));

        MessageBuilder escalationMessageBuilder2 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("This is an escalation message")
                .withBody("Escalation Message sent by SMS")
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        Message message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addOnFailureToReachAll(escalationMessageBuilder1)
                .addOnFailureToReachAll(escalationMessageBuilder2)
                .buildMessage();
        
        //persist a new MessageWithUnreachableHandlers
        controller.notifyAboutMessageWithUnreachableHandlers(
                new MessageWithUnreachableHandlers(
                        message,
                        MessageWithUnreachableHandlers.Reason.ALL_HANDLERS)
        );

        testRunner.enqueue(new byte[]{});
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSProcessMessagesWithUnreachableHandlers.REL_ALL_HANDLERS, 2);
        Set<MessageWrapper> messages = testRunner.getFlowFilesForRelationship(UCSProcessMessagesWithUnreachableHandlers.REL_ALL_HANDLERS).stream()
                .map(ff -> {
                    try {
                        return MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));
                    } catch (MessageSerializationException ex) {
                        throw new IllegalArgumentException();
                    }
                })
                .collect(Collectors.toSet());

        assertThat(
                messages.stream()
                .map(m -> m.getMessage().getParts()[0].getContent())
                .collect(Collectors.toSet()), containsInAnyOrder("Escalation Message sent by CHAT", "Escalation Message sent by SMS")
        );
    }

    @Test
    public void doTestSOME_HANDLERS() throws IOException, MessageSerializationException {
        MessageBuilder escalationMessageBuilder1 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("This is an escalation message")
                .withBody("Escalation Message sent by CHAT")
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"));

        MessageBuilder escalationMessageBuilder2 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("This is an escalation message")
                .withBody("Escalation Message sent by SMS")
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        Message message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addOnFailureToReachAny(escalationMessageBuilder1)
                .addOnFailureToReachAny(escalationMessageBuilder2)
                .buildMessage();

        //persist a new MessageWithUnreachableHandlers
        controller.notifyAboutMessageWithUnreachableHandlers(
                new MessageWithUnreachableHandlers(
                        message,
                        MessageWithUnreachableHandlers.Reason.SOME_HANDLERS)
        );

        testRunner.enqueue(new byte[]{});
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSProcessMessagesWithUnreachableHandlers.REL_SOME_HANDLERS, 2);
        Set<MessageWrapper> messages = testRunner.getFlowFilesForRelationship(UCSProcessMessagesWithUnreachableHandlers.REL_SOME_HANDLERS).stream()
                .map(ff -> {
                    try {
                        return MessageSerializer.deserializeMessageWrapper(new String(ff.toByteArray()));
                    } catch (MessageSerializationException ex) {
                        throw new IllegalArgumentException();
                    }
                })
                .collect(Collectors.toSet());

        assertThat(
                messages.stream()
                .map(m -> m.getMessage().getParts()[0].getContent())
                .collect(Collectors.toSet()), containsInAnyOrder("Escalation Message sent by CHAT", "Escalation Message sent by SMS")
        );
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSProcessMessagesWithUnreachableHandlers());
    }
}
