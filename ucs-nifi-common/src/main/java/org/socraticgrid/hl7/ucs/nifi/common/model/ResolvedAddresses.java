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
package org.socraticgrid.hl7.ucs.nifi.common.model;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;

/**
 * This class is used to hold the resolved PhysicalAddreses of a message.
 *
 * @author esteban
 */
public class ResolvedAddresses {
    
    public static class ResolvedAddressesBuilder{
        
        private ResolvedAddresses ra = new ResolvedAddresses();
        
        public ResolvedAddressesBuilder addSenderAddress(PhysicalAddress address){
            ra.senderAddressesByServiceId.put(address.getServiceId(), address);
            return this;
        }
        
        public ResolvedAddressesBuilder addAllSenderAddresses(Set<PhysicalAddress> addresses){
            addresses.forEach(a -> addSenderAddress(a));
            return this;
        }
        
        public ResolvedAddressesBuilder addRecipientAddress(PhysicalAddress address){
            ra.recipientsAddressesByServiceId.computeIfAbsent(address.getServiceId(), k -> new LinkedHashSet<>()).add(address);
            return this;
        }
        
        public ResolvedAddressesBuilder addAllRecipientAddresses(Set<PhysicalAddress> addresses){
            addresses.forEach(a -> addRecipientAddress(a));
            return this;
        }
        
        public ResolvedAddresses build(){
            return this.ra;
        }
        
    }

    private final Map<String, PhysicalAddress> senderAddressesByServiceId = new LinkedHashMap<>();
    private final Map<String, Set<PhysicalAddress>> recipientsAddressesByServiceId = new LinkedHashMap<>();

    private ResolvedAddresses() {
    }
    
    public Map<String, PhysicalAddress> getSenderAddressesByServiceId() {
        return senderAddressesByServiceId;
    }

    public Map<String, Set<PhysicalAddress>> getRecipientsAddressesByServiceId() {
        return recipientsAddressesByServiceId;
    }
}