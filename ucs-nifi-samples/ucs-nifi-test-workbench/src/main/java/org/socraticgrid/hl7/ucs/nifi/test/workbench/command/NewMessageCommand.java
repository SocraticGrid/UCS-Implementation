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
package org.socraticgrid.hl7.ucs.nifi.test.workbench.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.util.MessageBuilder;

/**
 *
 * @author esteban
 */
public class NewMessageCommand implements Command {
    private Message message;

    public static class Recipient {
        public String to;
        public String type;

        public String getTo() {
            return to;
        }

        public String getType() {
            return type;
        }
        
    }
    
    @Override
    public void init(JsonObject config) {
        try {
            String conversationId = config.get("conversationId").getAsString();
            String from = config.get("from").getAsString();
            String subject = config.get("subject").getAsString();
            String body = config.get("body").getAsString();
            
            List<Recipient> finalRecipients = new ArrayList<>();
            
            JsonArray recipients = config.get("recipients").getAsJsonArray();
            for (JsonElement recipient : recipients) {
                Recipient r = new Recipient();
                r.to = recipient.getAsJsonObject().get("to").getAsString();
                r.type = recipient.getAsJsonObject().get("type").getAsString();
                
                finalRecipients.add(r);
            }
            
            MessageBuilder messageBuilder = new MessageBuilder()
                    .withMessageId(UUID.randomUUID().toString())
                    .withSender(from)
                    .withConversationId(conversationId)
                    .withSubject(subject)
                    .withBody(body);
                    
            
            for (Recipient finalRecipient : finalRecipients) {
                messageBuilder.addRecipient(new MessageBuilder.Recipient(finalRecipient.getTo(), finalRecipient.getType()));
            }
            
            this.message = messageBuilder.buildMessage();
        } catch (IOException | MessageSerializationException ex) {
            throw new IllegalArgumentException("Error preparing New Message Command", ex);
        }
    }

    @Override
    public JsonObject execute() {
        try {
            CreateUCSSessionCommand.getLastSession().getNewClient().sendMessage(new MessageModel(message));
        
            return new JsonObject();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error executing New Message Command: "+ex.getMessage(), ex);
        }
    }
    
}
