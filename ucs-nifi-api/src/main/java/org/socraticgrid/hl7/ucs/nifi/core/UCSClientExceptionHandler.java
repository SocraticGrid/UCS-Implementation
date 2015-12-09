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
import org.socraticgrid.hl7.ucs.nifi.common.model.ExceptionWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ExceptionWrapperSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;

/**
 *
 * @author esteban
 */
public class UCSClientExceptionHandler extends UCSClientBaseHandler{

    private static final Logger LOG = LoggerFactory.getLogger(UCSClientExceptionHandler.class);

    public UCSClientExceptionHandler(UCSClientIntf listener) {
        super(listener);
    }
    
    @Override
    public void handle(HttpExchange he) throws IOException {
        try{
            String body = IOUtils.toString(he.getRequestBody());

            ExceptionWrapper ew = ExceptionWrapperSerializer.deserializeExceptionWrapper(body);
            
            LOG.error("Exception received from NiFi. Notyfing listener. Request body:\n{}.", body);
            this.getListener().handleException(new MessageModel(ew.getMessage()), ew.getSender(), ew.getReceiver(), ew.getProcessingException(), ew.getServerId());
        } catch (MessageSerializationException ex) {
            LOG.error("Listener notification failed.", ex);
        } finally{
            he.sendResponseHeaders(200, 0);
            he.close();
        }
    }
    
}
