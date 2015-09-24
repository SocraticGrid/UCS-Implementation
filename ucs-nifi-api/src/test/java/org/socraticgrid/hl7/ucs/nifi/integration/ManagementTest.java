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
package org.socraticgrid.hl7.ucs.nifi.integration;

import java.util.List;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Ignore;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.ServiceInfo;
import org.socraticgrid.hl7.services.uc.model.Status;

/**
 *
 * @author esteban
 */
public class ManagementTest extends BaseIntegrationTest {
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void discoverChannelsTest() throws Exception {
        List<ServiceInfo> discoverChannels = this.management.discoverChannels();
        
        assertThat(discoverChannels, hasSize(5));
        assertThat("ALERT, CHAT, EMAIL, SMS, VOIP", is(discoverChannels.stream()
            .map(si -> si.getServiceName())
            .sorted()
            .collect(joining(", ")))
        );
    }
    
    @Test
    @Ignore("We need a running instance of NiFi here")
    public void getStatusTest() throws Exception {
        List<Status> statuses = this.management.getStatus(null, null);
        
        assertThat(statuses, hasSize(5));
        assertThat("ALERT, CHAT, EMAIL, SMS, VOIP", is(statuses.stream()
            .map(si -> si.getCapability())
            .sorted()
            .collect(joining(", ")))
        );
        assertThat(statuses.stream()
                    .map(s -> s.isAvailable())
                    .collect(toSet()), 
                both(
                        hasSize(1)).and(
                        contains(Boolean.TRUE)
                )
        );
        
    }
    
}
