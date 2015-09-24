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
package org.socraticgrid.hl7.ucs.nifi.controller.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.nifi.annotation.lifecycle.OnEnabled;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;

/**
 * This is just a MOCK implementation of UserContactInfoResolverController.
 * This class must be replaced with a real resolver.
 * @author esteban
 */
public class MOCKUserContactInfoResolverControllerImpl extends AbstractControllerService implements UserContactInfoResolverController {
    
    public static final String SERVICE_TYPE_SMS = "SMS";
    public static final String SERVICE_TYPE_EMAIL = "EMAIL";
    public static final String SERVICE_TYPE_CHAT = "CHAT";
    public static final String SERVICE_TYPE_TEXT_TO_VOICE = "TEXT-TO-VOICE";
    
    private final Map<String, UserContactInfo> data = new HashMap<>();

    public MOCKUserContactInfoResolverControllerImpl() {
        //create some MOCK UserContactInfo
        data.put("eafry", this.mockUserContactInfo("eafry", "eafry@cognitivemedicine.com","18583957317", "eafry@socraticgrid.org", "18583957317"));
        data.put("jhughes", this.mockUserContactInfo("jhughes", "jhughes@cognitivemedicine.com","000000000", "jhughes@socraticgrid.org","000000000"));
        data.put("ealiverti", this.mockUserContactInfo("ealiverti", "ealiverti@cognitivemedicine.com","491623342171", "ealiverti@socraticgrid.org","+4981614923621"));
    }
    
    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        return descriptors;
    }
    
    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws Exception{ 
        
    }
  
    @Override
    public UserContactInfo resolveUserContactInfo(String userId) {
        return data.get(userId);
    }
    
    private UserContactInfo mockUserContactInfo(String name, String email, String phoneNumber, String chatHandle, String textToVoiceNumber){
        Map<String, PhysicalAddress> addressesByType = new HashMap<>();
        
        addressesByType.put(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_EMAIL, new PhysicalAddress(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_EMAIL, email));
        addressesByType.put(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_SMS, new PhysicalAddress(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_SMS, phoneNumber));
        addressesByType.put(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_CHAT, new PhysicalAddress(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_CHAT, chatHandle));
        addressesByType.put(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_TEXT_TO_VOICE, new PhysicalAddress(MOCKUserContactInfoResolverControllerImpl.SERVICE_TYPE_TEXT_TO_VOICE, textToVoiceNumber));
        
        UserContactInfo uci = new UserContactInfo();
        uci.setName(name);
        uci.setAddressesByType(addressesByType);
        
        uci.setPreferredAddress(addressesByType.values().iterator().next());
        
        return uci;
    }

}
