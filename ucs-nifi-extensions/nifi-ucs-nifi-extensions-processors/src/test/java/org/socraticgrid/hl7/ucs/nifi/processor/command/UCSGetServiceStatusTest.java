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

import java.io.IOException;
import java.util.List;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.model.AdapterStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.Status;
import org.socraticgrid.hl7.ucs.nifi.common.model.UCSStatus;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.UCSStatusSerializer;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;

/**
 *
 * @author BasitAzeem
 *
 */
public class UCSGetServiceStatusTest extends UCSControllerServiceBasedTest {

    @Test
    public void testDefaultStatus() throws IOException, MessageSerializationException {

        testRunner.enqueue(new byte[]{});
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSGetServiceStatus.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSGetServiceStatus.REL_SUCCESS).get(0);
        Assert.assertNotNull(ff);
        
        String content = new String(ff.toByteArray());
        
        UCSStatus status = UCSStatusSerializer.deserializeUCSStatus(content);
        assertThat(status.getStatus(), is(Status.AVAILABLE));
        assertThat(status.getAdapterStatusList(), hasSize(5));
        assertThat(status.getAdapterStatusList().stream()
                .map(as -> as.getStatus()).collect(toSet()), both(
                        hasSize(1)).and(
                        contains(Status.AVAILABLE))
        );
    }

    @Test
    public void testUnavailableEmailStatus() throws IOException, MessageSerializationException {

        //Mark EMAIL as UNAVAILABLE
        this.serviceStatusControllerService.updateServiceStatus("EMAIL", Status.UNAVAILABLE);
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSGetServiceStatus.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSGetServiceStatus.REL_SUCCESS).get(0);
        Assert.assertNotNull(ff);
        
        String content = new String(ff.toByteArray());
        
        UCSStatus status = UCSStatusSerializer.deserializeUCSStatus(content);
        assertThat(status.getStatus(), is(Status.AVAILABLE));
        
        List<AdapterStatus> availableAdapters = status.getAdapterStatusList().stream()
                .filter(as -> as.getStatus() == Status.AVAILABLE)
                .collect(toList());
        
        List<AdapterStatus> unavailableAdapters = status.getAdapterStatusList().stream()
                .filter(as -> as.getStatus() == Status.UNAVAILABLE)
                .collect(toList());
        
        assertThat(availableAdapters, hasSize(4));
        assertThat(
                availableAdapters.stream()
                        .map(as -> as.getAdapterName())
                        .sorted()
                        .collect(joining(", "))
                , is ("ALERT, CHAT, SMS, VOIP"));
        
        assertThat(unavailableAdapters, hasSize(1));
        assertThat(
                unavailableAdapters.iterator().next().getAdapterName()
                , is ("EMAIL"));
    }
    
    @Test
    public void testAllAdaptersUnavailableStatus() throws IOException, MessageSerializationException {
        
        //Mark all the supported adapters as UNAVAILABLE
        this.controller.getSupportedAdapters().forEach(a->
                this.serviceStatusControllerService.updateServiceStatus(a.getAdapterName(), Status.UNAVAILABLE)
        );
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSGetServiceStatus.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSGetServiceStatus.REL_SUCCESS).get(0);
        Assert.assertNotNull(ff);
        
        String content = new String(ff.toByteArray());
        
        UCSStatus status = UCSStatusSerializer.deserializeUCSStatus(content);
        assertThat(status.getStatus(), is(Status.UNAVAILABLE));
        assertThat(status.getAdapterStatusList(), hasSize(5));
        assertThat(status.getAdapterStatusList().stream()
                .map(as -> as.getStatus()).collect(toSet()), both(
                        hasSize(1)).and(
                        contains(Status.UNAVAILABLE))
        );
    }
    
    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSGetServiceStatus());
    }

}
