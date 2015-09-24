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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.quartz.SchedulerException;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.QueryFilter;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.model.ResolvedAddresses;
import org.socraticgrid.hl7.ucs.nifi.common.model.UCSStatus;
import org.socraticgrid.hl7.ucs.nifi.processor.model.MessageWithUnreachableHandlers;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage;

/**
 *
 * @author esteban
 */
public interface UCSControllerService {
    public void start() throws Exception;
    public void stop() throws Exception;
    
    public ResolvedAddresses resolvePhysicalAddressesByServiceId(Message message);
    public UserContactInfo resolveUserContactInfo(String userId);
    
    /**
     * Message persistence related methods
     */
    public void saveMessage(Message message);
    public void updateMessage(Message message);
    public Optional<Message> getMessageById(String messageId);
    public List<Message> listMessages();
    public List<Message> listMessages(long from, long total);
    
    public void saveMessageReference(Message message, String recipientId, String reference);
    public Optional<Message> getMessageByReference(String reference);
    public Optional<String> getRecipientIdByReference(String reference);
    
    public boolean isKnownConversation(String conversationId); 
    
    public Set<Message> getRelatedMessages(String messageId);
    
    /**
     * UCS Client interface related methods
     */
    public void registerUCSClientCallback(URL callback);
    public Set<URL> getUCSClientCallbacks();
    
    /*
    * Escalation and re-routing 
    */
    public void notifyAboutMessageWithUnreachableHandlers(MessageWithUnreachableHandlers message);
    public Set<MessageWithUnreachableHandlers> consumeMessagesWithUnreachableHandlers();

    /**
     * UCS Message escalation related methods
     */
    public void notifyAboutMessageWithResponseTimeout(TimedOutMessage message);
    public Set<TimedOutMessage> consumeMessagesWithResponseTimeout();
    public void setupResponseTimeout(Message message) throws SchedulerException;
    
    /**
     * UCS Alert interface related methods
     */
    public void registerUCSAlertingCallback(URL callback);
    public Set<URL> getUCSAlertingCallbacks();
    
    /**
     * UCS Management Services related methods
     */
    public List<Adapter> getSupportedAdapters();
    public UCSStatus getServiceStatus();
    
    /**
     * Conversation API
     */
    public void saveConversation(Conversation conversation);
    public Optional<Conversation> getConversationById(String conversationId);
    public List<Message> listMessagesByConversationId(String conversationId);
    public List<Message> listMessagesByConversationId(String conversationId, Optional<Long> from, Optional<Long> total);
    public List<Conversation> queryConversations(String query, List<QueryFilter> filters);
}
