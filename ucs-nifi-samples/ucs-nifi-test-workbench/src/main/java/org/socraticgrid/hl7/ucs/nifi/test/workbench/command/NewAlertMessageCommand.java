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
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.socraticgrid.hl7.services.uc.model.MessageModel;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.stringtemplate.v4.ST;

/**
 *
 * @author esteban
 */
public class NewAlertMessageCommand implements Command {
    private String message;

    public static class Recipient {
        public String to;

        public String getTo() {
            return to;
        }

        public String getType() {
            return "ALERT";
        }
        
    }
    
    public static class Property {
        public String key;
        public String value;

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

    }
    
    @Override
    public void init(JsonObject config) {
        try {
            String conversationId = config.get("conversationId").getAsString();
            String from = config.get("from").getAsString();
            String status = config.get("status") == null ? "New" : config.get("status").getAsString();
            String subject = config.get("subject").getAsString();
            String body = config.get("body").getAsString();
            
            List<Recipient> finalRecipients = new ArrayList<>();
            
            JsonArray recipients = config.get("recipients").getAsJsonArray();
            for (JsonElement recipient : recipients) {
                Recipient r = new Recipient();
                r.to = recipient.getAsJsonObject().get("to").getAsString();
                finalRecipients.add(r);
            }
            
            List<Property> finalProperties = new ArrayList<>();
            JsonArray properties = config.get("properties").getAsJsonArray();
            for (JsonElement property : properties) {
                Property p = new Property();
                p.key = property.getAsJsonObject().get("key").getAsString();
                p.value = property.getAsJsonObject().get("value").getAsString();
                
                finalProperties.add(p);
            }
            
            String template = IOUtils.toString(NewAlertMessageCommand.class
                    .getResourceAsStream("/templates/alert-message-sample.tpl"));
            
            ST st = new ST(template, '$', '$');
            st.add("messageId", UUID.randomUUID().toString());
            st.add("from", from);
            st.add("conversationId", conversationId);
            st.add("subject", subject);
            st.add("body", body);
            st.add("status", status);
            st.add("recipients", finalRecipients);
            st.add("properties", finalProperties);
            
            this.message = st.render();
            
        } catch (IOException ex) {
            throw new IllegalArgumentException("Error preparing New Alert Message Command", ex);
        }
    }

    @Override
    public JsonObject execute() {
        try {
            MessageWrapper messageWrapper = MessageSerializer.deserializeMessageWrapper(message);
            
            CreateUCSSessionCommand.getLastSession().getNewClient().sendMessage(new MessageModel(messageWrapper.getMessage()));
        
            return new JsonObject();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error executing New Alert Message Command: "+ex.getMessage(), ex);
        }
    }
    
}
