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

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author BasitAzeem
 */
public class UCSRouteMessageOnContentTest {

    private TestRunner testRunner;

    @Before
    public void init() throws Exception {
        testRunner = TestRunners.newTestRunner(new UCSRouteMessageOnContent());
    }

    @Test
    public void doTestNoMatchRoute() throws InitializationException,
            IOException {
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody("This is the content of the test message")
                .addRecipient(
                        new MessageBuilder.Recipient("491623342171", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.setProperty("Yes", ".*yes.*");
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSRouteMessageOnContent.REL_NO_MATCH, 1);
        final MockFlowFile out = testRunner.getFlowFilesForRelationship(
                UCSRouteMessageOnContent.REL_NO_MATCH).get(0);
        out.assertAttributeExists(UCSRouteMessageOnContent.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageOnContent.ROUTE_ATTRIBUTE_KEY,
                UCSRouteMessageOnContent.REL_NO_MATCH.getName());
        out.assertContentEquals(message);

    }

    @Test
    public void doTestMatchCaseInsensitiveRoute() throws InitializationException, IOException {
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody(
                        "This is the content of the test message with YES keyword")
                .addRecipient(
                        new MessageBuilder.Recipient("491623342171", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.setProperty("Yes", ".*yes.*");
        testRunner.setProperty(UCSRouteMessageOnContent.CASE_SENSITIVE,
                UCSRouteMessageOnContent.FALSE);

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred("Yes", 1);
        final MockFlowFile out = testRunner.getFlowFilesForRelationship("Yes")
                .get(0);
        out.assertAttributeExists(UCSRouteMessageOnContent.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageOnContent.ROUTE_ATTRIBUTE_KEY,
                "Yes");
        out.assertContentEquals(message);

    }

    @Test
    public void doTestMatchCaseSensitiveRoute() throws InitializationException, IOException {
        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withBody(
                        "This is the content of the test message with yes keyword")
                .addRecipient(
                        new MessageBuilder.Recipient("491623342171", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.setProperty("Yes", ".*yes.*");
        testRunner.setProperty(UCSRouteMessageOnContent.CASE_SENSITIVE,
                UCSRouteMessageOnContent.TRUE);

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred("Yes", 1);
        final MockFlowFile out = testRunner.getFlowFilesForRelationship("Yes")
                .get(0);
        out.assertAttributeExists(UCSRouteMessageOnContent.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageOnContent.ROUTE_ATTRIBUTE_KEY,
                "Yes");
        out.assertContentEquals(message);

    }

    @Test
    public void doTestFailureRoute() throws InitializationException,
            IOException {
        String message = "Malformed XML";

        testRunner.setProperty("Yes", ".*yes.*");

        testRunner.enqueue(message.getBytes());
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSRouteMessageOnContent.REL_FAILURE, 1);
        final MockFlowFile out = testRunner.getFlowFilesForRelationship(
                UCSRouteMessageOnContent.REL_FAILURE).get(0);
        out.assertContentEquals(message);
    }

}
