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
package org.socraticgrid.hl7.ucs.nifi.processor.command;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;

/**
 *
 * @author esteban
 */
public class UCSUnregisterUCSClientCallbackTest extends UCSControllerServiceBasedTest{
    
    @Test
    public void testMissingRegistrationIdArg() throws MessageSerializationException {
        assertThat(controller.getUCSClientCallbacks(), empty());
        
        Map<String, String> attributes = this.createBasicAttributes();
        
        //This procesor doesn't use the content of the incoming FlowFile, so
        //we can send an emtpy one.
        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSUnregisterUCSClientCallback.REL_FAILURE);
        
        assertThat(controller.getUCSClientCallbacks(), empty());
    }
    
    @Test
    public void testHappyPath() throws MessageSerializationException, MalformedURLException {
        
        assertThat(controller.getUCSClientCallbacks(), empty());
        
        String registrationId = controller.registerUCSClientCallback(new URL("http://some.com/url"));
        assertThat(controller.getUCSClientCallbacks(), hasSize(1));
        
        Map<String, String> attributes = this.createBasicAttributes(registrationId);
        
        //This procesor doesn't use the content of the incoming FlowFile, so
        //we can send an emtpy one.
        testRunner.enqueue(new byte[]{}, attributes);
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSUnregisterUCSClientCallback.REL_SUCCESS);

        assertThat(controller.getUCSClientCallbacks(), empty());
        
    }
    
    private Map<String, String> createBasicAttributes(String registrationId){
        Map<String, String> result = this.createBasicAttributes();
        result.put("command.args", registrationId);
        
        return result;
    }
    
    private Map<String, String> createBasicAttributes(){
        Map<String, String> result = new HashMap<>();
        result.put("command.name", "unregisterUCSClientCallback");
        
        return result;
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSUnregisterUCSClientCallback());
    }
}
