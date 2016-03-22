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
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
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
public class UpdateMessageTest extends BaseIntegrationTest {
 
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
    public void alertStatusUpdateTest() throws Exception {
        AlertMessage alertMessage = (AlertMessage) messageWrapper.getMessage();
        alertMessage.getHeader().setAlertStatus(AlertStatus.Acknowledged);
        alerting.updateAlert(alertMessage);
        this.sleep();

        this.assertListsSizes(0, 1, 0, 0);
        assertThat(alertingUpdatedMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Acknowledged'
        assertThat(alertingUpdatedMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Acknowledged));
    }
    
    @Test
    public void alertStatusDoubleUpdateTest() throws Exception {
        AlertMessage alertMessage = (AlertMessage) messageWrapper.getMessage();
        alertMessage.getHeader().setAlertStatus(AlertStatus.Acknowledged);
        alerting.updateAlert(alertMessage);
        this.sleep();

        this.assertListsSizes(0, 1, 0, 0);
        assertThat(alertingUpdatedMessages.get(0).getHeader().getMessageId(), is(messageId));
        //the status changed to 'Acknowledged'
        assertThat(alertingUpdatedMessages.get(0).getHeader().getAlertStatus(), is(AlertStatus.Acknowledged));
        
        this.clearLists();
        
        //Double acknowledging an alert doesn't generate any error.
        alerting.updateAlert(alertMessage);
        this.sleep();

        this.assertListsSizes(0, 0, 0, 0);
    }
    
    @Test
    public void alertStatusInvalidUpdateTest() throws Exception {
        AlertMessage alertMessage = (AlertMessage) messageWrapper.getMessage();
        alertMessage.getHeader().setAlertStatus(AlertStatus.Expired);
        
        try{
            alerting.updateAlert(alertMessage);
            this.sleep();
            fail("Exception expected");
        } catch (UpdateException ex){
            //Expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    @Test
    public void alertStatusInvalidMessageIdTest() throws Exception {
        AlertMessage alertMessage = (AlertMessage) messageWrapper.getMessage();
        alertMessage.getHeader().setMessageId(messageId+"xxx");
        alertMessage.getHeader().setAlertStatus(AlertStatus.Acknowledged);
        
        try{
            alerting.updateAlert(alertMessage);
            this.sleep();
            fail("Exception expected");
        } catch (UpdateException ex){
            ex.printStackTrace();
            //Expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    @Test
    public void alertStatusInvalidMessageTypeTest() throws Exception {
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
        
        AlertMessage alertMessage = (AlertMessage) new AlertMessageBuilder().withStatus(AlertStatus.Acknowledged)
                .withMessageId(newMessageId)
                .withSubject("Subject")
                .withBody("Body")
                .withSender("ealiverti")
                .buildMessageWrapper().getMessage();
        
        try{
            alerting.updateAlert(alertMessage);
            this.sleep();
            fail("Exception expected");
        } catch (UpdateException ex){
            //Expected
        }
        
        this.assertListsSizes(0, 0, 0, 0);
    }
    
}
