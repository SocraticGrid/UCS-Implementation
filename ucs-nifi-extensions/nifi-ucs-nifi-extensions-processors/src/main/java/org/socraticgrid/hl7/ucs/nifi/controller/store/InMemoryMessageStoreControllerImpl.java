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
package org.socraticgrid.hl7.ucs.nifi.controller.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.socraticgrid.hl7.services.uc.model.Conversation;

import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.QueryFilter;


public class InMemoryMessageStoreControllerImpl extends AbstractControllerService implements MessageStoreController {
    
    private final Map<String, MessageRecipientTuple> messageRecipientsByReference = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Message> messages = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, List<Message>> messagesByConversationId = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Conversation> conversations = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        return descriptors;
    }
    
    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws Exception{ 
        
    }
    
    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
    
    @Override
    public void saveMessage(Message message) {
        messages.put(message.getHeader().getMessageId(), message);
        
        if (message.getHeader().getRelatedConversationId() != null){
            String conversationId = message.getHeader().getRelatedConversationId();
            messagesByConversationId.computeIfAbsent(conversationId, k -> new ArrayList())
                    .add(message);
        }
    }
    
    @Override
    public void updateMessage(Message message) {
        //TODO: implement something like revision number?
        messages.put(message.getHeader().getMessageId(), message);
    }

    @Override
    public Optional<Message> getMessageById(String messageId) {
        return Optional.ofNullable(messages.get(messageId));
    }

    @Override
    public List<Message> listMessages() {
        return messages.values().stream()
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Message> listMessages(long from, long total) {
        return messages.values().stream()
                .skip(from)
                .limit(total)
                .collect(Collectors.toList());
    }

    @Override
    public void addMessageReference(String reference, MessageRecipientTuple messageRecipient) {
        messageRecipientsByReference.put(reference, messageRecipient);
    }

    @Override
    public Optional<MessageRecipientTuple> getMessageRecipientTupleByReferece(String reference) {
        return Optional.ofNullable(this.messageRecipientsByReference.get(reference));
    }
    
    @Override
    public boolean isKnownConversation(String conversationId){
        return messagesByConversationId.containsKey(conversationId);
    }

    @Override
    public Set<Message> getRelatedMessages(String messageId) {
        if (messageId == null){
            return Collections.EMPTY_SET;
        }
        
        return messages.values().stream()
                .filter(m -> messageId.equals(m.getHeader().getRelatedMessageId()))
                .collect(toSet());
    }

    @Override
    public void saveConversation(Conversation conversation) {
        if (this.conversations.containsKey(conversation.getConversationId())){
            throw new IllegalArgumentException("Duplicated Conversation id: '"+conversation.getConversationId()+"'");
        }
        
        this.conversations.put(conversation.getConversationId(), conversation);
    }

    @Override
    public Optional<Conversation> getConversationById(String conversationId) {
        return Optional.ofNullable(this.conversations.get(conversationId));
    }

    @Override
    public List<Message> listMessagesByConversationId(String conversationId) {
        return this.listMessagesByConversationId(conversationId, Optional.empty(), Optional.empty());
    }

    @Override
    public List<Message> listMessagesByConversationId(String conversationId, Optional<Long> from, Optional<Long> total) {
        List<Message> msgs = this.messagesByConversationId.get(conversationId);
        if (msgs == null){
            return Collections.EMPTY_LIST;
        }
        
        Stream<Message> stream = msgs.stream();
        
        if(from.isPresent()){
            stream.skip(from.get());
        }
        if(total.isPresent()){
            stream.limit(total.get());
        }
        
        return stream.collect(toList());
    }

    /**
     * This implementation doesn't make use of query nor filters parameters.
     * @param query
     * @param filters
     * @return 
     */
    @Override
    public List<Conversation> queryConversations(String query, List<QueryFilter> filters) {
        return this.conversations.values().stream().collect(toList());
    }
}
