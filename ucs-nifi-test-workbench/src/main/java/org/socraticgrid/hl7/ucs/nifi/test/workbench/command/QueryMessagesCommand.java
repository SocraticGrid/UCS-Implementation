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
import java.util.List;
import java.util.Optional;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageBody;
import org.socraticgrid.hl7.services.uc.model.MessageSummary;
import org.socraticgrid.hl7.services.uc.model.MessageType;
import org.socraticgrid.hl7.services.uc.model.SimpleMessage;
import org.socraticgrid.hl7.services.uc.model.SimpleMessageHeader;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.converter.ToJSONConverter;

/**
 *
 * @author esteban
 */
public class QueryMessagesCommand implements Command {

    private Optional<MessageType> typeMessageTypeFilter;
    
    @Override
    public void init(JsonObject config) {
        JsonElement typeMessageTypeFilterElement = config.get("typeMessageTypeFilter");
        
        typeMessageTypeFilter = Optional.ofNullable(typeMessageTypeFilterElement == null ? null : MessageType.valueOf(typeMessageTypeFilterElement.getAsString()));
    }

    @Override
    public JsonObject execute() {
        try {
            final JsonArray results = new JsonArray();
            
            List<MessageSummary> messageSummaries = CreateUCSSessionCommand.getLastSession().getNewClient().queryMessage(null);

            //to make things easier for the client side, we will convert each 
            //MessageSummary into a Message here.
            //This is specifically taylored for the UI.
            messageSummaries.stream()
                    .filter(qm -> typeMessageTypeFilter.isPresent() ? typeMessageTypeFilter.get().equals(qm.getMessageType()): true)
                    .map(qm -> {
                        MessageBody messageBody = new MessageBody();
                        messageBody.setContent(qm.getSubject());
                        messageBody.setType("text/plain");
                        
                        SimpleMessageHeader header = new SimpleMessageHeader();
                        header.setMessageId(qm.getMessageId());
                        header.setCreated(qm.getCreated());
                        header.setRecipientsList(qm.getRecipientsList());
                        header.setSender(qm.getSender());
                        header.setSubject(qm.getSubject());
                        header.setDeliveryStatusList(qm.getDeliveryStatusList());
                        header.setRelatedConversationId(qm.getRelatedConversationId());
                        header.setRelatedMessageId(qm.getRelatedMessageId());

                        Message m = new SimpleMessage(header);
                        m.setParts(new MessageBody[]{messageBody});
                        return m;
                    }).
                    map(m -> ToJSONConverter.toJsonObject(m)).
                    forEach(jo -> results.add(jo));
            
            JsonObject result = new JsonObject();
            result.add("messages", results);
            
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error executing New Message Command: " + ex.getMessage(), ex);
        }
    }

}
