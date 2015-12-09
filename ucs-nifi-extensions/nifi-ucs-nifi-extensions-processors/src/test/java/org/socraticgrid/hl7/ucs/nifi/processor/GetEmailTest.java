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

import java.io.IOException;
import java.util.Collection;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author BasitAzeem
 *
 */
public class GetEmailTest {

    private TestRunner testRunner;

    @Before
    public void init() throws Exception {
        testRunner = TestRunners.newTestRunner(new GetEmail());
        testRunner.setProperty(GetEmail.IMAP_SERVER_URL, "smtp.gmail.com");
        testRunner.setProperty(GetEmail.IMAP_USERNAME, "nifi@socraticgrid.org");
        testRunner.setProperty(GetEmail.IMAP_PASSWORD, "nifi_socraticgrid");
    }

    @Test
    public void doTestReceiveEmail() throws IOException {
        testRunner.run();
        Collection<MockFlowFile> flowFiles = testRunner
                .getFlowFilesForRelationship(GetEmail.REL_NEW_MESSAGE);
        Assert.assertNotNull(flowFiles);
    }

}
