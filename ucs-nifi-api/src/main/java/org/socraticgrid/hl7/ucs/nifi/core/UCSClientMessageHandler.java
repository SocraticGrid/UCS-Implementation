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
package org.socraticgrid.hl7.ucs.nifi.core;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.interfaces.UCSClientIntf;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class UCSClientMessageHandler extends UCSClientBaseHandler{

    private static final Logger LOG = LoggerFactory.getLogger(UCSClientMessageHandler.class);

    public UCSClientMessageHandler(UCSClientIntf listener) {
        super(listener);
    }
    
    @Override
    public void handle(HttpExchange he) throws IOException {
        try{
            String body = IOUtils.toString(he.getRequestBody());

            //TODO: how do we know the serviceId
            String serviceId = "<unknown>";

            LOG.debug("Request recevied from nifi:\n {}", body);
            MessageWrapper messageWrapper;
            try {
                messageWrapper = MessageSerializer.deserializeMessageWrapper(body);
            } catch (MessageSerializationException ex) {
                LOG.error("Exception deserializing Message.", ex);
                return;
            }

            try {
                this.getListener().receiveMessage(new MessageModel(messageWrapper.getMessage()), serviceId);
            } catch (Exception ex) {
                LOG.error("Listener notification failed.", ex);
                //if the listener fails, there;s nothing we could do.
            }
        } finally {
            he.sendResponseHeaders(200, 0);
            he.close();
        }
        
    }
    
}
