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

import java.util.UUID;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.ReadOnlyException;
import org.socraticgrid.hl7.services.uc.exceptions.UCSException;
import org.socraticgrid.hl7.services.uc.exceptions.UpdateException;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class CancelMessageTest extends BaseIntegrationTest {
    private final String messageId = UUID.randomUUID().toString();
    private MessageWrapper messageWrapper;
    
    @Before
    public void sendMessage() throws Exception {
        messageWrapper = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("ealiverti")
                .buildMessageWrapper();
        
        client.sendMessage(new MessageModel(messageWrapper.getMessage()));
        this.sleep();
        this.assertListsSizesAndClear(1, 0, 0, 0);
        
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void cancelAlertTest() throws Exception{
        client.cancelMessage(messageId, true);
        this.sleep();
        
        this.assertListsSizes(0, 0, 1, 0);
        assertThat(alertingCancelledMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Retracted'
        assertThat(alertingCancelledMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Retracted));
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void doubleCancelAlertTest() throws Exception{
        client.cancelMessage(messageId, true);
        this.sleep();
        
        this.assertListsSizes(0, 0, 1, 0);
        assertThat(alertingCancelledMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Retracted'
        assertThat(alertingCancelledMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Retracted));
        
        this.clearLists();
        
        //cancelling and already cancelled alert generates no error.
        client.cancelMessage(messageId, true);
        this.sleep();
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void cancelUnknownAlertIdTest() throws Exception{
        
        try{
            client.cancelMessage(messageId+"xxx", true);
            this.sleep();
        } catch (InvalidMessageException e){
            //expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void cancelInvalidMessageTypeTest() throws Exception{
        
        String newMessageId = UUID.randomUUID().toString();
        
        messageWrapper = new MessageBuilder()
                .withMessageId(newMessageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("ealiverti")
                .buildMessageWrapper();
        
        client.sendMessage(new MessageModel(messageWrapper.getMessage()));
        this.sleep();
        this.assertListsSizes(0, 0, 0, 0);
        
        try{
            client.cancelMessage(newMessageId, true);
            this.sleep();
        } catch (Exception e){
            if (!(e.getCause() instanceof UCSException)){
                throw e;
            }
            //Expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void cancelAlertAndTryToUpdateTest() throws Exception{
        client.cancelMessage(messageId, true);
        this.sleep();
        
        this.assertListsSizes(0, 0, 1, 0);
        assertThat(alertingCancelledMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Retracted'
        assertThat(alertingCancelledMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Retracted));
        
        this.clearLists();
        
        //A retracted message can't be updated.
        AlertMessage alertMessage = (AlertMessage) new AlertMessageBuilder().withStatus(AlertStatus.Acknowledged)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("ealiverti")
                .buildMessageWrapper().getMessage();
        
        try{
            alerting.updateAlert(alertMessage);
            this.sleep();
        } catch (UpdateException e){
            //expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void cancelAcknowledgedAlertTest() throws Exception{
        
        //ACK the alert
        AlertMessage alertMessage = (AlertMessage) new AlertMessageBuilder().withStatus(AlertStatus.Acknowledged)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("ealiverti")
                .buildMessageWrapper().getMessage();
        
        alerting.updateAlert(alertMessage);
        this.sleep();
        this.assertListsSizesAndClear(0, 1, 0, 0);
        
        //Try to cancel the message
        try{
            client.cancelMessage(messageId, true);
            this.sleep();
        } catch (ReadOnlyException e){
            //Expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
}
