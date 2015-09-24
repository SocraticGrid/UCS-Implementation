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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.omg.CosNaming.NamingContextExtPackage.InvalidAddress;
import org.quartz.DateBuilder;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownUserException;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.QueryFilter;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.model.AdapterStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.ResolvedAddresses;
import org.socraticgrid.hl7.ucs.nifi.common.model.ResolvedAddresses.ResolvedAddressesBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;
import org.socraticgrid.hl7.ucs.nifi.common.model.UCSStatus;
import org.socraticgrid.hl7.ucs.nifi.controller.store.MessageRecipientTuple;
import org.socraticgrid.hl7.ucs.nifi.controller.store.MessageStoreController;
import org.socraticgrid.hl7.ucs.nifi.controller.user.UserContactInfoResolverController;
import org.socraticgrid.hl7.ucs.nifi.processor.model.MessageWithUnreachableHandlers;
import org.socraticgrid.hl7.ucs.nifi.services.JobScheduler;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage;
import org.socraticgrid.hl7.ucs.nifi.services.TimedoutAlertMessageConfigurationJob;
import org.socraticgrid.hl7.ucs.nifi.services.TimedoutMessageConfigurationJob;

/**
 *
 * @author esteban
 */
public class UCSControllerServiceImpl implements UCSControllerService {

    public static class UCSControllerServiceImplBuilder {

        private UserContactInfoResolverController userContactInfoResolver;
        private MessageStoreController messageStore;
        private ServiceStatusController serviceStatusController;

        public UCSControllerServiceImplBuilder setUserContactInfoResolver(UserContactInfoResolverController userContactInfoResolver) {
            this.userContactInfoResolver = userContactInfoResolver;
            return this;
        }

        public UCSControllerServiceImplBuilder setMessageStore(MessageStoreController messageStore) {
            this.messageStore = messageStore;
            return this;
        }

        public UCSControllerServiceImplBuilder setServiceStatusController(ServiceStatusController serviceStatusController) {
            this.serviceStatusController = serviceStatusController;
            return this;
        }

        public UCSControllerServiceImpl build() {
            if (userContactInfoResolver == null) {
                throw new IllegalStateException("userContactInfoResolver is not set");
            }
            if (messageStore == null) {
                throw new IllegalStateException("messageStore is not set");
            }
            if (serviceStatusController == null) {
                throw new IllegalStateException("serviceStatusController is not set");
            }

            UCSControllerServiceImpl instance = new UCSControllerServiceImpl();
            instance.userContactInfoResolver = this.userContactInfoResolver;
            instance.messageStore = this.messageStore;
            instance.serviceStatusController = this.serviceStatusController;

            return instance;
        }
    }

    private UserContactInfoResolverController userContactInfoResolver;
    private MessageStoreController messageStore;
    private ServiceStatusController serviceStatusController;

    //TODO: make this Map persistent
    private Map<String, TimedOutMessage> timeOutMessages = new ConcurrentHashMap<>();

    //TODO: make this List persistent.
    private Set<URL> ucsClientCallbacks = Collections.synchronizedSet(new HashSet<>());

    //TODO: make this List persistent.
    private Set<URL> ucsAlertingCallbacks = Collections.synchronizedSet(new HashSet<>());

    //TODO: make this List persistent.
    private Set<MessageWithUnreachableHandlers> messagesWithUnreachableHandlers = Collections.synchronizedSet(new HashSet<>());

    private UCSControllerServiceImpl() {
    }

    @Override
    public void start() throws Exception {
        this.messageStore.start();

        //start scheduler
        JobScheduler.getInMemoryInstance().startScheduler();
    }

    @Override
    public void stop() throws Exception {
        this.messageStore.stop();

        // stop JobSchedulers 
        JobScheduler.getInMemoryInstance().stopScheduler();
    }

    @Override
    public UserContactInfo resolveUserContactInfo(String userId) {
        return userContactInfoResolver.resolveUserContactInfo(userId);
    }

