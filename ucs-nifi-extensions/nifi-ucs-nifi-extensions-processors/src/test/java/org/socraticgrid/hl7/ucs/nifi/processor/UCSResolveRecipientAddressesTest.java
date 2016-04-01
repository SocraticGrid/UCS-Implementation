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
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class UCSResolveRecipientAddressesTest extends UCSControllerServiceBasedTest{

    @Test
    public void testResolveSMSAddresses() throws MessageSerializationException, IOException {

        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("eafry", "SMS"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSResolveRecipientAddresses.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSResolveRecipientAddresses.REL_SUCCESS).get(0);
        String content = new String(flowFile.toByteArray());
        
        Message m = MessageSerializer.deserializeMessageWrapper(content).getMessage();
        assertThat(m.getHeader().getDeliveryStatusList(), is(nullValue()));
        assertThat(m.getHeader().getRecipientsList(), hasSize(2));
        assertThat(
            m.getHeader().getRecipientsList().stream()
                .map(r -> r.getDeliveryAddress().getPhysicalAddress().getAddress())
                .collect(Collectors.toList())
            , containsInAnyOrder("12345678901","09876543212"));
    }
    
    @Test
    public void testResolveSMSAndEMAILAddresses() throws MessageSerializationException, IOException {

        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "EMAIL"))
                .addRecipient(new MessageBuilder.Recipient("eafry", "SMS"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSResolveRecipientAddresses.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSResolveRecipientAddresses.REL_SUCCESS).get(0);
        String content = new String(flowFile.toByteArray());
        
        Message m = MessageSerializer.deserializeMessageWrapper(content).getMessage();
        assertThat(m.getHeader().getDeliveryStatusList(), is(nullValue()));
        assertThat(m.getHeader().getRecipientsList(), hasSize(2));
        
        //group the different recipient addresses by their systemId
        Map<String, List<PhysicalAddress>> addresses = m.getHeader().getRecipientsList().stream()
                .map(r -> r.getDeliveryAddress().getPhysicalAddress())
                .collect(Collectors.groupingBy(a -> a.getServiceId()));
        
        assertThat(addresses.keySet(), containsInAnyOrder("SMS","EMAIL"));
        
        assertThat(addresses.get("SMS"), hasSize(1));
        assertThat(addresses.get("SMS").get(0).getAddress(), is("12345678901"));
        
        assertThat(addresses.get("EMAIL"), hasSize(1));
        assertThat(addresses.get("EMAIL").get(0).getAddress(), is("ealiverti@cognitivemedicine.com"));
        
    }

    
    @Test
    public void testResolveUnsupportedAddresses() throws MessageSerializationException {

        String message = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><messageWrapper xmlns:model=\"http://org.socraticgrid.hl7.services.uc.model\" xmlns:exceptions=\"http://org.socraticgrid.hl7.services.uc.exceptions\">\n"
                + "    <message xsi:type=\"model:simpleMessage\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "        <model:parts>\n"
                + "            <model:content>This is the content of the test message</model:content>\n"
                + "            <model:type>text/plain</model:type>\n"
                + "        </model:parts>\n"
                + "        <model:simpleMessageHeader>\n"
                + "            <model:messageId>ff26b0af-0b7d-47ee-89f9-06ed13cf7fee</model:messageId>\n"
                + "            <model:sender>\n"
                + "                <model:physicaladdress>\n"
                + "                    <model:address>eafry</model:address>\n"
                + "                    <model:serviceId>SMS</model:serviceId>\n"
                + "                </model:physicaladdress>\n"
                + "            </model:sender>\n"
                + "            <model:recipientsList>\n"
                + "                <model:deliveryAddress>\n"
                + "                    <model:physicaladdress>\n"
                + "                        <model:address>ealiverti</model:address>\n"
                + "                        <model:serviceId>EMAIL</model:serviceId>\n"
                + "                    </model:physicaladdress>\n"
                + "                </model:deliveryAddress>\n"
                + "                <model:deliveryReceipt>false</model:deliveryReceipt>\n"
                + "                <model:readReceipt>false</model:readReceipt>\n"
                + "                <model:role/>\n"
                + "                <model:visibility>Public</model:visibility>\n"
                + "            </model:recipientsList>\n"
                + "            <model:recipientsList>\n"
                + "                <model:deliveryAddress>\n"
                + "                    <model:party>\n"
                + "                        <model:name>engineers</model:name>\n"
                + "                    </model:party>\n"
                + "                </model:deliveryAddress>\n"
                + "                <model:deliveryReceipt>false</model:deliveryReceipt>\n"
                + "                <model:readReceipt>false</model:readReceipt>\n"
                + "                <model:role/>\n"
                + "                <model:visibility>Public</model:visibility>\n"
                + "            </model:recipientsList>\n"
                + "            <model:subject>Test Message</model:subject>\n"
                + "            <model:created>2015-02-26T14:54:19.740+01:00</model:created>\n"
                + "            <model:lastModified>2015-02-26T14:54:19.739+01:00</model:lastModified>\n"
                + "            <model:deliveryGuarantee>BestEffort</model:deliveryGuarantee>\n"
                + "            <model:dynamics>Asynchronous</model:dynamics>\n"
                + "            <model:priority>0</model:priority>\n"
                + "            <model:receiptNotification>false</model:receiptNotification>\n"
                + "            <model:retainFullyInLog>false</model:retainFullyInLog>\n"
                + "            <model:timeout>30000</model:timeout>\n"
                + "        </model:simpleMessageHeader>\n"
                + "    </message>\n"
                + "</messageWrapper>";
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSResolveRecipientAddresses.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSResolveRecipientAddresses.REL_SUCCESS).get(0);
        String content = new String(flowFile.toByteArray());
        
        Message m = MessageSerializer.deserializeMessageWrapper(content).getMessage();
        assertThat(m.getHeader().getDeliveryStatusList(), hasSize(1));
        
        
        assertThat(m.getHeader().getRecipientsList(), hasSize(2));
        
        //group the different recipient addresses by their systemId
        Map<String, List<PhysicalAddress>> addresses = m.getHeader().getRecipientsList().stream()
                .filter(r -> r.getDeliveryAddress() != null)
                .map(r -> r.getDeliveryAddress().getPhysicalAddress())
                .collect(Collectors.groupingBy(a -> a.getServiceId()));
        
        assertThat(addresses.keySet(), containsInAnyOrder("EMAIL"));
        
        assertThat(addresses.get("EMAIL"), hasSize(1));
        assertThat(addresses.get("EMAIL").get(0).getAddress(), is("ealiverti@cognitivemedicine.com"));
        
    }
    
    @Test
    public void testUnknownUserAddresses() throws MessageSerializationException, IOException {

        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("unknown-user", "SMS"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertTransferCount(UCSResolveRecipientAddresses.REL_SUCCESS, 1);
        MockFlowFile successFlowFile = testRunner.getFlowFilesForRelationship(UCSResolveRecipientAddresses.REL_SUCCESS).get(0);
        String content = new String(successFlowFile.toByteArray());
        
        Message m = MessageSerializer.deserializeMessageWrapper(content).getMessage();
        assertThat(m.getHeader().getDeliveryStatusList(), is(nullValue()));
        assertThat(m.getHeader().getRecipientsList(), hasSize(2));
        assertThat(
            m.getHeader().getRecipientsList().stream()
                .filter(r -> r.getDeliveryAddress() != null)
                .map(r -> r.getDeliveryAddress().getPhysicalAddress().getAddress())
                .collect(Collectors.toList())
            , containsInAnyOrder("09876543212"));
        
        testRunner.assertTransferCount(UCSResolveRecipientAddresses.REL_UNKNOWN_RECIPIENT, 1);
    }
    
    @Test
    public void testGroupPrefixedAddress() throws MessageSerializationException, IOException {

        //CHAT recipients whose address starts with "GROUP:" are not resolved
        //by this processor.
        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("GROUP:Room A", "CHAT"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSResolveRecipientAddresses.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSResolveRecipientAddresses.REL_SUCCESS).get(0);
        String content = new String(flowFile.toByteArray());
        
        Message m = MessageSerializer.deserializeMessageWrapper(content).getMessage();
        assertThat(m.getHeader().getDeliveryStatusList(), is(nullValue()));
        assertThat(m.getHeader().getRecipientsList(), hasSize(1));
        assertThat(m.getHeader().getRecipientsList().iterator().next().getDeliveryAddress().getPhysicalAddress().getAddress(), is("GROUP:Room A"));
        
    }
    
    @Test
    public void testInvalidGroupPrefixedAddress() throws MessageSerializationException, IOException {

        //CHAT recipients whose address starts with "GROUP:" are not resolved
        //by this processor. If the systemId is not CHAT, the processor will
        //attempt to resolve the address though.
        String message = new MessageBuilder()
                .withSender("eafry")
                .addRecipient(new MessageBuilder.Recipient("GROUP:Room A", "SMS"))
                .buildSerializedMessageWrapper();
        
        testRunner.enqueue(message.getBytes());
        testRunner.run();
        
        testRunner.assertTransferCount(UCSResolveRecipientAddresses.REL_SUCCESS, 1);
        testRunner.assertTransferCount(UCSResolveRecipientAddresses.REL_UNKNOWN_RECIPIENT, 1);
        
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSResolveRecipientAddresses());
    }
}
