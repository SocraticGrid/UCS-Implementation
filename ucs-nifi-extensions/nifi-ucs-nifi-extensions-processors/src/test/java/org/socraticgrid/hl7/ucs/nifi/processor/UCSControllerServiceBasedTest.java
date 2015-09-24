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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.util.HashMap;
import java.util.Map;

import org.apache.nifi.util.TestRunner;
import org.junit.Before;
import org.socraticgrid.hl7.ucs.nifi.controller.ServiceStatusControllerService;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSControllerServiceProxy;
import org.socraticgrid.hl7.ucs.nifi.controller.store.InMemoryMessageStoreControllerImpl;
import org.socraticgrid.hl7.ucs.nifi.controller.user.MOCKUserContactInfoResolverControllerImpl;

/**
 *
 * @author esteban
 */
public abstract class UCSControllerServiceBasedTest {
    
    protected TestRunner testRunner;
    
    protected InMemoryMessageStoreControllerImpl messageStoreController;
    protected MOCKUserContactInfoResolverControllerImpl userContactInfoResolverController;
    protected ServiceStatusControllerService serviceStatusControllerService;
    protected UCSControllerServiceProxy controller;
    
    @Before
    public void init() throws Exception {
        
        messageStoreController = new InMemoryMessageStoreControllerImpl();
        userContactInfoResolverController = new MOCKUserContactInfoResolverControllerImpl();
        serviceStatusControllerService = new ServiceStatusControllerService();
        controller = this.createUCSControllerService();
        
        this.testRunner = this.createTestRunner();
        
        testRunner.addControllerService("ucs-message-store", messageStoreController);
        testRunner.enableControllerService(messageStoreController);
        
        testRunner.addControllerService("ucs-user-contact-info-resolver", userContactInfoResolverController);
        testRunner.enableControllerService(userContactInfoResolverController);
        
        testRunner.addControllerService("service-status-controller",serviceStatusControllerService);
		testRunner.enableControllerService(serviceStatusControllerService);
        
        Map<String, String> ucsControllerServiceProxyConfig = new HashMap<>();
        ucsControllerServiceProxyConfig.put(UCSControllerServiceProxy.MESSAGE_STORE_IMPL.getName(), "ucs-message-store");
        ucsControllerServiceProxyConfig.put(UCSControllerServiceProxy.USER_CONTACT_INFO_RESOLVER_IMPL.getName(), "ucs-user-contact-info-resolver");
        ucsControllerServiceProxyConfig.put(UCSControllerServiceProxy.SERVICE_STATUS_CONTROLLER_SERVICE.getName(), "service-status-controller");
        
        testRunner.addControllerService("ucs-controller", controller, ucsControllerServiceProxyConfig);
        testRunner.enableControllerService(controller);
        
        testRunner.setProperty(UCSGetUCSClientCallbacks.UCS_CONTROLLER_SERVICE, "ucs-controller");
        
        this.afterInit();
    }
    
    protected void afterInit() throws Exception {
    }
    
    protected UCSControllerServiceProxy createUCSControllerService() {
        return new UCSControllerServiceProxy();
    }
    
    protected abstract TestRunner createTestRunner();
}