    @Override
    public ResolvedAddresses resolvePhysicalAddressesByServiceId(Message message) {
        ResolvedAddressesBuilder resultBuilder = new ResolvedAddressesBuilder();

        //resolve sender's addresses
        this.collectAddresses(message.getHeader().getSender())
                .stream().forEach((address) -> {
                    try {
                        resultBuilder.addAllSenderAddresses(this.resolveAddresses(address));
                    } catch (UnknownUserException ex) {
                        message.getExceptions().add(new ProcessingException(ExceptionType.UnknownUser, "UCSControllerService", message.toString(), ex.getMessage()));
                    } catch (InvalidAddress ex) {
                        message.getExceptions().add(new ProcessingException(ExceptionType.InvalidAddress, "UCSControllerService", message.toString(), ex.getMessage()));
                    }
                });

        //resolve recipients' addresses
        Set<Recipient> recipientsList = message.getHeader().getRecipientsList();
        recipientsList.stream().forEach((recipient) -> {
            this.collectAddresses(recipient.getDeliveryAddress())
                    .stream().forEach((address) -> {
                        try {
                            resultBuilder.addAllRecipientAddresses(this.resolveAddresses(address));
                        } catch (UnknownUserException ex) {
                            message.getExceptions().add(new ProcessingException(ExceptionType.UnknownUser, "UCSControllerService", message.toString(), ex.getMessage()));
                        } catch (InvalidAddress ex) {
                            message.getExceptions().add(new ProcessingException(ExceptionType.InvalidAddress, "UCSControllerService", message.toString(), ex.getMessage()));
                        }
                    });
        });

        return resultBuilder.build();
    }

    @Override
    public void saveMessage(Message message) {
        this.messageStore.saveMessage(message);
    }

    @Override
    public void updateMessage(Message message) {
        this.messageStore.updateMessage(message);
    }

    @Override
    public Optional<Message> getMessageById(String messageId) {
        return this.messageStore.getMessageById(messageId);
    }

    @Override
    public List<Message> listMessages() {
        return this.messageStore.listMessages();
    }

    @Override
    public Set<Message> getRelatedMessages(String messageId) {
        return this.messageStore.getRelatedMessages(messageId);
    }

    @Override
    public List<Message> listMessages(long from, long total) {
        return this.messageStore.listMessages(from, total);
    }

    @Override
    public void saveMessageReference(Message message, String recipientId, String reference) {
        this.messageStore.addMessageReference(reference, new MessageRecipientTuple(message.getHeader().getMessageId(), recipientId));
    }

    @Override
    public Optional<Message> getMessageByReference(String reference) {
        return this.messageStore.getMessageRecipientTupleByReferece(reference)
                .flatMap(mrt -> this.messageStore.getMessageById(mrt.getMessageId()));
    }

    @Override
    public Optional<String> getRecipientIdByReference(String reference) {
        return this.messageStore.getMessageRecipientTupleByReferece(reference)
                .map(MessageRecipientTuple::getRecipientId);
    }

    @Override
    public boolean isKnownConversation(String conversationId) {
        return this.messageStore.isKnownConversation(conversationId);
    }

    private Set<PhysicalAddress> collectAddresses(DeliveryAddress da) {
        Set<PhysicalAddress> collectedAddresses = new HashSet<>();
        switch (da.getAddressType()) {
            case Physical:
                collectedAddresses.add(da.getPhysicalAddress());
                break;
            case Group:
                throw new UnsupportedOperationException("Unsupported Address Type 'Group'");
            case Party:
                throw new UnsupportedOperationException("Unsupported Address Type 'Party'");
        }

        return collectedAddresses;
    }

    /**
     * Converts a PhysicalAddress.address into the real PhysicalAddress
     */
    private Set<PhysicalAddress> resolveAddresses(PhysicalAddress address) throws UnknownUserException, InvalidAddress {
        //TODO: do we have the necessary information in each PhysicalAddress? 
        //or do we need to resolve each of them?

        String serviceId = address.getServiceId();
        String userId = address.getAddress();

        UserContactInfo userInfo = this.userContactInfoResolver.resolveUserContactInfo(userId);
        if (userInfo == null) {
            throw new UnknownUserException("No UserContanctInfo for user " + userId);
        }

        //set all the known physical addresses for the user
        if (userInfo.getAddressesByType().get(serviceId) == null) {
            throw new InvalidAddress("No Address specified for user " + userId + " and service " + serviceId);
        }

        return userInfo.getAddressesByType().values().stream().collect(Collectors.toCollection(LinkedHashSet::new));

    }

    @Override
    public void registerUCSClientCallback(URL callback) {
        this.ucsClientCallbacks.add(callback);
    }

    @Override
    public Set<URL> getUCSClientCallbacks() {
        return Collections.unmodifiableSet(this.ucsClientCallbacks);
    }

    @Override
    public void notifyAboutMessageWithUnreachableHandlers(MessageWithUnreachableHandlers message) {
        this.messagesWithUnreachableHandlers.add(message);
    }

