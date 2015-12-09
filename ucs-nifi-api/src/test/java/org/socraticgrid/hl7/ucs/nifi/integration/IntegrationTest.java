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
package org.socraticgrid.hl7.ucs.nifi.integration;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Ignore;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.exceptions.ReadOnlyException;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.services.uc.model.MessageSummary;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;

/**
 *
 * @author esteban
 */
public class IntegrationTest extends BaseIntegrationTest {

    @Test
    @Ignore("We need a running instance of NiFi here")
    public void testSendMessageAndCheck() throws Exception {
        
        final String messageId = UUID.randomUUID().toString();
        
        MessageWrapper messageWrapper = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("eafry")
                .buildMessageWrapper();
        
        List<MessageSummary> queryMessage = client.queryMessage(null);
        assertThat(queryMessage.stream()
                .filter(ms -> messageId.equals(ms.getMessageId()))
                .findAny().isPresent(), 
                is(false)
        );

        client.sendMessage(new MessageModel(messageWrapper.getMessage()));
        this.sleep();
        
        queryMessage = client.queryMessage(null);
        
        Set<String> messagesIds = queryMessage.stream()
                .filter(ms -> messageId.equals(ms.getMessageId()))
                .map(ms -> ms.getMessageId())
                .collect(toSet());
        
        assertThat(messagesIds, hasSize(1));
    }

    @Test
    @Ignore("We need a running instance of NiFi here")
    public void testMessageWithUnknownConversation() throws Exception {
        final String conversationId = "UNKNOWN";
        final String messageId = UUID.randomUUID().toString();
        
        MessageWrapper messageWrapper = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withConversationId(conversationId)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("eafry")
                .buildMessageWrapper();
        
        client.sendMessage(new MessageModel(messageWrapper.getMessage()));
        this.sleep();
        
        this.assertListsSizes(0, 0, 0, 1);
        
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void testAlertMessageAndLifeCycle1() throws Exception {

        final String messageId = UUID.randomUUID().toString();

        MessageWrapper messageWrapper = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender(messageId)
                .buildMessageWrapper();

        List<MessageSummary> messages = client.queryMessage(null);
        assertThat(messages.stream().filter(m -> m.getMessageId().equals(messageId)).collect(toList()), is(empty()));

        client.sendMessage(new MessageModel(messageWrapper.getMessage()));
        this.sleep();
        messages = client.queryMessage(null);

        assertThat(messages.stream().filter(m -> m.getMessageId().equals(messageId)).collect(toList()), hasSize(1));

        this.assertListsSizes(1, 0, 0, 0);
        assertThat(alertingReceivedMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Pending'
        assertThat(alertingReceivedMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Pending));

        this.clearLists();

        AlertMessage alertMessage = (AlertMessage) messageWrapper.getMessage();
        alertMessage.getHeader().setAlertStatus(AlertStatus.Acknowledged);
        alerting.updateAlert(alertMessage);
        this.sleep();

        this.assertListsSizes(0, 1, 0, 0);
        assertThat(alertingUpdatedMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Acknowledged'
        assertThat(alertingUpdatedMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Acknowledged));

        this.clearLists();

        //An Alert that is already Acknowledged can't be cancelled.
        try {
            client.cancelMessage(messageId, true);
            this.sleep();
            fail("Exception expected");
        } catch (ReadOnlyException e) {
            System.out.println("Expected");
            //expected
        }
        this.assertListsSizes(0, 0, 0, 0);

    }
}
