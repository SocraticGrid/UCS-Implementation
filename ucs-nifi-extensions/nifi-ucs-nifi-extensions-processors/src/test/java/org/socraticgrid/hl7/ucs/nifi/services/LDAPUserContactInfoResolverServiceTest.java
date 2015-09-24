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
package org.socraticgrid.hl7.ucs.nifi.services;

import org.socraticgrid.hl7.ucs.nifi.controller.user.UserContactInfoResolverController;
import org.socraticgrid.hl7.ucs.nifi.controller.user.LDAPUserContactInfoResolverControllerImpl;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.spy;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;

/**
 *
 * @author pavan
 */
public class LDAPUserContactInfoResolverServiceTest { 
	private UserContactInfoResolverController  contactInfoResolverService;
	
	@Before
    public void init() throws Exception {
		//contactInfoResolverService = new LDAPUserContactInfoResolverControllerImpl();
		contactInfoResolverService = spy(new LDAPUserContactInfoResolverControllerImpl());	
		
		UserContactInfo contactInfo = new UserContactInfo();
		contactInfo.setName("testuser");
		Map<String, PhysicalAddress> physicalAddress = new HashMap<String, PhysicalAddress>();
		physicalAddress.put(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_CHAT, new PhysicalAddress(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_CHAT, "testuser@test.com"));
		physicalAddress.put(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_SMS, new PhysicalAddress(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_SMS, "725844448479"));
		physicalAddress.put(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_EMAIL, new PhysicalAddress(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_EMAIL, "testemail@test.com"));
		physicalAddress.put(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_TEXT_TO_VOICE, new PhysicalAddress(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_TEXT_TO_VOICE, "+725844448479"));
		
		contactInfo.setAddressesByType(physicalAddress);
		
		//these are the messages we want to return
        Mockito.doAnswer(new Answer() {
            private int execNumber = 0;
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                try{
                if (execNumber == 0){
                    return contactInfo;
                } else {
                    return new UserContactInfo();
                }
                } finally {
                    execNumber++;
                }
            }
        }).when(contactInfoResolverService).resolveUserContactInfo(anyObject());
	}
	
    @Test
    public void testValidUserRetrieveWithMockData()throws Exception{ 
    	UserContactInfo contactInfo = contactInfoResolverService.resolveUserContactInfo("testuser");
    	Assert.assertNotNull(contactInfo);
        Assert.assertEquals("testuser", contactInfo.getName());
        Assert.assertNotNull(contactInfo.getAddressesByType());
        Map<String, PhysicalAddress> physicalAddress = contactInfo.getAddressesByType();        
        Assert.assertTrue(physicalAddress.size()>0);
        
        PhysicalAddress address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_CHAT);
        Assert.assertEquals("testuser@test.com",address.getAddress()); 
        
        address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_SMS);
        Assert.assertEquals("725844448479",address.getAddress()); 
        
        address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_EMAIL);
        Assert.assertEquals("testemail@test.com",address.getAddress()); 
        
        address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_TEXT_TO_VOICE);
        Assert.assertEquals("+725844448479",address.getAddress()); 
    }
    
     @Ignore
     @Test
    //this is actual test method which connects to real ldap server
    public void testValidUserRetrieve()throws Exception{ 
    	UserContactInfo contactInfo = contactInfoResolverService.resolveUserContactInfo("ealiverti");
    	Assert.assertNotNull(contactInfo);
        Assert.assertEquals("ealiverti", contactInfo.getName());
        Assert.assertNotNull(contactInfo.getAddressesByType());
        Map<String, PhysicalAddress> physicalAddress = contactInfo.getAddressesByType();        
        Assert.assertTrue(physicalAddress.size()>0);
        
        PhysicalAddress address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_EMAIL);
        Assert.assertEquals("ealiverti@cognitivemedicine.com",address.getAddress()); 
        
        address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_SMS);
        Assert.assertEquals("491623342171",address.getAddress()); 
        
        address = physicalAddress.get(LDAPUserContactInfoResolverControllerImpl.SERVICE_TYPE_TEXT_TO_VOICE);
        Assert.assertEquals("+4981614923621",address.getAddress()); 
    }  
}
