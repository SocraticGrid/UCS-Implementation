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
package org.socraticgrid.hl7.ucs.nifi.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertMessageHeader;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.MessageBody;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.SimpleMessage;
import org.socraticgrid.hl7.services.uc.model.SimpleMessageHeader;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class SerializationTests {
    
    @Test
    public void messageSerializationTest() throws MessageSerializationException{
        
        String subject = "Test Message";
        String content = "This is the content of the test message";
        String contentType = "text/plain";
        
        String recipient1Id = UUID.randomUUID().toString();
        String recipient2Id = UUID.randomUUID().toString();
        
        String addressId = UUID.randomUUID().toString();
        DeliveryAddress sender = new DeliveryAddress();
        sender.setDeliveryAddressId(addressId);
        sender.setAddress(new PhysicalAddress("SMS", "eafry"));
        
        //2 recipients: 1 is the same as the sender, the other is ealiverti
        Recipient recipient1 = new Recipient();
        recipient1.setRecipientId(recipient1Id);
        recipient1.setDeliveryAddress(sender);
        
        
        Recipient recipient2 = new Recipient();
        recipient2.setRecipientId(recipient2Id);
        recipient2.setDeliveryAddress(new DeliveryAddress(new PhysicalAddress("SMS", "ealiverti")));
        
        
        Set<Recipient> recipients = new HashSet<>();
        recipients.add(recipient1);
        recipients.add(recipient2);
        
        String messageId = UUID.randomUUID().toString();
        SimpleMessageHeader header = new SimpleMessageHeader();
        header.setMessageId(messageId);
        header.setCreated(new Date());
        header.setTimeout(30000);
        header.setSender(sender);
        header.setSubject(subject);
        header.setRecipientsList(recipients);
        header.setReceiptNotification(true);
        
        MessageBody body = new MessageBody();
        body.setContent(content);
        body.setType(contentType);
        
        SimpleMessage message = new SimpleMessage(header);
        message.setParts(new MessageBody[]{body});
        
        MessageWrapper messageWrapper = new MessageWrapper(message);
        
        String serializedMessage = MessageSerializer.serializeMessageWrapper(messageWrapper);
        
        System.out.println("Serialized:\n"+serializedMessage);
        
        MessageWrapper deserializedMessage = MessageSerializer.deserializeMessageWrapper(serializedMessage);

        assertThat(deserializedMessage.getMessage().getHeader().getSubject(), is(subject));
        assertThat(deserializedMessage.getMessage().getParts().length, is(1));
        assertThat(deserializedMessage.getMessage().getParts()[0].getContent(), is(content));
        assertThat(deserializedMessage.getMessage().getParts()[0].getType(), is(contentType));
        
    }
    
    
    @Test
    public void nestedMessageSerializationTest() throws MessageSerializationException{
        
        String subject = "Where are you?";
        String content = "This is the content of the fail message";
        String contentType = "text/plain";
        
        String recipient1Id = UUID.randomUUID().toString();
        
        String addressId = UUID.randomUUID().toString();
        DeliveryAddress sender = new DeliveryAddress();
        sender.setDeliveryAddressId(addressId);
        sender.setAddress(new PhysicalAddress("SMS", "eafry"));
        
        Recipient recipient1 = new Recipient();
        recipient1.setRecipientId(recipient1Id);
        recipient1.setDeliveryAddress(sender);
        
        Set<Recipient> recipients = new HashSet<>();
        recipients.add(recipient1);
        
        String messageId = UUID.randomUUID().toString();
        SimpleMessageHeader header = new SimpleMessageHeader();
        header.setMessageId(messageId);
        header.setCreated(new Date());
        header.setTimeout(30000);
        header.setSender(sender);
        header.setSubject(subject);
        header.setRecipientsList(recipients);
        header.setReceiptNotification(true);
        
        MessageBody body = new MessageBody();
        body.setContent(content);
        body.setType(contentType);
        
        SimpleMessage messageF = new SimpleMessage(header);
        messageF.setParts(new MessageBody[]{body});
        
        
        
        
        String subjectN = "Test Message";
        String contentN = "This is the content of the test message";
        String contentTypeN = "text/plain";
        
        String recipient1NId = UUID.randomUUID().toString();
        String recipient2NId = UUID.randomUUID().toString();
        
        String addressIdN = UUID.randomUUID().toString();
        DeliveryAddress senderN = new DeliveryAddress();
        senderN.setDeliveryAddressId(addressIdN);
        senderN.setAddress(new PhysicalAddress("SMS", "eafry"));
        
        //2 recipients: 1 is the same as the sender, the other is ealiverti
        Recipient recipient1N = new Recipient();
        recipient1N.setRecipientId(recipient1NId);
        recipient1N.setDeliveryAddress(senderN);
        
        
        Recipient recipient2N = new Recipient();
        recipient2N.setRecipientId(recipient2NId);
        recipient2N.setDeliveryAddress(new DeliveryAddress(new PhysicalAddress("SMS", "ealiverti")));
        
        
        Set<Recipient> recipientsN = new HashSet<>();
        recipientsN.add(recipient1N);
        recipientsN.add(recipient2N);
        
        String messageIdN = UUID.randomUUID().toString();
        SimpleMessageHeader headerN = new SimpleMessageHeader();
        headerN.setOnFailureToReachAll(new ArrayList());
        headerN.setMessageId(messageIdN);
        headerN.setCreated(new Date());
        headerN.setTimeout(30000);
        headerN.setSender(senderN);
        headerN.setSubject(subjectN);
        headerN.setRecipientsList(recipientsN);
        headerN.setReceiptNotification(true);
        headerN.getOnFailureToReachAll().add(messageF);
        headerN.getOnFailureToReachAll().add(messageF);
        
        MessageBody bodyN = new MessageBody();
        bodyN.setContent(contentN);
        bodyN.setType(contentTypeN);
        
        SimpleMessage message = new SimpleMessage(headerN);
        message.setParts(new MessageBody[]{bodyN});
        
        MessageWrapper messageWrapper = new MessageWrapper(message);
        
        String serializedMessage = MessageSerializer.serializeMessageWrapper(messageWrapper);
        
        System.out.println("Serialized:\n"+serializedMessage);
        
        MessageWrapper deserializedMessage = MessageSerializer.deserializeMessageWrapper(serializedMessage);

        assertThat(deserializedMessage.getMessage().getHeader().getSubject(), is(subjectN));
        assertThat(deserializedMessage.getMessage().getParts().length, is(1));
        assertThat(deserializedMessage.getMessage().getParts()[0].getContent(), is(contentN));
        assertThat(deserializedMessage.getMessage().getParts()[0].getType(), is(contentTypeN));
        
        assertThat(deserializedMessage.getMessage().getHeader().getOnFailureToReachAll().get(0).getHeader().getSubject(), is(subject));
        assertThat(deserializedMessage.getMessage().getHeader().getOnFailureToReachAll().get(0).getParts().length, is(1));
        assertThat(deserializedMessage.getMessage().getHeader().getOnFailureToReachAll().get(0).getParts()[0].getContent(), is(content));
        assertThat(deserializedMessage.getMessage().getHeader().getOnFailureToReachAll().get(0).getParts()[0].getType(), is(contentType));
        
    }
    
    @Test
    public void messagesSerializationTest() throws MessageSerializationException{
        
        String subject = "Test Message";
        String content = "This is the content of the test message";
        String contentType = "text/plain";
        
        String recipient1Id = UUID.randomUUID().toString();
        String recipient2Id = UUID.randomUUID().toString();
        
        String addressId = UUID.randomUUID().toString();
        DeliveryAddress sender = new DeliveryAddress();
        sender.setDeliveryAddressId(addressId);
        sender.setAddress(new PhysicalAddress("SMS", "eafry"));
        
        //2 recipients: 1 is the same as the sender, the other is ealiverti
        Recipient recipient1 = new Recipient();
        recipient1.setRecipientId(recipient1Id);
        recipient1.setDeliveryAddress(sender);
        
        
        Recipient recipient2 = new Recipient();
        recipient2.setRecipientId(recipient2Id);
        recipient2.setDeliveryAddress(new DeliveryAddress(new PhysicalAddress("SMS", "ealiverti")));
        
        
        Set<Recipient> recipients = new HashSet<>();
        recipients.add(recipient1);
        recipients.add(recipient2);
        
        String messageId = UUID.randomUUID().toString();
        SimpleMessageHeader header = new SimpleMessageHeader();
        header.setMessageId(messageId);
        header.setCreated(new Date());
        header.setTimeout(30000);
        header.setSender(sender);
        header.setSubject(subject);
        header.setRecipientsList(recipients);
        
        MessageBody body = new MessageBody();
        body.setContent(content);
        body.setType(contentType);
        
        SimpleMessage message = new SimpleMessage(header);
        message.setParts(new MessageBody[]{body});
        
        MessageWrapper messageWrapper = new MessageWrapper(message);
        
        List<MessageWrapper> messages = new ArrayList<>();
        messages.add(messageWrapper);
        messages.add(messageWrapper);
        
        String serializedMessages = MessageSerializer.serializeMessageWrappers(messages);
        
        System.out.println("Serialized:\n"+serializedMessages);
        
        XMLListWrapper<MessageWrapper> deserializedMessages = MessageSerializer.deserializeMessageWrappers(serializedMessages);
        
        assertThat(deserializedMessages.getItems().get(0).getMessage().getHeader().getSubject(), is(subject));
        assertThat(deserializedMessages.getItems().get(0).getMessage().getParts().length, is(1));
        assertThat(deserializedMessages.getItems().get(0).getMessage().getParts()[0].getContent(), is(content));
        assertThat(deserializedMessages.getItems().get(0).getMessage().getParts()[0].getType(), is(contentType));
        
    }
    
    
    @Test
    public void alertMessageSerializationTest() throws MessageSerializationException{
        
        String subject = "Test Message";
        String content = "This is the content of the test message";
        String contentType = "text/plain";
        
        String recipient1Id = UUID.randomUUID().toString();
        String recipient2Id = UUID.randomUUID().toString();
        
        String addressId = UUID.randomUUID().toString();
        DeliveryAddress sender = new DeliveryAddress();
        sender.setDeliveryAddressId(addressId);
        sender.setAddress(new PhysicalAddress("SMS", "eafry"));
        
        //2 recipients: 1 is the same as the sender, the other is ealiverti
        Recipient recipient1 = new Recipient();
        recipient1.setRecipientId(recipient1Id);
        recipient1.setDeliveryAddress(sender);
        
        
        Recipient recipient2 = new Recipient();
        recipient2.setRecipientId(recipient2Id);
        recipient2.setDeliveryAddress(new DeliveryAddress(new PhysicalAddress("SMS", "ealiverti")));
        
        
        Set<Recipient> recipients = new HashSet<>();
        recipients.add(recipient1);
        recipients.add(recipient2);
        
        String messageId = UUID.randomUUID().toString();
        AlertMessageHeader header = new AlertMessageHeader();
        header.setAlertStatus(AlertStatus.New);
        header.setMessageId(messageId);
        header.setCreated(new Date());
        header.setTimeout(30000);
        header.setSender(sender);
        header.setSubject(subject);
        header.setRecipientsList(recipients);
        header.setReceiptNotification(true);
        
        MessageBody body = new MessageBody();
        body.setContent(content);
        body.setType(contentType);
        
        AlertMessage message = new AlertMessage(header);
        message.setParts(new MessageBody[]{body});
        
        MessageWrapper messageWrapper = new MessageWrapper(message);
        
        String serializedMessage = MessageSerializer.serializeMessageWrapper(messageWrapper);
        
        System.out.println("Serialized:\n"+serializedMessage);
        
        MessageWrapper deserializedMessage = MessageSerializer.deserializeMessageWrapper(serializedMessage);

        assertThat(deserializedMessage.getMessage().getHeader().getSubject(), is(subject));
        assertThat(deserializedMessage.getMessage().getParts().length, is(1));
        assertThat(deserializedMessage.getMessage().getParts()[0].getContent(), is(content));
        assertThat(deserializedMessage.getMessage().getParts()[0].getType(), is(contentType));
        
    }
    
}