    @Override
    public synchronized Set<MessageWithUnreachableHandlers> consumeMessagesWithUnreachableHandlers() {
        try {
            return Collections.unmodifiableSet(
                    this.messagesWithUnreachableHandlers.stream().collect(Collectors.toSet())
            );
        } finally {
            this.messagesWithUnreachableHandlers.clear();
        }
    }

    @Override
    public void notifyAboutMessageWithResponseTimeout(TimedOutMessage message) {
        this.timeOutMessages.put(message.getMessage().getHeader().getMessageId(), message);
    }

    @Override
    public synchronized Set<TimedOutMessage> consumeMessagesWithResponseTimeout() {
        try {
            return this.timeOutMessages.values().stream().collect(Collectors.toSet());
        } finally {
            this.timeOutMessages.clear();
        }
    }

    @Override
    public void setupResponseTimeout(Message message) throws SchedulerException {
        //check these two values to track response
        if (message.getHeader().getRespondBy() > 0 && message.getHeader().isReceiptNotification()) {
            JobDetail jobDetail;
            if (message instanceof AlertMessage) {
                jobDetail = TimedoutAlertMessageConfigurationJob.createJobDetail(message.getHeader().getMessageId(), this);
            } else {
                jobDetail = TimedoutMessageConfigurationJob.createJobDetail(message, this);
            }
            JobScheduler.getInMemoryInstance().scheduleOneTimeJob(jobDetail, message.getHeader().getRespondBy(), DateBuilder.IntervalUnit.MINUTE);
        }

    }

    @Override
    public void registerUCSAlertingCallback(URL callback) {
        this.ucsAlertingCallbacks.add(callback);
    }

    @Override
    public Set<URL> getUCSAlertingCallbacks() {
        return Collections.unmodifiableSet(this.ucsAlertingCallbacks);
    }

    @Override
    public List<Adapter> getSupportedAdapters() {
        List<Adapter> supportedAdapters = new ArrayList<>();
        supportedAdapters.add(new Adapter(1l, "SMS"));
        supportedAdapters.add(new Adapter(2l, "CHAT"));
        supportedAdapters.add(new Adapter(3l, "EMAIL"));
        supportedAdapters.add(new Adapter(4l, "ALERT"));
        supportedAdapters.add(new Adapter(5l, "VOIP"));
        return supportedAdapters;
    }

    @Override
    public UCSStatus getServiceStatus() {
        UCSStatus ucsStatus = new UCSStatus();
        Map<String, AdapterStatus> serviceStatusMap = serviceStatusController
                .getServiceStatusMap();
        List<AdapterStatus> adapterStatusList = new ArrayList<>();

        //By default, Adapters are AVAILABLE
        List<Adapter> supportedAdapters = getSupportedAdapters();
        supportedAdapters.stream()
                .filter(a -> serviceStatusMap.containsKey(a.getAdapterName()))
                .map(a -> serviceStatusMap.get(a.getAdapterName()))
                .forEach(adapterStatusList::add);
        supportedAdapters.stream()
                .filter(a -> !serviceStatusMap.containsKey(a.getAdapterName()))
                .map(a -> new AdapterStatus(a.getAdapterName(), Status.AVAILABLE, new Date()))
                .forEach(adapterStatusList::add);

        ucsStatus.setAdapterStatusList(adapterStatusList);

        // Evaluating overall service status
        long unaAvailableCount = adapterStatusList.stream()
                .filter(a -> a.getStatus() == Status.UNAVAILABLE)
                .count();
        ucsStatus.setStatus(Status.AVAILABLE);
        if (unaAvailableCount == supportedAdapters.size()) {
            ucsStatus.setStatus(Status.UNAVAILABLE);
        }
        ucsStatus.setLastUpdateDateTime(new Date());

        return ucsStatus;
    }

    @Override
    public void saveConversation(Conversation conversation) {
        this.messageStore.saveConversation(conversation);
    }

    @Override
    public Optional<Conversation> getConversationById(String conversationId) {
        return this.messageStore.getConversationById(conversationId);
    }

    @Override
    public List<Message> listMessagesByConversationId(String conversationId) {
        return this.messageStore.listMessagesByConversationId(conversationId);
    }

    @Override
    public List<Message> listMessagesByConversationId(String conversationId, Optional<Long> from, Optional<Long> total) {
        return this.messageStore.listMessagesByConversationId(conversationId, from, total);
    }

    @Override
    public List<Conversation> queryConversations(String query, List<QueryFilter> filters) {
        return this.messageStore.queryConversations(query, filters);
    }
}
