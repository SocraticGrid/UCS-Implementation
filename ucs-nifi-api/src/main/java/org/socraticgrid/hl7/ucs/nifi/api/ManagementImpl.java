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
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.FeatureNotSupportedException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidContentException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidConversationException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidQueryException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownServiceException;
import org.socraticgrid.hl7.services.uc.interfaces.ManagementIntf;
import org.socraticgrid.hl7.services.uc.logging.LogEntry;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.EventMetadata;
import org.socraticgrid.hl7.services.uc.model.EventType;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.services.uc.model.ServiceInfo;
import org.socraticgrid.hl7.services.uc.model.Status;
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.model.UCSStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.AdapterSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.UCSStatusSerializer;
import org.socraticgrid.hl7.ucs.nifi.core.NiFiHTTPBroker;

/**
 *
 * @author esteban
 */
public class ManagementImpl implements ManagementIntf {
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ClientImpl.class);
    
    private final NiFiHTTPBroker niFiHTTPBroker;
    
    protected ManagementImpl(NiFiHTTPBroker niFiHTTPBroker) {
        this.niFiHTTPBroker = niFiHTTPBroker;
    }

    @Override
    public List<ServiceInfo> discoverChannels() throws FeatureNotSupportedException {
        try{ 
            String result =  niFiHTTPBroker.sendManagementCommand("discoverChannels", Optional.empty(), true);
            
            XMLListWrapper<Adapter> deserializedAdapters = AdapterSerializer.deserializeAdapters(result);

            List<ServiceInfo> results = new ArrayList<>();
            
            deserializedAdapters.getItems().stream()
                    .map(a -> {ServiceInfo si = new ServiceInfo(); si.setServiceName(a.getAdapterName()); return si;})
                    .forEach(results::add);
            
            return results;
        } catch (FeatureNotSupportedException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public List<EventMetadata> getEventMetaData(EventType eventType, List<String> eventIds) throws InvalidMessageException, InvalidConversationException, FeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<EventMetadata> queryEventMetaData(String query) throws InvalidQueryException, FeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Status> getStatus(String capablityType, List<String> capabilityIds) throws InvalidMessageException, InvalidContentException, InvalidConversationException, UnknownServiceException, ServiceAdapterFaultException, FeatureNotSupportedException {
        try{ 
            String result =  niFiHTTPBroker.sendManagementCommand("getStatus", Optional.empty(), true);
            
            UCSStatus deserializedStatus = UCSStatusSerializer.deserializeUCSStatus(result);

            List<Status> results = new ArrayList<>();
            
            deserializedStatus.getAdapterStatusList().stream()
                    .map(as -> {
                        Status s = new Status(); 
                        s.setAvailable(as.getStatus() == org.socraticgrid.hl7.ucs.nifi.common.model.Status.AVAILABLE); 
                        s.setSupported(true);
                        s.setCapability(as.getAdapterName());
                        return s;})
                    .forEach(results::add);
            
            return results;
        } catch (FeatureNotSupportedException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }

    @Override
    public List<Conversation> getConversations(List<String> conversationIds) throws InvalidContentException, FeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T extends Message> List<MessageModel<T>> getMessages(List<String> messageIds) throws InvalidMessageException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<LogEntry> getEvents(List<String> messageIds) throws InvalidMessageException, FeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean resumeChannel(List<String> channelIds) throws UnknownServiceException, ServiceAdapterFaultException, FeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean suspendChannel(List<String> channelIds) throws UnknownServiceException, ServiceAdapterFaultException, FeatureNotSupportedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
