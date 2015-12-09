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
package org.socraticgrid.hl7.ucs.nifi.common.util;

import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;
import java.io.IOException;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class AlertMessageBuilderTest {
    
    @Test
    public void testNestedMessages() throws IOException, MessageSerializationException{
        
        String messageW = new AlertMessageBuilder()
                .withStatus(AlertStatus.Acknowledged)
                .withConversationId("testC")    
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealivert", "EMAIL"))
                .addOnFailureToReachAll(
                        new MessageBuilder()
                            .withConversationId("testC")
                            .withSender("eafry")
                            .withSubject("Where are you?")
                            .withBody("I coulnd't reach you!")
                            .addRecipient(new MessageBuilder.Recipient("eliverti", "SMS"))
                )
                .buildSerializedMessageWrapper();
        
        System.out.println("\nMessage:\n"+messageW+"\n");
        
        MessageWrapper mw = MessageSerializer.deserializeMessageWrapper(messageW);
        
        System.out.println("MessageW: "+mw);
        
        assertThat(mw.getMessage(), is(instanceOf(AlertMessage.class)));
        assertThat(((AlertMessage)mw.getMessage()).getHeader().getAlertStatus(), is(AlertStatus.Acknowledged));
        
    }
}
