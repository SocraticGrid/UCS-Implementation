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
package org.socraticgrid.hl7.ucs.nifi.test.workbench.command;

import com.google.gson.JsonObject;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.BadBodyException;
import org.socraticgrid.hl7.services.uc.exceptions.FeatureNotSupportedException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidContentException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.MissingBodyTypeException;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.UndeliverableMessageException;
import org.socraticgrid.hl7.services.uc.interfaces.UCSAlertingIntf;
import org.socraticgrid.hl7.services.uc.interfaces.UCSClientIntf;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.api.UCSNiFiSession;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.UCSClientWS;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.converter.ToJSONConverter;

/**
 *
 * @author esteban
 */
public class CreateUCSSessionCommand implements Command {

    private final static Logger LOG = LoggerFactory.getLogger(CreateUCSSessionCommand.class);

    private static final Object lastUCSSessionLock = new Object();
    private static UCSNiFiSession lastSession;

    private String nifiHost;
    private int nifiSendMessageCommandPort;
    private int nifiClientCommandPort;
    private int nifiAlertingCommandPort;
    private int nifiManagementCommandPort;
    private int nifiConversationCommandPort;

    private String clientsHost;

    @Override
    public void init(JsonObject config) {
        nifiHost = config.get("nifiHost").getAsString();
        nifiClientCommandPort = config.get("nifiClientCommandPort").getAsInt();
        nifiAlertingCommandPort = config.get("nifiAlertingCommandPort").getAsInt();
        nifiManagementCommandPort = config.get("nifiManagementCommandPort").getAsInt();
        nifiConversationCommandPort = config.get("nifiConversationCommandPort").getAsInt();
        nifiSendMessageCommandPort = config.get("nifiSendMessagePort").getAsInt();

        clientsHost = config.get("clientsHost").getAsString();
    }

    @Override
    public JsonObject execute() {
        synchronized (lastUCSSessionLock) {

            if (lastSession != null) {
                try {
                    lastSession.dispose();
                } catch (Exception ex) {
                    LOG.warn("Error disposing previous UCS Session.");
                }
            }

            try {
                lastSession = new UCSNiFiSession.UCSNiFiSessionBuilder()
                    .withNifiHost(nifiHost)
                    .withNifiClientCommandPort(nifiClientCommandPort)
                    .withNifiAlertingCommandPort(nifiAlertingCommandPort)
                    .withNifiManagementCommandPort(nifiManagementCommandPort)
                    .withNifiConversationCommandPort(nifiConversationCommandPort)
                    .withNifiSendMessageCommandPort(nifiSendMessageCommandPort)
                    .withClientHost(clientsHost)
                    .withUCSClientListener(new UCSClientIntf() {

                        @Override
                        public boolean callReady(Conversation conversation, String callHandle, String serverId) {
                            return false;
                        }

                        @Override
                        public <T extends Message> boolean handleException(MessageModel<T> messageModel, DeliveryAddress sender, DeliveryAddress receiver, ProcessingException exp, String serverId) {
                            JsonObject exception = new JsonObject();
                            exception.add("message", ToJSONConverter.toJsonObject(messageModel.getMessageType()));
                            exception.add("sender", ToJSONConverter.toJsonObject(sender));
                            exception.add("receiver", ToJSONConverter.toJsonObject(receiver));
                            exception.add("exception", ToJSONConverter.toJsonObject(exp));
                            exception.addProperty("serverId", serverId);

                            UCSClientWS.broadcast(UCSClientWS.MESSAGE_TYPE.EXCEPTION, exception);

                            return true;
                        }

                        @Override
                        public <T extends Message> boolean handleNotification(MessageModel<T> messageModel, String serverId) {
                            return false;
                        }

                        @Override
                        public <T extends Message> MessageModel<T> handleResponse(MessageModel<T> messageModel, String serverId) throws InvalidMessageException, InvalidContentException, MissingBodyTypeException, BadBodyException, ServiceAdapterFaultException, UndeliverableMessageException, FeatureNotSupportedException {
                            JsonObject message = ToJSONConverter.toJsonObject(messageModel.getMessageType());
                            UCSClientWS.broadcast(UCSClientWS.MESSAGE_TYPE.RESPONSE, message);
                            return null;
                        }

                        @Override
                        public <T extends Message> boolean receiveMessage(MessageModel<T> messageModel, String serverId) {
                            JsonObject message = ToJSONConverter.toJsonObject(messageModel.getMessageType());
                            UCSClientWS.broadcast(UCSClientWS.MESSAGE_TYPE.MESSAGE, message);
                            return false;
                        }
                    })
                    .withUCSAlertingListener(new UCSAlertingIntf() {

                        @Override
                        public <T extends Message> boolean receiveAlertMessage(MessageModel<T> messageModel, List<String> localReceivers, String serverId) {
                            JsonObject message = ToJSONConverter.toJsonObject(messageModel.getMessageType());
                            UCSClientWS.broadcast(UCSClientWS.MESSAGE_TYPE.ALERT_NEW, message);
                            return false;
                        }

                        @Override
                        public <T extends Message> boolean updateAlertMessage(MessageModel<T> newMessageModel, MessageModel<T> oldMessageModel, List<String> localReceivers, String serverId) {
                            JsonObject message = ToJSONConverter.toJsonObject(newMessageModel.getMessageType());
                            UCSClientWS.broadcast(UCSClientWS.MESSAGE_TYPE.ALERT_UPDATED, message);
                            return false;
                        }

                        @Override
                        public <T extends Message> boolean cancelAlertMessage(MessageModel<T> messageModel, List<String> localReceivers, String serverId) {
                            JsonObject message = ToJSONConverter.toJsonObject(messageModel.getMessageType());
                            UCSClientWS.broadcast(UCSClientWS.MESSAGE_TYPE.ALERT_CANCELED, message);
                            return false;
                        }

                    })
                    .build();

                return new JsonObject();

            } catch (Exception ex) {
                throw new IllegalStateException("Exception creating UCS Session", ex);
            }
        }
    }

    public static UCSNiFiSession getLastSession() {
        synchronized (lastUCSSessionLock) {
            return lastSession;
        }
    }

}
