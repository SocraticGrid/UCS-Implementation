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
package org.socraticgrid.hl7.ucs.nifi.services;

import java.io.IOException;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class MessageBodyResolverTest {
    
    @Test
    public void testRecipientId() throws IOException, MessageSerializationException{
        Message message = new MessageBuilder()
                .addBody(new MessageBuilder.Body("This is an SMS for recipient 1", "text/plain", MessageBodyResolver.RECIPIENT_PREFIX+"1"))
                .addBody(new MessageBuilder.Body("This is an SMS for recipient 2", "text/plain", MessageBodyResolver.RECIPIENT_PREFIX+"2"))
                .addRecipient(new MessageBuilder.Recipient("1", "123456", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("2", "654321", "SMS"))
                .buildMessage();
 
        //resolve using recipientId only
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, null, "1", null).getContent(),
                is("This is an SMS for recipient 1")
        );
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, null, "2", null).getContent(),
                is("This is an SMS for recipient 2")
        );
        
        //resolve using recipientId and a fake address.
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, null, "1", "fake1").getContent(),
                is("This is an SMS for recipient 1")
        );
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, null, "2", "fake2").getContent(),
                is("This is an SMS for recipient 2")
        );
        
        //resolve using recipientId and systemId (other than SMS). Recipient 
        //takes precedence over system
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, "EMAIL", "1", null).getContent(),
                is("This is an SMS for recipient 1")
        );
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, "EMAIL", "2", null).getContent(),
                is("This is an SMS for recipient 2")
        );
        
    }
    
    @Test
    public void testAddress() throws IOException, MessageSerializationException{
        Message message = new MessageBuilder()
                .addBody(new MessageBuilder.Body("This is an SMS for recipient 1", "text/plain", MessageBodyResolver.RECIPIENT_PREFIX+"123456"))
                .addBody(new MessageBuilder.Body("This is an SMS for recipient 2", "text/plain", MessageBodyResolver.RECIPIENT_PREFIX+"654321"))
                .addRecipient(new MessageBuilder.Recipient("1", "123456", "SMS"))
                .addRecipient(new MessageBuilder.Recipient("2", "654321", "SMS"))
                .buildMessage();
 
        //resolve using address only
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, null, null, "123456").getContent(),
                is("This is an SMS for recipient 1")
        );
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, null, null, "654321").getContent(),
                is("This is an SMS for recipient 2")
        );
        
    }
    
    @Test
    public void testServiceId() throws IOException, MessageSerializationException{
        Message message = new MessageBuilder()
                .addBody(new MessageBuilder.Body("This is an SMS", "text/plain", MessageBodyResolver.SERVICE_PREFIX+"SMS"))
                .addBody(new MessageBuilder.Body("This is an EMAIL", "text/plain", MessageBodyResolver.SERVICE_PREFIX+"EMAIL"))
                .addRecipient(new MessageBuilder.Recipient("1", "123456", "EMAIL"))
                .buildMessage();
 
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, "SMS", null, null).getContent(),
                is("This is an SMS")
        );
        assertThat(
            MessageBodyResolver.resolveMessagePart(message, "EMAIL", null, null).getContent(),
                is("This is an EMAIL")
        );
        
    }
    
}
