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

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doNothing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSControllerServiceProxy;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage.TimeOutType;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author pavan
 */
public class UCSProcessResponseTimeoutTest extends UCSControllerServiceBasedTest {

    @Override
    protected UCSControllerServiceProxy createUCSControllerService() {
        return spy(new UCSControllerServiceProxy());
    }
    
    @Override
    public void afterInit() throws Exception {

        //we don't want to connect to a real server
        doNothing().when(controller).onEnabled(anyObject());

        MessageWrapper originalMessageWrapper = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("eafry", "SMS"))
                .buildMessageWrapper();

        MessageWrapper originalMessageWrapper1 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("eafry", "SMS"))
                .buildMessageWrapper();

        List<Message> onNoResponseAny = new ArrayList<>();
        onNoResponseAny.add(originalMessageWrapper1.getMessage());
        originalMessageWrapper.getMessage().getHeader().setOnNoResponseAny(onNoResponseAny);

        //these are the messages we want to return
        Mockito.doAnswer(new Answer() {
            private int execNumber = 0;

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                try {
                    if (execNumber == 0) {
                        return Arrays.asList(new TimedOutMessage(originalMessageWrapper.getMessage(), TimeOutType.PARTIAL_RESPONSES)
                        ).stream().collect(Collectors.toSet());
                    } else {
                        return Collections.EMPTY_SET;
                    }
                } finally {
                    execNumber++;
                }
            }
        }).when(controller).consumeMessagesWithResponseTimeout();
        
    }

    @Test
    public void doTestTimedoutMessage() throws IOException {
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSProcessResponseTimeout.REL_PARTIAL_RESPONSES, 1);
        List<MockFlowFile> flowFiles = testRunner.getFlowFilesForRelationship(UCSProcessResponseTimeout.REL_PARTIAL_RESPONSES);
        Assert.assertNotNull(flowFiles);
        Assert.assertTrue(flowFiles.size() > 0);
        String content = new String(flowFiles.get(0).toByteArray());
        Assert.assertTrue(content.contains("ealiverti"));
    }

    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSProcessResponseTimeout());
    }
}
