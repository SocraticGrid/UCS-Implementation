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
public class UCSPrepareTextToVoiceTest extends UCSControllerServiceBasedTest{
    
    @Test
    public void testSingleMessage() throws IOException{
        
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("+4981614923621", "TEXT-TO-VOICE"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareTextToVoice.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareTextToVoice.REL_SUCCESS).get(0);
        
        String phoneAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.PHONE_ATTRIBUTE_NAME).getValue();
        String textAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.TEXT_ATTRIBUTE_NAME).getValue();
        String referenceAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.REFERENCE_ATTRIBUTE_NAME).getValue();
        
        assertThat(ff.getAttribute(phoneAttributeKey), is("+4981614923621"));
        assertThat(ff.getAttribute(textAttributeKey), is("This is the content of the test message"));
        assertThat(ff.getAttribute(referenceAttributeKey), not(nullValue()));
    }
    
    @Test
    public void testSingleMessage2() throws IOException{

        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("+4981614923621", "TEXT-TO-VOICE"))
                .addRecipient(new MessageBuilder.Recipient("4981614923621", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareTextToVoice.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(UCSPrepareTextToVoice.REL_SUCCESS).get(0);
        
        String phoneAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.PHONE_ATTRIBUTE_NAME).getValue();
        String textAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.TEXT_ATTRIBUTE_NAME).getValue();
        String referenceAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.REFERENCE_ATTRIBUTE_NAME).getValue();
        
        assertThat(ff.getAttribute(phoneAttributeKey), is("+4981614923621"));
        assertThat(ff.getAttribute(textAttributeKey), is("This is the content of the test message"));
        assertThat(ff.getAttribute(referenceAttributeKey), not(nullValue()));
    }
    
    @Test
    public void testNoTextToVoice() throws IOException{

        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("4981614923621", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareTextToVoice.REL_FAILURE, 1);
    }
    
    @Test
    public void testMultiMessages() throws IOException{

        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(new MessageBuilder.Recipient("+4981614923621", "TEXT-TO-VOICE"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti@cognitivemedicine.com", "EMAIL"))
                .addRecipient(new MessageBuilder.Recipient("18583957317", "TEXT-TO-VOICE"))
                .addRecipient(new MessageBuilder.Recipient("eafry@cognitivemedicine.com", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSPrepareTextToVoice.REL_SUCCESS, 2);
        
        String phoneAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.PHONE_ATTRIBUTE_NAME).getValue();
        String textAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.TEXT_ATTRIBUTE_NAME).getValue();
        String referenceAttributeKey = testRunner.getProcessContext().getProperty(UCSPrepareTextToVoice.REFERENCE_ATTRIBUTE_NAME).getValue();
        
        List<String> phoneNumbers = testRunner.getFlowFilesForRelationship(UCSPrepareTextToVoice.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(phoneAttributeKey))
                .collect(Collectors.toList());
        
        Set<String> text = testRunner.getFlowFilesForRelationship(UCSPrepareTextToVoice.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(textAttributeKey))
                .collect(Collectors.toSet());
        
        Set<String> references = testRunner.getFlowFilesForRelationship(UCSPrepareSMS.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute(referenceAttributeKey))
                .collect(Collectors.toSet());
        
        assertThat(phoneNumbers, containsInAnyOrder("18583957317", "+4981614923621"));
        assertThat(text, contains("This is the content of the test message"));
        assertThat(references, hasSize(2));
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSPrepareTextToVoice());
    }
}
