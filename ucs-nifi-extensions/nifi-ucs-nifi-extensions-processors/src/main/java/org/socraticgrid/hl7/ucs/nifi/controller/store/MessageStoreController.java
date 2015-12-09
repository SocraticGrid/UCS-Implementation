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
package org.socraticgrid.hl7.ucs.nifi.controller.store;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.socraticgrid.hl7.services.uc.model.Conversation;

import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.QueryFilter;

/**
 * Interface to a Persistent Message Store.
 * @author esteban
 */
@Tags({"UCS"})
@CapabilityDescription("")
public interface MessageStoreController extends ControllerService {
    public void start();
    public void stop();
    
    //Conversation API
    /**
     * Persists a new Conversation in the store. If a conversation with the
     * same id is already present in the store, this method will throw an
     * {@link IllegalArgumentException}.
     * @param conversation 
     */
    public void saveConversation(Conversation conversation);
    
    /**
     * Returns a conversation given its id or an empty {@link Optional}
     * @param conversationId
     * @return 
     */
    public Optional<Conversation> getConversationById(String conversationId);
    
    /**
     * Lists all the Messages for a given conversation.
     * @param conversationId
     * @return 
     */
    public List<Message> listMessagesByConversationId(String conversationId);
    
    /**
     * Lists the Messages for a given conversation.
     * @param conversationId
     * @param from
     * @param total
     * @return 
     */
    public List<Message> listMessagesByConversationId(String conversationId, Optional<Long> from, Optional<Long> total);
    
    /**
     * Queries the Conversation store and retrieves all the Conversations matching
     * the provided query and filters.
     * @param query
     * @param filters
     * @return 
     */
    public List<Conversation> queryConversations(String query, List<QueryFilter> filters);
    
    // End of Conversation API
    
    /**
     * Persists a message in the store.
     * @param message 
     */
    public void saveMessage(Message message);
    
    /**
     * Updates a message in the store.
     * @param message 
     */
    public void updateMessage(Message message);
    
    /**
     * Returns a Message given its id.
     * @param messageId
     * @return 
     */
    public Optional<Message> getMessageById(String messageId);
    
    /**
     * Returns all Messages in the store.
     * @param messageId
     * @return 
     */
    public List<Message> listMessages();
    
    /**
     * Same as {@link #listMessages()} but allowing pagination.
     * @param from
     * @param total
     * @return 
     */
    public List<Message> listMessages(long from, long total);
    
    /**
     * Associates a reference tag to a {@link MessageRecipientTuple}.
     * @param reference
     * @param messageRecipient 
     */
    public void addMessageReference(String reference, MessageRecipientTuple messageRecipient);
    
    /**
     * Returns the {@link MessageRecipientTuple} associated to a reference tag.
     * @param reference
     * @return 
     */
    public Optional<MessageRecipientTuple> getMessageRecipientTupleByReferece(String reference);
    
    /**
     * Indicates whether a specific conversation is known by this store. A 
     * conversation is known when the store has at least 1 active message 
     * for it. 
     * @param conversationId
     * @return 
     */
    public boolean isKnownConversation(String conversationId);
    
    /**
     * Returns the {@link Message} associated to a messageId.
     * @param messageId
     * @return 
     */
    public Set<Message> getRelatedMessages(String messageId);
    
}
