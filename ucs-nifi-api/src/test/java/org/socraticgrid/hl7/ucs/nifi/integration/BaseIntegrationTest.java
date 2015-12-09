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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.hamcrest.Matchers.hasSize;
import org.junit.After;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.interfaces.AlertingIntf;
import org.socraticgrid.hl7.services.uc.interfaces.ClientIntf;
import org.socraticgrid.hl7.services.uc.interfaces.ConversationIntf;
import org.socraticgrid.hl7.services.uc.interfaces.ManagementIntf;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.api.UCSNiFiSession;
import org.socraticgrid.hl7.ucs.nifi.util.UCSAlertingAdapter;
import org.socraticgrid.hl7.ucs.nifi.util.UCSClientAdapter;

/**
 *
 * @author esteban
 */
public class BaseIntegrationTest {

    protected List<ProcessingException> exceptions = Collections.synchronizedList(new ArrayList<>());
    protected List<AlertMessage> alertingReceivedMessages = Collections.synchronizedList(new ArrayList<>());
    protected List<AlertMessage> alertingUpdatedMessages = Collections.synchronizedList(new ArrayList<>());
    protected List<AlertMessage> alertingCancelledMessages = Collections.synchronizedList(new ArrayList<>());

    protected UCSNiFiSession session = null;
    protected ClientIntf client;
    protected AlertingIntf alerting;
    protected ManagementIntf management;
    protected ConversationIntf conversation;

    @Before
    public void doBefore() throws IOException, InterruptedException {
        session = new UCSNiFiSession.UCSNiFiSessionBuilder()
                .withAlertingCommandURL("http://localhost:8890/contentListener")
                .withClientCommandURL("http://localhost:8889/contentListener")
                .withManagementCommandURL("http://localhost:8891/contentListener")
                .withConversationCommandURL("http://localhost:8892/contentListener")
                .withNifiSendMessageURL("http://localhost:8888/contentListener")
                .withUCSClientHost("localhost")
                .withUCSClientPort(8555)
                .withUCSClientListener(new UCSClientAdapter() {

                    @Override
                    public <T extends Message> boolean handleException(MessageModel<T> messageModel, DeliveryAddress sender, DeliveryAddress receiver, ProcessingException exp, String serverId) {
                        exceptions.add(exp);
                        return true;
                    }

                })
                .withUCSAlertingListener(new UCSAlertingAdapter() {

                    @Override
                    public <T extends Message> boolean receiveAlertMessage(MessageModel<T> messageModel, List<String> localReceivers, String serverId) {
                        alertingReceivedMessages.add((AlertMessage) messageModel.getMessageType());
                        return true;
                    }

                    @Override
                    public <T extends Message> boolean updateAlertMessage(MessageModel<T> newMessageModel, MessageModel<T> oldMessageModel, List<String> localReceivers, String serverId) {
                        alertingUpdatedMessages.add((AlertMessage) newMessageModel.getMessageType());
                        return true;
                    }

                    @Override
                    public <T extends Message> boolean cancelAlertMessage(MessageModel<T> messageModel, List<String> localReceivers, String serverId) {
                        alertingCancelledMessages.add((AlertMessage) messageModel.getMessageType());
                        return true;
                    }

                })
                .build();

        client = session.getNewClient();
        alerting = session.getNewAlerting();
        management = session.getNewManagement();
        conversation = session.getNewConversation();
    }

    @After
    public void doAfter() throws IOException {
        if (session != null) {
            session.dispose();
        }
    }

    protected void clearLists() {
        this.alertingCancelledMessages.clear();
        this.alertingReceivedMessages.clear();
        this.alertingUpdatedMessages.clear();
        this.exceptions.clear();
    }

    protected void assertListsSizesAndClear(Integer alertingReceivedMessagesSize, Integer alertingUpdatedMessagesSize, Integer alertingCancelledMessagesSize, Integer exceptionsSize) {
        this.assertListsSizes(alertingReceivedMessagesSize, alertingUpdatedMessagesSize, alertingCancelledMessagesSize, exceptionsSize);
        this.clearLists();
    }

    protected void assertAllListsAreClear() {
        this.assertListsSizes(0, 0, 0, 0);
    }
    
    protected void assertListsSizes(Integer alertingReceivedMessagesSize, Integer alertingUpdatedMessagesSize, Integer alertingCancelledMessagesSize, Integer exceptionsSize) {
        if (alertingReceivedMessagesSize != null) {
            assertThat("alertingReceivedMessages size is not what we expected.", alertingReceivedMessages, hasSize(alertingReceivedMessagesSize));
        }
        if (alertingUpdatedMessagesSize != null) {
            assertThat("alertingUpdatedMessages size is not what we expected.", alertingUpdatedMessages, hasSize(alertingUpdatedMessagesSize));
        }
        if (alertingCancelledMessagesSize != null) {
            assertThat("alertingCancelledMessages size is not what we expected.", alertingCancelledMessages, hasSize(alertingCancelledMessagesSize));
        }
        if (exceptionsSize != null) {
            assertThat("exceptions size is not what we expected.", exceptions, hasSize(exceptionsSize));
        }
    }

    protected void sleep() {
        this.sleep(2000);
    }

    protected void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
