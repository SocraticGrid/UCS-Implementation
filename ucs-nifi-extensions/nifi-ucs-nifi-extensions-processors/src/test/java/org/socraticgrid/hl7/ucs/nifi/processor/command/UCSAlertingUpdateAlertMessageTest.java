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
package org.socraticgrid.hl7.ucs.nifi.processor.command;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.AlertMessageHeader;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author pavan
 */
public class UCSAlertingUpdateAlertMessageTest extends UCSControllerServiceBasedTest {

    @Test
    public void doTestMissingArguments() throws MessageSerializationException, IOException {

        //arg1 and arg2 missing
        Map<String, String> attributes = this.createBasicAttributes(null, null);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_FAILURE);
        testRunner.clearTransferState();
        
        //arg1 missing
        attributes = this.createBasicAttributes(null, AlertStatus.Acknowledged);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_FAILURE);
        testRunner.clearTransferState();
        
        //arg2 missing
        attributes = this.createBasicAttributes(UUID.randomUUID().toString(), null);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_FAILURE);
        testRunner.clearTransferState();
    }
    
    @Test
    public void doTestMismatchStatus() throws MessageSerializationException, IOException {
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new AlertMessageBuilder().withStatus(AlertStatus.Expired)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        Map<String, String> attributes = this.createBasicAttributes(messageId, AlertStatus.Acknowledged);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_STATUS_MISSMATCH);
        
        //the status of the message in UCSControllerService was not updated.
        assertThat(((AlertMessageHeader)controller.getMessageById(messageId).get().getHeader()).getAlertStatus(), is(AlertStatus.Expired));

    }

    @Test
    public void doTestMismatchStatus1() throws MessageSerializationException, IOException {
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new AlertMessageBuilder().withStatus(AlertStatus.Acknowledged)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        Map<String, String> attributes = this.createBasicAttributes(messageId, AlertStatus.Expired);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_STATUS_MISSMATCH);

        //the status of the message in UCSControllerService was not updated.
        assertThat(((AlertMessageHeader)controller.getMessageById(messageId).get().getHeader()).getAlertStatus(), is(AlertStatus.Acknowledged));
    }
    
    @Test
    public void doTestMismatchType() throws MessageSerializationException, IOException {
        //Create a new Message (not AlertMessage) and persist it in UCSControllerService
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new MessageBuilder()
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        Map<String, String> attributes = this.createBasicAttributes(messageId, AlertStatus.Acknowledged);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_FAILURE);

    }

    @Test
    public void doTestSuccessStatus() throws MessageSerializationException, IOException {
        //Create a new Message (not AlertMessage) and persist it in UCSControllerService
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new AlertMessageBuilder().withStatus(AlertStatus.Pending)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        Map<String, String> attributes = this.createBasicAttributes(messageId, AlertStatus.Acknowledged);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_SUCCESS);

        //the status of the message in UCSControllerService has been updated.
        assertThat(((AlertMessageHeader)controller.getMessageById(messageId).get().getHeader()).getAlertStatus(), is(AlertStatus.Acknowledged));
    }

    @Test
    public void doTestNoUpdateStatus() throws MessageSerializationException, IOException {
        //Create a new Message (not AlertMessage) and persist it in UCSControllerService
        String messageId = UUID.randomUUID().toString();
        MessageWrapper mw = new AlertMessageBuilder().withStatus(AlertStatus.Acknowledged)
                .withMessageId(messageId)
                .withSubject("Subject")
                .withBody("Body")
                .buildMessageWrapper();

        controller.saveMessage(mw.getMessage());

        Map<String, String> attributes = this.createBasicAttributes(messageId, AlertStatus.Acknowledged);

        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred(UCSAlertingUpdateAlertMessage.REL_NO_UPDATE);
        
        //the status of the message in UCSControllerService is still the same.
        assertThat(((AlertMessageHeader)controller.getMessageById(messageId).get().getHeader()).getAlertStatus(), is(AlertStatus.Acknowledged));

    }
    
    private Map<String, String> createBasicAttributes(String messageId, AlertStatus newStatus) {
        Map<String, String> result = this.createBasicAttributes();
        if (messageId != null){
            result.put("command.args.1", messageId);
        }
        if (newStatus != null){
            result.put("command.args.2", newStatus.name());
        }
        
        return result;
    }
    
    private Map<String, String> createBasicAttributes(){
        Map<String, String> result = new HashMap<>();
        result.put("command.name", "updateAlertMessage");
        
        return result;
    }

    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSAlertingUpdateAlertMessage());
    }
}
