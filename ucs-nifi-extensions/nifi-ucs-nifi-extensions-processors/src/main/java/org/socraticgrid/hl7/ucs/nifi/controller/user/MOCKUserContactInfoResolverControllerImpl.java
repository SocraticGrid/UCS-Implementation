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
package org.socraticgrid.hl7.ucs.nifi.controller.user;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
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
    
    public static final String MOCK_CONTACTS_FILE = "/mock-users-contact.txt";
    
    public static final String SERVICE_TYPE_SMS = "SMS";
    public static final String SERVICE_TYPE_EMAIL = "EMAIL";
    public static final String SERVICE_TYPE_CHAT = "CHAT";
    public static final String SERVICE_TYPE_TEXT_TO_VOICE = "TEXT-TO-VOICE";
    
    private final Map<String, UserContactInfo> data = new HashMap<>();

    public MOCKUserContactInfoResolverControllerImpl() {
        
        InputStream mockContactsFile = MOCKUserContactInfoResolverControllerImpl.class.getResourceAsStream(MOCK_CONTACTS_FILE);
        
        if (mockContactsFile == null){
            throw new IllegalStateException("Mock Contact file "+MOCK_CONTACTS_FILE+" couldn't be found");
        }
        
        try {
            List<String> lines = IOUtils.readLines(mockContactsFile);
            lines.stream().map(l -> l.trim()).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach((String l) -> {
                //The expected format is: name, email, telephone number, chat id, text to voice number.
                String[] parts = l.split(",");
                if (parts.length != 5){
                    throw new IllegalArgumentException("Line '"+l+"' from "+MOCK_CONTACTS_FILE+" doesn't contain 5 elements.");
                }
                
                data.put(parts[0].trim(), this.mockUserContactInfo(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()));
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Error reading information from "+MOCK_CONTACTS_FILE, ex);
        }
        
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
