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
package org.socraticgrid.hl7.ucs.nifi.client.example;

import com.google.gson.Gson;
import java.io.PrintStream;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.exceptions.BadBodyException;
import org.socraticgrid.hl7.services.uc.exceptions.FeatureNotSupportedException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidContentException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.MissingBodyTypeException;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.UndeliverableMessageException;
import org.socraticgrid.hl7.services.uc.interfaces.UCSClientIntf;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;

/**
 *
 * @author esteban
 */
public class UCSClientAdapter implements UCSClientIntf{

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UCSClientAdapter.class);
    
    private final PrintStream output;
    private final Gson gson;

    public UCSClientAdapter(PrintStream output) {
        this.output = output;
        this.gson = new Gson();
    }
    
    @Override
    public boolean callReady(Conversation c, String string, String string1) {
        return true;
    }

    @Override
    public <T extends Message> boolean handleException(MessageModel<T> mm, DeliveryAddress da, DeliveryAddress da1, ProcessingException pe, String string) {
        
        if (pe.getFault() != null && pe.getFault().contains("Reference not found!")){
            return true;
        }
        
        output.print("Exception Received:\n");
        output.print(gson.toJson(MessageUtils.toJsonObject(pe)));
        output.print("\n");
        output.flush();
        return true;
    }

    @Override
    public <T extends Message> boolean handleNotification(MessageModel<T> mm, String string) {
        return true;
    }

    @Override
    public <T extends Message> MessageModel<T> handleResponse(MessageModel<T> mm, String string) throws InvalidMessageException, InvalidContentException, MissingBodyTypeException, BadBodyException, ServiceAdapterFaultException, UndeliverableMessageException, FeatureNotSupportedException {
        output.print("New Response Message:\n");
        output.print(gson.toJson(MessageUtils.toJsonObject(mm.getMessageType())));
        output.print("\n");
        output.flush();
        return null;
    }

    @Override
    public <T extends Message> boolean receiveMessage(MessageModel<T> mm, String string) {
        output.print("New Message:\n");
        output.print(gson.toJson(MessageUtils.toJsonObject(mm.getMessageType())));
        output.print("\n");
        output.flush();
        return true;
    }
    
}
