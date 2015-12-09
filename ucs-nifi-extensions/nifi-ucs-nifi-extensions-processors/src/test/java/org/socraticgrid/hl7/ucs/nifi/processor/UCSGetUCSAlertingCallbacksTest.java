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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 *
 * @author pavan
 */
public class UCSGetUCSAlertingCallbacksTest extends UCSControllerServiceBasedTest{
    
    @Test
    public void testNoRegisteredCallbacks(){
        
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.CALLBACK_ATTRIBUTE_NAME, "callback.url");
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.REMOVE_PROTOCOL_FROM_URL, "false");
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSGetUCSAlertingCallbacks.REL_EMPTY, 1);
    }
    
    @Test
    public void testSingleRegisteredCallback() throws MalformedURLException{
        
        controller.registerUCSAlertingCallback(new URL("http://localhost:8080/App/listener"));
        
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.CALLBACK_ATTRIBUTE_NAME, "callback.url");
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.REMOVE_PROTOCOL_FROM_URL, "false");
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSGetUCSAlertingCallbacks.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSGetUCSAlertingCallbacks.REL_SUCCESS).get(0);
        
        assertThat(flowFile.getAttribute("callback.url"), is("http://localhost:8080/App/listener"));
    }
    
    @Test
    public void testSingleRegisteredCallbackRemoveProtocol() throws MalformedURLException{
        
        controller.registerUCSAlertingCallback(new URL("http://localhost:8080/App/listener"));
        
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.CALLBACK_ATTRIBUTE_NAME, "callback.url");
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.REMOVE_PROTOCOL_FROM_URL, "true");
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSGetUCSAlertingCallbacks.REL_SUCCESS, 1);
        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship(UCSGetUCSAlertingCallbacks.REL_SUCCESS).get(0);
        
        assertThat(flowFile.getAttribute("callback.url"), is("localhost:8080/App/listener"));
    }
    
    @Test
    public void testMultipleRegisteredCallback() throws MalformedURLException{
        
        controller.registerUCSAlertingCallback(new URL("http://localhost:8080/App/listener"));
        controller.registerUCSAlertingCallback(new URL("https://localhost:8080/OtherApp/listener"));
        
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.CALLBACK_ATTRIBUTE_NAME, "callback.url");
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.REMOVE_PROTOCOL_FROM_URL, "false");
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSGetUCSAlertingCallbacks.REL_SUCCESS, 2);
        Set<String> callbacks = testRunner.getFlowFilesForRelationship(UCSGetUCSAlertingCallbacks.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute("callback.url"))
                .collect(Collectors.toSet());
        
        assertThat(callbacks, containsInAnyOrder("http://localhost:8080/App/listener", "https://localhost:8080/OtherApp/listener"));
    }
    
    @Test
    public void testMultipleRegisteredCallbackRemoveProtocol() throws MalformedURLException{
        
        controller.registerUCSAlertingCallback(new URL("http://localhost:8080/App/listener"));
        controller.registerUCSAlertingCallback(new URL("https://localhost:8080/OtherApp/listener"));
        
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.CALLBACK_ATTRIBUTE_NAME, "callback.url");
        testRunner.setProperty(UCSGetUCSAlertingCallbacks.REMOVE_PROTOCOL_FROM_URL, "true");
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        
        testRunner.assertAllFlowFilesTransferred(UCSGetUCSAlertingCallbacks.REL_SUCCESS, 2);
        Set<String> callbacks = testRunner.getFlowFilesForRelationship(UCSGetUCSAlertingCallbacks.REL_SUCCESS).stream()
                .map(ff -> ff.getAttribute("callback.url"))
                .collect(Collectors.toSet());
        
        assertThat(callbacks, containsInAnyOrder("localhost:8080/App/listener", "localhost:8080/OtherApp/listener"));
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSGetUCSAlertingCallbacks());
    }
}
