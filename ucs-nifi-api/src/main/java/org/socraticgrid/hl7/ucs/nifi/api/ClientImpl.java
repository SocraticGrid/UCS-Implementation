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
package org.socraticgrid.hl7.ucs.nifi.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.BadBodyException;
import org.socraticgrid.hl7.services.uc.exceptions.DeliveryException;
import org.socraticgrid.hl7.services.uc.exceptions.FeatureNotSupportedException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidAddressException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidContentException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidQueryException;
import org.socraticgrid.hl7.services.uc.exceptions.MessageDeliveryTimeoutException;
import org.socraticgrid.hl7.services.uc.exceptions.MissingBodyTypeException;
import org.socraticgrid.hl7.services.uc.exceptions.ReadOnlyException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceOfflineException;
import org.socraticgrid.hl7.services.uc.exceptions.UndeliverableMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownServiceException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownUserException;
import org.socraticgrid.hl7.services.uc.exceptions.UpdateException;
import org.socraticgrid.hl7.services.uc.interfaces.ClientIntf;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.CommunicationsPreferences;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.services.uc.model.MessageSummary;
import org.socraticgrid.hl7.services.uc.model.QueryScope;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.core.NiFiHTTPBroker;

/**
 *
 * @author esteban
 */
public class ClientImpl implements ClientIntf {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ClientImpl.class);
    
    private final NiFiHTTPBroker niFiHTTPBroker;
    
    protected ClientImpl(NiFiHTTPBroker niFiHTTPBroker) {
        this.niFiHTTPBroker = niFiHTTPBroker;
    }
    
    @Override
    public <T extends Message> String sendMessage(MessageModel<T> messageModel) throws InvalidMessageException, InvalidContentException, MissingBodyTypeException, BadBodyException, InvalidAddressException, UnknownServiceException, DeliveryException, MessageDeliveryTimeoutException, ServiceAdapterFaultException, UndeliverableMessageException, FeatureNotSupportedException, ServiceOfflineException, UpdateException, ReadOnlyException {
        try {
            T message = messageModel.getMessageType();
            
            String messageId = message.getHeader().getMessageId();
            if (messageId == null){
                messageId = UUID.randomUUID().toString();
                message.getHeader().setMessageId(messageId);
            }
            niFiHTTPBroker.sendMessage(message);
            
            return messageId;
        } catch (MessageSerializationException ex) {
            LOG.error("Message coulnd't be serialized", ex);
            throw new InvalidMessageException("Message coulnd't be serialized. Check the logs for more details.");
        } catch (IOException ex) {
            LOG.error("NiFi response was not OK", ex);
            throw new UndeliverableMessageException("NiFi response was not OK. Check the logs for more details.");
        }
    }
    
    /**
     * {@link #queryMessage(java.lang.String) } sometimes is not enough because 
     * {@link MessageSummary} doesn't contain all the information of a messages. 
     * For example, it doesn't contain the status of an {@link AlertMessage}.
     * For the cases where the entire message is required, consider using this
     * method.
     * @return
     * @throws InvalidQueryException 
     */
    public List<Message> listMessages() throws InvalidQueryException {
        try {
            String result = niFiHTTPBroker.sendClientCommand("getMessages", Optional.empty(), true);
            
            XMLListWrapper<MessageWrapper> messageWrapper = MessageSerializer.deserializeMessageWrappers(result);
            
            return messageWrapper.getItems().stream()
                    .map(mw -> mw.getMessage())
                    .collect(Collectors.toList());
            
        } catch (Exception ex) {
            LOG.error("Exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }
    
    @Override
    public List<MessageSummary> queryMessage(String query) throws InvalidQueryException {
        try {
            String result = niFiHTTPBroker.sendClientCommand("getMessages", Optional.empty(), true);
            
            XMLListWrapper<MessageWrapper> messageWrapper = MessageSerializer.deserializeMessageWrappers(result);
            
            //TODO: this is only a hack to set some kind of Subject to messages
            //without it. This MUST be removed in the future!
            messageWrapper.getItems().parallelStream()
                    .filter(mw -> mw.getMessage().getHeader().getSubject() == null)
                    .filter(mw -> mw.getMessage().getParts() != null && mw.getMessage().getParts().length > 0)
                    .forEach(mw -> mw.getMessage().getHeader().setSubject(mw.getMessage().getParts()[0].getContent()));
                    
            return messageWrapper.getItems().stream()
                    .map(mw -> mw.getMessage())
                    .map(m -> new MessageSummary(m.getHeader()))
                    .collect(Collectors.toList());
            
        } catch (Exception ex) {
            LOG.error("Exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public boolean assertPresence(String userId, String context, String status) throws FeatureNotSupportedException, UnknownUserException {
        throw new FeatureNotSupportedException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean cancelMessage(String messageId, boolean requireRetratcion) throws InvalidMessageException, FeatureNotSupportedException, ServiceOfflineException, ReadOnlyException {
        try{ 
            List<String> args = new ArrayList<>();
            args.add(messageId);
            niFiHTTPBroker.sendClientCommand("cancelMessage", Optional.of(args), true);
            return true;
        } catch (InvalidMessageException | FeatureNotSupportedException | ServiceOfflineException | ReadOnlyException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public <T extends Message> MessageModel<T> createMessage(MessageModel<T> BaseMessage) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<String> queryUsers(String query) throws InvalidQueryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T extends Message> MessageModel<T> retrieveMessage(String messageId) throws InvalidMessageException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public UserContactInfo retrieveUser(String userId) throws UnknownUserException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void sendMessageById(String MessageId) throws InvalidMessageException, InvalidContentException, MissingBodyTypeException, BadBodyException, InvalidAddressException, UnknownServiceException, DeliveryException, MessageDeliveryTimeoutException, ServiceAdapterFaultException, UndeliverableMessageException, FeatureNotSupportedException, ServiceOfflineException, UpdateException, ReadOnlyException {
        throw new FeatureNotSupportedException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean updateCommunicationsPreferences(String userId, CommunicationsPreferences prefs) throws UnknownUserException, UpdateException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T extends Message> boolean updateMessage(String messageId, MessageModel<T> newMessageModel, boolean checkUpdate) throws InvalidMessageException, InvalidContentException, MissingBodyTypeException, BadBodyException, InvalidAddressException, UnknownServiceException, FeatureNotSupportedException, UpdateException, ReadOnlyException {
        throw new FeatureNotSupportedException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<String> findSupportedContent(List<Recipient> recipients, QueryScope scope) throws InvalidAddressException, UnknownServiceException, FeatureNotSupportedException {
        throw new FeatureNotSupportedException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
