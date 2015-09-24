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
package org.socraticgrid.hl7.ucs.nifi.core;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.interfaces.UCSAlertingIntf;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class UCSAlertingMessageUpdatedHandler extends UCSAlertingBaseHandler{

    private static final Logger LOG = LoggerFactory.getLogger(UCSAlertingMessageUpdatedHandler.class);

    public UCSAlertingMessageUpdatedHandler(UCSAlertingIntf listener) {
        super(listener);
    }
    
    @Override
    public void handle(HttpExchange he) throws IOException {
        try{
            String body = IOUtils.toString(he.getRequestBody());

            XMLListWrapper<MessageWrapper> messages;
            try {
                messages = MessageSerializer.deserializeMessageWrappers(body);
            } catch (MessageSerializationException ex) {
                LOG.error("Exception deserializing Message.", ex);
                return;
            }
            
            MessageModel oldModel = new MessageModel(messages.getItems().get(0).getMessage());
            MessageModel newModel = new MessageModel(messages.getItems().get(1).getMessage());
            List<String> localReceivers = null;
            //TODO: how do we know the serviceId
            String serverId = "<unknown>";
            
            this.getListener().updateAlertMessage(newModel, oldModel, localReceivers, serverId);
            
        } finally {
            he.sendResponseHeaders(200, 0);
            he.close();
        }
        
    }
    
}
