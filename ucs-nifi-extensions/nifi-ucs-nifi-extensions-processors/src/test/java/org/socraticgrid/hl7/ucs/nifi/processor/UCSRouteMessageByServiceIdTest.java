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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSRouteMessageByServiceIdTest extends UCSControllerServiceBasedTest{

    @Override
    protected void afterInit() throws Exception {
        testRunner.setProperty(UCSRouteMessageByServiceId.REL_NO_MATCH_AS_EXCEPTION, "false");
    }
    
    @Test
    public void doTestNoSMSRoute() throws InitializationException, IOException {
        
        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .buildSerializedMessageWrapper();

        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_ORIGINAL, 1);
        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_NO_MATCH, 1);
        final MockFlowFile out = testRunner.getFlowFilesForRelationship(UCSRouteMessageByServiceId.REL_NO_MATCH).get(0);
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY, UCSRouteMessageByServiceId.REL_NO_MATCH.getName());
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID, "SMS");

    }

    @Test
    public void doTestNoSMSNorEMAILRoute() throws InitializationException, IOException {

        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "EMAIL"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_ORIGINAL, 1);
        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_NO_MATCH, 2);
        
        List<String> attributes = testRunner.getFlowFilesForRelationship(UCSRouteMessageByServiceId.REL_NO_MATCH).stream()
                .map(ff -> ff.getAttribute(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY)+":"+ff.getAttribute(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID))
                .collect(Collectors.toList());
        
        assertThat(attributes, containsInAnyOrder(UCSRouteMessageByServiceId.REL_NO_MATCH.getName()+":SMS", UCSRouteMessageByServiceId.REL_NO_MATCH.getName()+":EMAIL"));
    }

    @Test
    public void doTestNoSMSButEMAILRoute() throws InitializationException, IOException {
        
        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.setProperty("EMAIL", "");
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_ORIGINAL, 1);
        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_NO_MATCH, 1);
        testRunner.assertTransferCount("EMAIL", 1);

        MockFlowFile out = testRunner.getFlowFilesForRelationship(UCSRouteMessageByServiceId.REL_NO_MATCH).get(0);
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY, UCSRouteMessageByServiceId.REL_NO_MATCH.getName());
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID, "SMS");

        out = testRunner.getFlowFilesForRelationship("EMAIL").get(0);
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY, "EMAIL");
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID, "EMAIL");

    }
    
    @Test
    public void doTestSMSAndEMAILRoute() throws InitializationException, IOException {
        
        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "EMAIL"))
                .buildSerializedMessageWrapper();

        testRunner.setProperty("EMAIL", "");
        testRunner.setProperty("SMS", "");
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();

        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_ORIGINAL, 1);
        testRunner.assertTransferCount(UCSRouteMessageByServiceId.REL_NO_MATCH, 0);
        testRunner.assertTransferCount("EMAIL", 1);
        testRunner.assertTransferCount("SMS", 1);

        MockFlowFile out = testRunner.getFlowFilesForRelationship("SMS").get(0);
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY, "SMS");
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID, "SMS");

        out = testRunner.getFlowFilesForRelationship("EMAIL").get(0);
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_KEY, "EMAIL");
        out.assertAttributeExists(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID);
        out.assertAttributeEquals(UCSRouteMessageByServiceId.ROUTE_ATTRIBUTE_SERVICE_ID, "EMAIL");

    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSRouteMessageByServiceId());
    }
}
