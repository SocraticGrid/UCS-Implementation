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

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.AdapterSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSControllerServiceBasedTest;

/**
 *
 * @author BasitAzeem
 *
 */
public class UCSGetSupportedAdaptersTest extends UCSControllerServiceBasedTest {

    @Test
    public void testListFetched() throws IOException, MessageSerializationException {
        
        testRunner.enqueue(new byte[]{});
        testRunner.run();
        testRunner.assertAllFlowFilesTransferred(
                UCSGetSupportedAdapters.REL_SUCCESS, 1);
        MockFlowFile ff = testRunner.getFlowFilesForRelationship(
                UCSGetSupportedAdapters.REL_SUCCESS).get(0);
        Assert.assertNotNull(ff);
        
        String content = new String(ff.toByteArray());
        
        List<Adapter> adapters = AdapterSerializer.deserializeAdapters(content).getItems();
        
        assertThat(adapters, hasSize(5));
        assertThat("ALERT, CHAT, EMAIL, SMS, VOIP", is(adapters.stream()
            .map(a -> a.getAdapterName())
            .sorted()
            .collect(joining(", ")))
        );
        
    }

    @Override
    protected TestRunner createTestRunner() {
        return TestRunners.newTestRunner(new UCSGetSupportedAdapters());
    }
}
