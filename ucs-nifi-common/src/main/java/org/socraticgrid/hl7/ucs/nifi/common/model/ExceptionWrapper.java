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
package org.socraticgrid.hl7.ucs.nifi.common.model;

import javax.xml.bind.annotation.XmlRootElement;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;

/**
 *
 * @author esteban
 */
@XmlRootElement
public class ExceptionWrapper {
    private Message message; 
    private String serverId;
    private ProcessingException processingException; 
    private DeliveryAddress sender; 
    private DeliveryAddress receiver; 

    public ExceptionWrapper() {
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public ProcessingException getProcessingException() {
        return processingException;
    }

    public void setProcessingException(ProcessingException processingException) {
        this.processingException = processingException;
    }

    public DeliveryAddress getSender() {
        return sender;
    }

    public void setSender(DeliveryAddress sender) {
        this.sender = sender;
    }

    public DeliveryAddress getReceiver() {
        return receiver;
    }

    public void setReceiver(DeliveryAddress receiver) {
        this.receiver = receiver;
    }
            
}
