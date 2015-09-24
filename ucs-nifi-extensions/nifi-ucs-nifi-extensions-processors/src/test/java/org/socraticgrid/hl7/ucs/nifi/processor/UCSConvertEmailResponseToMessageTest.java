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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author BasitAzeem
 *
 */
public class UCSConvertEmailResponseToMessageTest extends UCSControllerServiceBasedTest {

    @Test
    public void testNoMessageId() throws MessageSerializationException {
        Map<String, String> flowFileAttributes = new HashMap<>();
        flowFileAttributes.put("toEmails", "er.basit@gmail.com");
        flowFileAttributes.put("fromEmails", "nifi@socraticgrid.com");
        flowFileAttributes.put("subject", "Test Email");
        flowFileAttributes.put("contentType", "text/plain");
        testRunner.enqueue("This is just a test Email!".getBytes(),
                flowFileAttributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSConvertEmailResponseToMessage.REL_NO_MATCH, 1);
    }

    @Test
    public void testNonExistingMessageId() throws MessageSerializationException {
        Map<String, String> flowFileAttributes = new HashMap<>();
        flowFileAttributes.put("toEmails", "er.basit@gmail.com");
        flowFileAttributes.put("fromEmails", "nifi@socraticgrid.com");
        flowFileAttributes.put("subject", "Test Email::[abcde]");
        flowFileAttributes.put("contentType", "text/plain");
        testRunner.enqueue("This is just a test Email!".getBytes(),
                flowFileAttributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSConvertEmailResponseToMessage.REL_NO_MATCH, 1);
    }

    @Test
    public void testHappyPath() throws MessageSerializationException,
            IOException {

        String recipientId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();

        MessageWrapper originalMessageWrapper = new MessageBuilder()
                .withConversationId("testC")
                .withSender("er.basit@gmail.com")
                .addRecipient(
                        new MessageBuilder.Recipient("er.basit@gmail.com",
                                "EMAIL")).buildMessageWrapper();

        originalMessageWrapper.getMessage().getHeader().getRecipientsList()
                .iterator().next().setRecipientId(recipientId);
        originalMessageWrapper.getMessage().getHeader().setMessageId(messageId);
        controller.saveMessage(originalMessageWrapper.getMessage());

        Map<String, String> flowFileAttributes = new HashMap<>();
        flowFileAttributes.put("toEmails", "[er.basit@gmail.com]");
        flowFileAttributes.put("fromEmails", "nifi@socraticgrid.com");
        flowFileAttributes.put("subject", "Test Email::[" + messageId + "]");
        flowFileAttributes.put("contentType", "text/plain");
        testRunner.enqueue("This is just a test Email!".getBytes(),
                flowFileAttributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(
                UCSUpdateMessageDeliveryStatus.REL_SUCCESS, 1);

        Message message = MessageSerializer.deserializeMessageWrapper(
                new String(testRunner
                        .getFlowFilesForRelationship(
                                UCSUpdateMessageDeliveryStatus.REL_SUCCESS)
                        .get(0).toByteArray())).getMessage();

		// The sender is the original recipient. In a real case scneario, the
        // address should be resolved.
        assertThat(message.getHeader().getSender().getPhysicalAddress()
                .getAddress(), is("nifi@socraticgrid.com"));

        // The recipient is the original sender.
        assertThat(message.getHeader().getRecipientsList().iterator().next()
                .getDeliveryAddress().getPhysicalAddress().getAddress(),
                is("er.basit@gmail.com"));

        // The body of the message is the Email response
        assertThat(Arrays.asList(message.getParts()), hasSize(1));
        assertThat(message.getParts()[0].getType(), is("text/plain"));
        assertThat(message.getParts()[0].getContent(),
                is("This is just a test Email!"));
    }

    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSConvertEmailResponseToMessage());
    }
}
