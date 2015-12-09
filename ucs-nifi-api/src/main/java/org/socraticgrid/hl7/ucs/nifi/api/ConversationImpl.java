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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static java.util.stream.Collectors.toList;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.BadBodyException;
import org.socraticgrid.hl7.services.uc.exceptions.DeliveryException;
import org.socraticgrid.hl7.services.uc.exceptions.FeatureNotSupportedException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidAddressException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidContentException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidConversationException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidQueryException;
import org.socraticgrid.hl7.services.uc.exceptions.MissingBodyTypeException;
import org.socraticgrid.hl7.services.uc.exceptions.NotConnectedException;
import org.socraticgrid.hl7.services.uc.exceptions.ReadOnlyException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.UndeliverableMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownServiceException;
import org.socraticgrid.hl7.services.uc.exceptions.UpdateException;
import org.socraticgrid.hl7.services.uc.interfaces.ConversationIntf;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.ConversationInfo;
import org.socraticgrid.hl7.services.uc.model.QueryFilter;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationInfoWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationInfoSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationSerializer;
import org.socraticgrid.hl7.ucs.nifi.core.NiFiHTTPBroker;

/**
 *
 * @author esteban
 */
public class ConversationImpl implements ConversationIntf {
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ConversationImpl.class);
    
    private final NiFiHTTPBroker niFiHTTPBroker;
    
    protected ConversationImpl(NiFiHTTPBroker niFiHTTPBroker) {
        this.niFiHTTPBroker = niFiHTTPBroker;
    }

    @Override
    public String createConversation(Conversation conversation) throws InvalidConversationException, InvalidContentException, MissingBodyTypeException, BadBodyException, InvalidAddressException, FeatureNotSupportedException, UnknownServiceException {
        try{ 
            
            List<String> parameters = new ArrayList<>();
            parameters.add(Base64.encodeBase64String(ConversationSerializer.serializeConversationWrapper(new ConversationWrapper(conversation)).getBytes()));
            
            String serializedConversationWrapper = niFiHTTPBroker.sendConversationCommand("createConversation", Optional.of(parameters), true);
            
            ConversationWrapper conversationWrapper = ConversationSerializer.deserializeConversationWrapper(serializedConversationWrapper);
            
            return conversationWrapper.getConversation().getConversationId();
            
        } catch (FeatureNotSupportedException | InvalidConversationException | MissingBodyTypeException | BadBodyException | InvalidAddressException | UnknownServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public Conversation connectConverstation(String conversationId) throws InvalidConversationException, InvalidAddressException, UnknownServiceException, FeatureNotSupportedException, ServiceAdapterFaultException, UndeliverableMessageException, ReadOnlyException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Conversation disconnectConverstation(String conversationId) throws FeatureNotSupportedException, InvalidConversationException, NotConnectedException, UnknownServiceException, ServiceAdapterFaultException, UndeliverableMessageException, DeliveryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Conversation> queryConversions(String query, List<QueryFilter> filters) throws FeatureNotSupportedException, InvalidQueryException {
        try{ 
            
            String serializedConversationWrapper = niFiHTTPBroker.sendConversationCommand("queryConversations", Optional.empty(), true);
            
            XMLListWrapper<ConversationWrapper> conversationWrapper = ConversationSerializer.deserializeConversationWrappers(serializedConversationWrapper);
            
            //unwrap the conversations
            return conversationWrapper.getItems().stream()
                    .map(cw -> cw.getConversation())
                    .collect(toList());
            
        } catch (InvalidQueryException | FeatureNotSupportedException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public ConversationInfo retrieveConversation(String conversationId) throws InvalidConversationException, FeatureNotSupportedException {
        try{ 
            
            List<String> parameters = new ArrayList<>();
            parameters.add(conversationId);
            
            String serializedConversationInfoWrapper = niFiHTTPBroker.sendConversationCommand("retrieveConversation", Optional.of(parameters), true);
            
            ConversationInfoWrapper conversationInfoWrapper = ConversationInfoSerializer.deserializeConversationInfoWrapper(serializedConversationInfoWrapper);
            
            return conversationInfoWrapper.getConversationInfo();
            
        } catch (InvalidConversationException | FeatureNotSupportedException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public boolean updateConversation(String conversationId, Conversation conversation) throws FeatureNotSupportedException, InvalidConversationException, InvalidAddressException, UnknownServiceException, ServiceAdapterFaultException, UpdateException, ReadOnlyException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean addMessagesToConversation(String conversationId, List<String> messageIds) throws InvalidConversationException, InvalidMessageException, UnknownServiceException, FeatureNotSupportedException, ReadOnlyException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean removeMessagesFromConversation(String conversationId, List<String> messageIds) throws InvalidConversationException, InvalidMessageException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
