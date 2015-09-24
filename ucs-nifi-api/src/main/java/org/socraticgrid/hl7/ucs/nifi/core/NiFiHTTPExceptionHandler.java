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
import java.util.EnumMap;
import java.util.Map;
import org.socraticgrid.hl7.services.uc.exceptions.BadBodyException;
import org.socraticgrid.hl7.services.uc.exceptions.DeliveryException;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.exceptions.FeatureNotSupportedException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidAddressException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidConversationException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidInputException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.InvalidQueryException;
import org.socraticgrid.hl7.services.uc.exceptions.MessageDeliveryTimeoutException;
import org.socraticgrid.hl7.services.uc.exceptions.MissingBodyTypeException;
import org.socraticgrid.hl7.services.uc.exceptions.NotConnectedException;
import org.socraticgrid.hl7.services.uc.exceptions.ReadOnlyException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceAdapterFaultException;
import org.socraticgrid.hl7.services.uc.exceptions.ServiceOfflineException;
import org.socraticgrid.hl7.services.uc.exceptions.UCSException;
import org.socraticgrid.hl7.services.uc.exceptions.UndeliverableMessageException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownServiceException;
import org.socraticgrid.hl7.services.uc.exceptions.UnknownUserException;
import org.socraticgrid.hl7.services.uc.exceptions.UpdateException;

/**
 *
 * @author esteban
 */
public class NiFiHTTPExceptionHandler {
    
    public static enum Attribute {
        TYPE("Ucs.exception.type"),
        FAULT("Ucs.exception.fault"),
        SERVER_ID("Ucs.exception.serverId"),
        CONTEXT("Ucs.exception.context"),
        RECEIVER("Ucs.exception.receiverId");
        
        private final String attributeName;

        private Attribute(String attributeName) {
            this.attributeName = attributeName;
        }

        public String getAttributeName() {
            return attributeName;
        }
    }
    
    private final Map<Attribute, String> requestAttributes = new EnumMap<>(Attribute.class);
                
    public NiFiHTTPExceptionHandler(HttpExchange he){
        for (Attribute a : Attribute.values()) {
            String v = he.getRequestHeaders().getFirst(a.getAttributeName());
            if (v != null){
                this.requestAttributes.put(a, v);
            }
        }
    } 
    
    /**
     * Returns whether the HttpExchange this object is wrapping represents a 
     * UCSException or not.
     * @return 
     */
    public boolean isUCSException(){
        return this.requestAttributes.containsKey(Attribute.TYPE);
    }
    
    public UCSException createUCSException(){
        if (!this.isUCSException()){
            throw new IllegalStateException("The wrapped HttpExchange doesn't represent a UCSException!");
        }

        String type = this.requestAttributes.get(Attribute.TYPE);
        String fault = this.requestAttributes.get(Attribute.FAULT) == null ? "" : this.requestAttributes.get(Attribute.FAULT);
        String service = this.requestAttributes.get(Attribute.SERVER_ID) == null ? "" : this.requestAttributes.get(Attribute.SERVER_ID);
        String context = this.requestAttributes.get(Attribute.CONTEXT) == null ? "" : this.requestAttributes.get(Attribute.CONTEXT);
        
        ExceptionType exceptionType = ExceptionType.valueOf(type);
        
        switch(exceptionType){
            case BadBody:
                return new BadBodyException(fault, service, null, context, 0);
            case Delivery:
                return new DeliveryException(fault, service, null);
            case FeatureNotSupported:
                return new FeatureNotSupportedException(fault, service, null);
            case InvalidAddress:
                return new InvalidAddressException(fault, service, null);
            case InvalidConversation:
                return new InvalidConversationException(fault, service, null);
            case InvalidInput:
                return new InvalidInputException(fault, service, null);
            case InvalidMessage:
                return new InvalidMessageException(fault, service, null);
            case InvalidQuery:
                return new InvalidQueryException(fault, service, null);
            case MessageDeliveryFailure:
                return new DeliveryException(fault, service, null);
            case MessageDeliveryTimeout:
                return new MessageDeliveryTimeoutException(fault, service, null);
            case MissingBodyType:
                return new MissingBodyTypeException(fault, service, null);
            case NotConnected:
                return new NotConnectedException(fault, service, null);
            case ReadOnly:
                return new ReadOnlyException(fault, service, null);
            case ServerAdapterFault:
                return new ServiceAdapterFaultException(fault, service, null);
            case ServiceOffline:
                return new ServiceOfflineException(fault, service, null);
            case UndeliverableMessage:
                return new UndeliverableMessageException(fault, service, null);
            case UnknownService:
                return new UnknownServiceException(fault, service, null);
            case UnknownUser:
                return new UnknownUserException(fault, service, null);
            case UpdateError:
                return new UpdateException(fault, service, null);
            case General:
            case InvalidContext:
            case SystemFault:
                return new UCSException(fault, service, null);
            default:
                return new UCSException(fault, service, null);
        }
    } 
    
    public void throwUCSException() throws UCSException{
        if (this.isUCSException()){
            throw this.createUCSException();
        }
    }
}
