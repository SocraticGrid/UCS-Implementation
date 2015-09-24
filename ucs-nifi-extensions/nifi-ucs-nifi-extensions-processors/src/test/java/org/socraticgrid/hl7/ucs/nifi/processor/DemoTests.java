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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class DemoTests {
    
    @Test
    public void serializeMessagesTest() throws IOException{
        MessageBuilder escalationMessageBuilder1 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("This is an escalation message")
                .withBody("Escalation Message sent by CHAT")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"));

        MessageBuilder escalationMessageBuilder2 = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("This is an escalation message")
                .withBody("Escalation Message sent by SMS")
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"));

        String message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addOnFailureToReachAll(escalationMessageBuilder1)
                .addOnFailureToReachAll(escalationMessageBuilder2)
                .buildSerializedMessageWrapper();
        
        this.writeToFile("escalation-", message);
        
        message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .withBody("Some Body")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addOnNoResponseAll(escalationMessageBuilder1)
                .addOnNoResponseAll(escalationMessageBuilder2)
                .withRespondBy(1)
                .buildSerializedMessageWrapper();
        
        this.writeToFile("no-response-", message);
        
        
        message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .addBody(new MessageBuilder.Body("Chat for Esteban", "text/plain", "[RECIPIENT-ID]:ealiverti"))
                .addBody(new MessageBuilder.Body("Chat for Jonathan", "text/plain", "[RECIPIENT-ID]:jhughes"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "CHAT"))
                .buildSerializedMessageWrapper();
        
        this.writeToFile("body-route-by-recipient-", message);
        
        
        message = new MessageBuilder()
                .withConversationId("testC")
                .withSender("eafry")
                .withSubject("Some Subject")
                .addBody(new MessageBuilder.Body("Content for CHAT", "text/plain", "[SERVICE-ID]:CHAT"))
                .addBody(new MessageBuilder.Body("Content for SMS", "text/plain", "[SERVICE-ID]:SMS"))
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "CHAT"))
                .addRecipient(new MessageBuilder.Recipient("jhughes", "SMS"))
                .buildSerializedMessageWrapper();
        
        this.writeToFile("body-route-by-service-", message);
        
    }
    
    @Test
    public void serializeAlertMessageWithEscalation() throws Exception{
        
        MessageBuilder escalationAlertBuilder = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withMessageId(UUID.randomUUID().toString())
                .withSender("emory")
                .addRecipient(new MessageBuilder.Recipient("emory", "ALERT"))
                .withConversationId("c1")
                .withSubject("This is an escalation")
                .withBody("Escalation message body");
        
        
        String alertMessageWrapper = new AlertMessageBuilder().withStatus(AlertStatus.New)
                .withMessageId(UUID.randomUUID().toString())
                .withSender("emory")
                .addRecipient(new MessageBuilder.Recipient("ealiverti", "ALERT"))
                .withConversationId("c1")
                .withSubject("This is the original message")
                .withBody("Orginal message body")
                .withReceiptNotification(true)
                .withRespondBy(1)
                .addOnNoResponseAll(escalationAlertBuilder)
                .buildSerializedMessageWrapper();
        
        this.writeToFile("alert-with-escalation", alertMessageWrapper);
        
    }
    
    private String writeToFile(String prefix, String content) throws IOException{
        Path p = Files.createTempFile(prefix, ".xml");
        Writer writer = new OutputStreamWriter(new FileOutputStream(p.toFile()));
        IOUtils.write(content, writer);
        writer.close();
        return p.toFile().getAbsolutePath();
    }
}
