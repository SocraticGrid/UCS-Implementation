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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Optional;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder.Body;

/**
 *
 * @author BasitAzeem
 *
 */
public class UCSPrepareEmailTest extends UCSControllerServiceBasedTest {

    @Test
    public void testTextEmailSingleRecipient() throws IOException, MessageSerializationException {
        Body body = new Body("This is the content of the test Email");
        body.type = "text/plain";
        Message message = new MessageBuilder()
                .withConversationId("testA")
                .withMessageId("TestMessageId")
                .withSender("eafry")
                .withBody(body)
                .withSubject("Test Email")
                .withReceiptNotification(true)
                .addRecipient(
                        new MessageBuilder.Recipient("er.basit@gmail.com",
                                "EMAIL")).buildMessage();

        //persist the message
        controller.saveMessage(message);

        testRunner.enqueue(MessageSerializer.serializeMessageWrapper(new MessageWrapper(message)).getBytes());
        testRunner.run();

        testRunner
                .assertAllFlowFilesTransferred(UCSPrepareEmail.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSPrepareEmail.REL_SUCCESS).get(0);

        String subjectAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_SUBJECT_ATTRIBUTE_NAME)
                .getValue();
        String toEmailAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.TO_EMAIL_ATTRIBUTE_NAME)
                .getValue();
        String emailMimeTypeAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_MIME_TYPE_ATTRIBUTE_NAME)
                .getValue();
        String referenceAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.REFERENCE_ATTRIBUTE_NAME)
                .getValue();
        assertThat(ff.getAttribute(subjectAttributeKey).split("::")[0],
                is("Test Email"));
        assertThat(ff.getAttribute(toEmailAttributeKey),
                is("er.basit@gmail.com"));
        assertThat(ff.getAttribute(emailMimeTypeAttributeKey), is("text/plain"));
        assertThat(ff.getAttribute(referenceAttributeKey), not(nullValue()));

        Optional<Message> messageByReference = controller.getMessageByReference(ff.getAttribute(referenceAttributeKey));
        assertThat(messageByReference.isPresent(), is(true));
        assertThat(messageByReference.get().getHeader().getMessageId(), is("TestMessageId"));

    }

    @Test
    public void testHtmlEmailSingleRecipient() throws IOException {
        Body body = new Body(
                "&lt;h1&gt;Hi,&lt;/h1&gt;&lt;br&gt;&lt;p&gt;This is the content of the test HTML Email&lt;/p&gt;");
        body.type = "text/html";
        String message = new MessageBuilder()
                .withConversationId("testB")
                .withSender("eafry")
                .withBody(body)
                .withSubject("Test Email")
                .addRecipient(
                        new MessageBuilder.Recipient("er.basit@gmail.com",
                                "EMAIL")).buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner
                .assertAllFlowFilesTransferred(UCSPrepareEmail.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSPrepareEmail.REL_SUCCESS).get(0);

        String subjectAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_SUBJECT_ATTRIBUTE_NAME)
                .getValue();
        String toEmailAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.TO_EMAIL_ATTRIBUTE_NAME)
                .getValue();
        String emailMimeTypeAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_MIME_TYPE_ATTRIBUTE_NAME)
                .getValue();
        assertThat(ff.getAttribute(subjectAttributeKey).split("::")[0],
                is("Test Email"));
        assertThat(ff.getAttribute(toEmailAttributeKey),
                is("er.basit@gmail.com"));
        assertThat(ff.getAttribute(emailMimeTypeAttributeKey), is("text/html"));
    }

    @Test
    public void testTextEmailMultipleServiceIds() throws IOException {
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .withSubject("Test Email")
                .addRecipient(
                        new MessageBuilder.Recipient("491623342171", "SMS"))
                .addRecipient(
                        new MessageBuilder.Recipient(
                                "eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner
                .assertAllFlowFilesTransferred(UCSPrepareEmail.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSPrepareEmail.REL_SUCCESS).get(0);

        String subjectAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_SUBJECT_ATTRIBUTE_NAME)
                .getValue();
        String toEmailAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.TO_EMAIL_ATTRIBUTE_NAME)
                .getValue();
        String emailMimeTypeAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_MIME_TYPE_ATTRIBUTE_NAME)
                .getValue();
        assertThat(ff.getAttribute(subjectAttributeKey).split("::")[0],
                is("Test Email"));
        assertThat(ff.getAttribute(toEmailAttributeKey),
                is("eafry@cognitivemedicine.com"));
        assertThat(ff.getAttribute(emailMimeTypeAttributeKey), is("text/plain"));
    }

    @Test
    public void testNoEmail() throws IOException {
        String message = new MessageBuilder()
                .withConversationId("testD")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("9900112233", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("9900112244", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner
                .assertAllFlowFilesTransferred(UCSPrepareEmail.REL_FAILURE, 1);
    }

    @Test
    public void testTextEmailMultipeleRecipients() throws IOException {
        Body body = new Body("This is the content of the test Email");
        body.type = "text/plain";
        String message = new MessageBuilder()
                .withConversationId("testA")
                .withSender("eafry")
                .withBody(body)
                .withSubject("Test Email")
                .addRecipient(
                        new MessageBuilder.Recipient("er.basit@gmail.com",
                                "EMAIL"))
                .addRecipient(
                        new MessageBuilder.Recipient(
                                "eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner
                .assertAllFlowFilesTransferred(UCSPrepareEmail.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSPrepareEmail.REL_SUCCESS).get(0);

        String subjectAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_SUBJECT_ATTRIBUTE_NAME)
                .getValue();
        String toEmailAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.TO_EMAIL_ATTRIBUTE_NAME)
                .getValue();
        String emailMimeTypeAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_MIME_TYPE_ATTRIBUTE_NAME)
                .getValue();
        assertThat(ff.getAttribute(subjectAttributeKey).split("::")[0],
                is("Test Email"));
        assertThat(
                ff.getAttribute(toEmailAttributeKey),
                Matchers.either(
                        Matchers.is("er.basit@gmail.com,eafry@cognitivemedicine.com"))
                .or(Matchers
                        .is("eafry@cognitivemedicine.com,er.basit@gmail.com")));
        assertThat(ff.getAttribute(emailMimeTypeAttributeKey), is("text/plain"));
    }

    @Test
    public void testTextEmailMultipleRecipientsWithCustomAttributes()
            throws IOException {

        String emailServiceIdAttributeKey = "EMAIL";
        String subjectAttributeKey = "custom.email.subject";
        String toAttributeKey = "custom.email.to";
        String mimeTypeAttributeKey = "custom.email.mime.type";

        testRunner.setProperty(UCSPrepareEmail.EMAIL_SERVICE_ID.getName(),
                emailServiceIdAttributeKey);
        testRunner.setProperty(
                UCSPrepareEmail.EMAIL_SUBJECT_ATTRIBUTE_NAME.getName(),
                subjectAttributeKey);
        testRunner.setProperty(
                UCSPrepareEmail.TO_EMAIL_ATTRIBUTE_NAME.getName(),
                toAttributeKey);
        testRunner.setProperty(
                UCSPrepareEmail.EMAIL_MIME_TYPE_ATTRIBUTE_NAME.getName(),
                mimeTypeAttributeKey);

        String message = new MessageBuilder()
                .withConversationId("testE")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(
                        new MessageBuilder.Recipient(
                                "ealiverti@cognitivemedicine.com", "EMAIL"))
                .addRecipient(
                        new MessageBuilder.Recipient("491623342171", "SMS"))
                .addRecipient(
                        new MessageBuilder.Recipient(
                                "eafry@cognitivemedicine.com", "EMAIL"))
                .addRecipient(
                        new MessageBuilder.Recipient("18583957317", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner
                .assertAllFlowFilesTransferred(UCSPrepareEmail.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSPrepareEmail.REL_SUCCESS).get(0);

        String subjectAttributeVal = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_SUBJECT_ATTRIBUTE_NAME)
                .getValue();
        String toEmailAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.TO_EMAIL_ATTRIBUTE_NAME)
                .getValue();
        String emailMimeTypeAttributeKey = testRunner.getProcessContext()
                .getProperty(UCSPrepareEmail.EMAIL_MIME_TYPE_ATTRIBUTE_NAME)
                .getValue();
        assertThat(ff.getAttribute(subjectAttributeVal).split("::")[0],
                is("<NO-SUBJECT>"));
        assertThat(
                ff.getAttribute(toEmailAttributeKey),
                Matchers.either(
                        Matchers.is("ealiverti@cognitivemedicine.com,eafry@cognitivemedicine.com"))
                .or(Matchers
                        .is("eafry@cognitivemedicine.com,ealiverti@cognitivemedicine.com")));
        assertThat(ff.getAttribute(emailMimeTypeAttributeKey), is("text/plain"));
    }

    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSPrepareEmail());
    }
}
