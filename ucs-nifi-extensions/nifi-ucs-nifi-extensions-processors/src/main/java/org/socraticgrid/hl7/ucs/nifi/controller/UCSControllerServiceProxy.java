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
package org.socraticgrid.hl7.ucs.nifi.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.annotation.lifecycle.OnShutdown;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.quartz.SchedulerException;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.QueryFilter;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.model.ResolvedAddresses;
import org.socraticgrid.hl7.ucs.nifi.common.model.UCSStatus;
import org.socraticgrid.hl7.ucs.nifi.controller.store.MessageStoreController;
import org.socraticgrid.hl7.ucs.nifi.controller.user.UserContactInfoResolverController;
import org.socraticgrid.hl7.ucs.nifi.processor.model.MessageWithUnreachableHandlers;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage;

/**
 *
 * @author esteban
 */
public class UCSControllerServiceProxy extends AbstractControllerService implements UCSController {

    public static final PropertyDescriptor MESSAGE_STORE_IMPL = new PropertyDescriptor.Builder()
            .name("MessageStoreController")
            .description("The name of a concrete implementation of MessageStoreController interface")
            .required(true)
            .identifiesControllerService(MessageStoreController.class)
            .build();

    public static final PropertyDescriptor USER_CONTACT_INFO_RESOLVER_IMPL = new PropertyDescriptor.Builder()
            .name("UserContactInfoController")
            .description("A concrete implementation UserContactInfoResolverController interface")
            .required(true)
            .identifiesControllerService(UserContactInfoResolverController.class)
            .build();

    public static final PropertyDescriptor SERVICE_STATUS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("ServiceStatusControllerService")
            .description(
                    "The Service Status Controller Service that this Processor uses to update the Status of this Adapter.")
            .identifiesControllerService(ServiceStatusController.class)
            .required(true).build();

    private UCSControllerService service;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(MESSAGE_STORE_IMPL);
        descriptors.add(USER_CONTACT_INFO_RESOLVER_IMPL);
        descriptors.add(SERVICE_STATUS_CONTROLLER_SERVICE);
        return descriptors;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws Exception {

        UserContactInfoResolverController userContactInfoResolver = context.getProperty(USER_CONTACT_INFO_RESOLVER_IMPL).asControllerService(UserContactInfoResolverController.class);

        MessageStoreController messageStore = context.getProperty(MESSAGE_STORE_IMPL).asControllerService(MessageStoreController.class);

        ServiceStatusController serviceStatusControllerService = context.getProperty(SERVICE_STATUS_CONTROLLER_SERVICE).asControllerService(ServiceStatusController.class);

        this.service = new UCSControllerServiceImpl.UCSControllerServiceImplBuilder().setMessageStore(messageStore).setUserContactInfoResolver(userContactInfoResolver).setServiceStatusController(serviceStatusControllerService).build();

        this.service.start();
    }

    @OnShutdown
    public void onShutdown() throws Exception {
        this.service.stop();
    }

    @Override
    public ResolvedAddresses resolvePhysicalAddressesByServiceId(Message message) {
        return this.service.resolvePhysicalAddressesByServiceId(message);
    }

    @Override
    public UserContactInfo resolveUserContactInfo(String userId) {
        return this.service.resolveUserContactInfo(userId);
    }

    @Override
    public void start() {
        //nobody is never going to call this method.
    }

    @Override
    public void stop() {
        //nobody is never going to call this method.
    }

    @Override
    public void saveMessage(Message message) {
        this.service.saveMessage(message);
    }

    @Override
    public void updateMessage(Message message) {
        this.service.updateMessage(message);
    }

    @Override
    public Optional<Message> getMessageById(String messageId) {
        return this.service.getMessageById(messageId);
    }

    @Override
    public List<Message> listMessages() {
        return this.service.listMessages();
    }

    @Override
    public List<Message> listMessages(long from, long total) {
        return this.service.listMessages(from, total);
    }

    @Override
    public Set<Message> getRelatedMessages(String messageId) {
        return this.service.getRelatedMessages(messageId);
    }

    @Override
    public void saveMessageReference(Message message, String recipientId, String reference) {
        this.service.saveMessageReference(message, recipientId, reference);
    }

    @Override
    public Optional<Message> getMessageByReference(String reference) {
        return this.service.getMessageByReference(reference);
    }

    @Override
    public Optional<String> getRecipientIdByReference(String reference) {
        return this.service.getRecipientIdByReference(reference);
    }

    @Override
    public boolean isKnownConversation(String conversationId) {
        return this.service.isKnownConversation(conversationId);
    }

    @Override
    public void registerUCSClientCallback(URL callback) {
        this.service.registerUCSClientCallback(callback);
    }

    @Override
    public Set<URL> getUCSClientCallbacks() {
        return this.service.getUCSClientCallbacks();
    }

    @Override
    public void notifyAboutMessageWithUnreachableHandlers(MessageWithUnreachableHandlers message) {
        this.service.notifyAboutMessageWithUnreachableHandlers(message);
    }

    @Override
    public Set<MessageWithUnreachableHandlers> consumeMessagesWithUnreachableHandlers() {
        return this.service.consumeMessagesWithUnreachableHandlers();
    }

    @Override
    public void notifyAboutMessageWithResponseTimeout(TimedOutMessage message) {
        this.service.notifyAboutMessageWithResponseTimeout(message);
    }

    @Override
    public Set<TimedOutMessage> consumeMessagesWithResponseTimeout() {
        return this.service.consumeMessagesWithResponseTimeout();
    }

    @Override
    public void setupResponseTimeout(Message message) throws SchedulerException {
        this.service.setupResponseTimeout(message);
    }

    @Override
    public void registerUCSAlertingCallback(URL callback) {
        this.service.registerUCSAlertingCallback(callback);
    }

    @Override
    public Set<URL> getUCSAlertingCallbacks() {
        return this.service.getUCSAlertingCallbacks();
    }

    @Override
    public List<Adapter> getSupportedAdapters() {
        return this.service.getSupportedAdapters();
    }

    @Override
    public UCSStatus getServiceStatus() {
        return this.service.getServiceStatus();
    }

    @Override
    public void saveConversation(Conversation conversation) {
        this.service.saveConversation(conversation);
    }

    @Override
    public Optional<Conversation> getConversationById(String conversationId) {
        return this.service.getConversationById(conversationId);
    }

    @Override
    public List<Message> listMessagesByConversationId(String conversationId) {
        return this.service.listMessagesByConversationId(conversationId);
    }

    @Override
    public List<Message> listMessagesByConversationId(String conversationId, Optional<Long> from, Optional<Long> total) {
        return this.service.listMessagesByConversationId(conversationId, from, total);
    }

    @Override
    public List<Conversation> queryConversations(String query, List<QueryFilter> filters) {
        return this.service.queryConversations(query, filters);
    }

}
