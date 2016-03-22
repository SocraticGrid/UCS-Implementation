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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSPrepareSMSTest extends UCSControllerServiceBasedTest{
    
    @Test
    public void testSingleSMS() throws IOException{
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("491623342171", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareSMS.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).get(0);
        
        String phoneAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.PHONE_ATTRIBUTE_NAME).getValue();
        String textAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.TEXT_ATTRIBUTE_NAME).getValue();
        String referenceAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.REFERENCE_ATTRIBUTE_NAME).getValue();
        
        assertThat(ff.getAttribute(phoneAttributeKey), is("491623342171"));
        assertThat(ff.getAttribute(textAttributeKey), is("This is the content of the test message"));
        assertThat(ff.getAttribute(referenceAttributeKey), not(nullValue()));
    }
    
    @Test
    public void testSingleSMS2() throws IOException{
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("491623342171", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareSMS.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).get(0);
        
        String phoneAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.PHONE_ATTRIBUTE_NAME).getValue();
        String textAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.TEXT_ATTRIBUTE_NAME).getValue();
        String referenceAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.REFERENCE_ATTRIBUTE_NAME).getValue();
        
        assertThat(ff.getAttribute(phoneAttributeKey), is("491623342171"));
        assertThat(ff.getAttribute(textAttributeKey), is("This is the content of the test message"));
        assertThat(ff.getAttribute(referenceAttributeKey), not(nullValue()));
    }
    
    @Test
    public void testNoSMS() throws IOException{
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("ealiverti@cognitivemedicine.com", "EMAIL"))
                .addRecipient(new MessageBuilder.Recipient("eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareSMS.REL_FAILURE, 1);
    }
    
    @Test
    public void testMultiSMS() throws IOException{
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("491623342171", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti@cognitivemedicine.com", "EMAIL"))
                .addRecipient(new MessageBuilder.Recipient("19717130576", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareSMS.REL_SUCCESS, 2);
        
        String phoneAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.PHONE_ATTRIBUTE_NAME).getValue();
        String textAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.TEXT_ATTRIBUTE_NAME).getValue();
        String referenceAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareSMS.REFERENCE_ATTRIBUTE_NAME).getValue();
        
        List<String> phoneNumbers = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(phoneAttributeKey))
                .collect(Collectors.toList());
        
        Set<String> text = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(textAttributeKey))
                .collect(Collectors.toSet());
        
        Set<String> references = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(referenceAttributeKey))
                .collect(Collectors.toSet());
        
        assertThat(phoneNumbers, containsInAnyOrder("19717130576", "491623342171"));
        assertThat(text, contains("This is the content of the test message"));
        assertThat(references, hasSize(2));
    }
    
    @Test
    public void testMultiSMSWithCustomAttributes() throws IOException{

        String smsServiceIdAttributeKey = "SMS-Service";
        String phoneAttributeKey = "custom.sms.p";
        String textAttributeKey = "custom.sms.t";
        String referenceAttributeKey = "custom.sms.r";
        
        testRunner.setProperty(UCSPrepareSMS.SMS_SERVICE_ID.getName(), smsServiceIdAttributeKey);
        testRunner.setProperty(UCSPrepareSMS.PHONE_ATTRIBUTE_NAME.getName(), phoneAttributeKey);
        testRunner.setProperty(UCSPrepareSMS.TEXT_ATTRIBUTE_NAME.getName(), textAttributeKey);
        testRunner.setProperty(UCSPrepareSMS.REFERENCE_ATTRIBUTE_NAME.getName(), referenceAttributeKey);
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("491623342171", "SMS-Service"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti@cognitivemedicine.com", "EMAIL"))
                .addRecipient(new MessageBuilder.Recipient("19717130576", "SMS-Service"))
                .addRecipient(new MessageBuilder.Recipient("eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareSMS.REL_SUCCESS, 2);
        
        List<String> phoneNumbers = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(phoneAttributeKey))
                .collect(Collectors.toList());
        
        Set<String> text = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(textAttributeKey))
                .collect(Collectors.toSet());
        
        Set<String> references = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(referenceAttributeKey))
                .collect(Collectors.toSet());
        
        assertThat(phoneNumbers, containsInAnyOrder("19717130576", "491623342171"));
        assertThat(text, contains("This is the content of the test message"));
        assertThat(references, hasSize(2));
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSPrepareSMS());
    }
}
