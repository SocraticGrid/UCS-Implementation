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
package org.socraticgrid.hl7.ucs.nifi.controller.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.annotation.lifecycle.OnShutdown;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.jivesoftware.smack.PacketInterceptor;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusController;

/**
 *
 * @author esteban
 */
public class ChatControllerService extends AbstractControllerService implements ChatController {

    public static final PropertyDescriptor CHAT_SERVER_URL = new PropertyDescriptor.Builder()
            .name("server-url")
            .description("The URL of the chat server")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CHAT_SERVER_USERNAME = new PropertyDescriptor.Builder()
            .name("server-username")
            .description("The Username used to connect to the chat server")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CHAT_SERVER_PASSWORD = new PropertyDescriptor.Builder()
            .name("server-password")
            .description("The Password used to connect to the chat server")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor SERVICE_STATUS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("service-status-controller")
            .description(
                    "The Service Status Controller Service that this Processor uses to update the Status of this Adapter.")
            .identifiesControllerService(ServiceStatusController.class)
            .required(true)
            .build();

    private String url;
    private String username;
    private String password;

    private AtomicReference<XMPPConnection> connectionReference;
    private final Map<String, MultiUserChat> chatsByRoomId = new ConcurrentHashMap<>();
    private final Map<String, ChatMessage> messagesByMessageId = new ConcurrentHashMap<>();
    private ServiceStatusController serviceStatusControllerService;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {

        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(CHAT_SERVER_URL);
        descriptors.add(CHAT_SERVER_USERNAME);
        descriptors.add(CHAT_SERVER_PASSWORD);
        descriptors.add(SERVICE_STATUS_CONTROLLER_SERVICE);
        return descriptors;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws InitializationException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        try {
            serviceStatusControllerService = context.getProperty(SERVICE_STATUS_CONTROLLER_SERVICE).asControllerService(ServiceStatusController.class);
            this.url = context.getProperty(CHAT_SERVER_URL).getValue();
            this.username = context.getProperty(CHAT_SERVER_USERNAME).getValue();
            this.password = context.getProperty(CHAT_SERVER_PASSWORD).getValue();

            //conect to the server
            //Apparently, a connection automatically adds a retry mechanism.
            //See ReconnectionManager for more info.
            connectionReference = new AtomicReference(new XMPPConnection(this.url));
            SASLAuthentication.supportSASLMechanism("PLAIN", 0);

            connectionReference.get().connect();
            connectionReference.get().login(this.username, this.password);

            //mark any message being sent by this connection.
            connectionReference.get().addPacketWriterInterceptor(new PacketInterceptor() {
                @Override
                public void interceptPacket(Packet packet) {
                    packet.setProperty("ucs-created", "true");
                }
            }, new PacketTypeFilter(Message.class));

        } catch (XMPPException ex) {
            throw new InitializationException("Error initializing ChatControllerService", ex);
        }
    }

    @OnShutdown
    public void onShutdown() {
        if (connectionReference.get() != null && connectionReference.get().isConnected()) {
            connectionReference.get().disconnect();
        }
    }

    @Override
    public void sendMessageToFixedRoom(String chatRoomId, String message, String sender) throws Exception {
        try {
            MultiUserChat muc = chatsByRoomId.computeIfAbsent(chatRoomId, s -> {
                MultiUserChat multiUserChat = new MultiUserChat(connectionReference.get(), chatRoomId);
                multiUserChat.addMessageListener(new ChatPacketListener(chatRoomId, messagesByMessageId));
                return multiUserChat;
            });

            //we join as the sender of the message and not as the user we are logged in as.
            muc.join(sender);

            muc.sendMessage(message);
            if (serviceStatusControllerService != null) {
                // Updating CHAT Adapter status to Available
                serviceStatusControllerService.updateServiceStatus("CHAT", Status.AVAILABLE);
            }
        } catch (Exception e) {
            if (serviceStatusControllerService != null) {
                // Updating CHAT Adapter status to Unavailable
                serviceStatusControllerService.updateServiceStatus("CHAT", Status.UNAVAILABLE);
            }
            throw e;
        }
    }

    @Override
    public void sendMessageToDynamicRoom(String chatRoomId, String roomSubject, String message, String sender, List<String> participants) throws Exception {
        try {
            MultiUserChat muc = chatsByRoomId.computeIfAbsent(chatRoomId, s -> {
                MultiUserChat multiUserChat = new MultiUserChat(connectionReference.get(), chatRoomId);
                multiUserChat.addMessageListener(new ChatPacketListener(chatRoomId, messagesByMessageId));
                return multiUserChat;
            });

            // Create the room or use a room that already exist
            boolean newRoom = false;
            try {
                muc.create(username);
                newRoom = true;
                muc.changeSubject(roomSubject);
            } catch (Exception e) {
                //TODO: is a better way to know if a room already exist?
            }

            if (newRoom) {
                Form form = muc.getConfigurationForm();
                Form submitForm = form.createAnswerForm();
                for (Iterator<FormField> fields = form.getFields(); fields.hasNext();) {
                    FormField field = (FormField) fields.next();
                    if (!FormField.TYPE_HIDDEN.equals(field.getType()) && field.getVariable() != null) {
                        submitForm.setDefaultAnswer(field.getVariable());
                    }
                }

                //TODO: this room will live forever!
                submitForm.setAnswer("muc#roomconfig_publicroom", true);
                submitForm.setAnswer("muc#roomconfig_persistentroom", true);
                submitForm.setAnswer("muc#roomconfig_publicroom", false);
                muc.sendConfigurationForm(submitForm);
            }

            //we join as the sender of the message and not as the user we are logged in as.
            muc.join(sender);

	        //send invitation to participants
            //TODO: should we do this ALWAYS?
            for (String participant : participants) {
                muc.invite(participant, "Join Group Notification");
            }

            //send message to new group
            muc.sendMessage(message);
            if (serviceStatusControllerService != null) {
                // Updating CHAT Adapter status to Available
                serviceStatusControllerService.updateServiceStatus("CHAT", Status.AVAILABLE);
            }
        } catch (Exception e) {
            if (serviceStatusControllerService != null) {
                // Updating CHAT Adapter status to Unavailable
                serviceStatusControllerService.updateServiceStatus("CHAT", Status.UNAVAILABLE);
            }
            throw e;
        }
    }

    @Override
    public void sendMessageToSingleParticipant(String chatRoomId, String roomSubject, String message, String sender, String participant) throws Exception {
        //For the time being, this method works as a group message with only 1 participant
        this.sendMessageToDynamicRoom(chatRoomId, roomSubject, message, sender, Arrays.asList(participant));
    }

    @Override
    public synchronized Set<ChatMessage> consumeMessages() {
        try {
            return this.messagesByMessageId.values().stream().collect(Collectors.toSet());
        } finally {
            this.messagesByMessageId.clear();
        }
    }

}
