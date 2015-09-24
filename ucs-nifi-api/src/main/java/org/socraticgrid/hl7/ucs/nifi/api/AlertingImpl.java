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
package org.socraticgrid.hl7.ucs.nifi.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidContentException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.ReadOnlyException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownServiceException;
import org.socraticgrid.hl7.services.uc.exceptions.UpdateException;
import org.socraticgrid.hl7.services.uc.interfaces.AlertingIntf;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.ucs.nifi.core.NiFiHTTPBroker;

/**
 *
 * @author esteban
 */
public class AlertingImpl implements AlertingIntf {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AlertingImpl.class);
    
    private final NiFiHTTPBroker niFiHTTPBroker;
    
    protected AlertingImpl(NiFiHTTPBroker niFiHTTPBroker) {
        this.niFiHTTPBroker = niFiHTTPBroker;
    }
    
    @Override
    public boolean updateAlert(AlertMessage alert) throws InvalidMessageException, InvalidContentException, UnknownServiceException, ServiceAdapterFaultException, UpdateException, ReadOnlyException {
        try {
            
            List<String> args = new ArrayList<>();
            args.add(alert.getHeader().getMessageId());
            args.add(alert.getHeader().getAlertStatus().name());
            
            niFiHTTPBroker.sendAlertingCommand("updateAlertMessage", Optional.of(args), true);
            
            return true;
        } catch (InvalidMessageException | ServiceAdapterFaultException | UpdateException | ReadOnlyException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Unexcpected exception while sending command to Nifi.", ex);
            throw new IllegalStateException("Unexcpected exception while sending command to Nifi. Check the logs for more details.", ex);
        }
    }
    
}
