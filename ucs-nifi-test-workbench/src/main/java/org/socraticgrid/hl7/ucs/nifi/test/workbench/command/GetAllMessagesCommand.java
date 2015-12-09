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
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.ConversationRequestMessage;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageType;
import org.socraticgrid.hl7.services.uc.model.NotificationMessage;
import org.socraticgrid.hl7.services.uc.model.SimpleMessage;
import org.socraticgrid.hl7.ucs.nifi.api.ClientImpl;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.converter.ToJSONConverter;

/**
 *
 * @author esteban
 */
public class GetAllMessagesCommand implements Command {

    private Optional<MessageType> typeMessageTypeFilter;
    private Class<? extends Message> typeMessageTypeClass;
    
    @Override
    public void init(JsonObject config) {
        JsonElement typeMessageTypeFilterElement = config.get("typeMessageTypeFilter");
        
        typeMessageTypeFilter = Optional.ofNullable(typeMessageTypeFilterElement == null ? null : MessageType.valueOf(typeMessageTypeFilterElement.getAsString()));
        
        //awful workaround to the fact that Message.getMEssageType() has a protected
        //accessor...
        if (typeMessageTypeFilter.isPresent()){
            switch (typeMessageTypeFilter.get()){
                case Alert:
                    typeMessageTypeClass = AlertMessage.class;
                    break;
                case ConversationRequest:
                    typeMessageTypeClass = ConversationRequestMessage.class;
                    break;
                case Notification:
                    typeMessageTypeClass = NotificationMessage.class;
                    break;
                case SimpleMessage:
                    typeMessageTypeClass = SimpleMessage.class;
                    break;
            }
        }
    }

    @Override
    public JsonObject execute() {
        try {
            final JsonArray results = new JsonArray();
            
            List<Message> messages = ((ClientImpl)CreateUCSSessionCommand.getLastSession().getNewClient()).listMessages();

            //to make things easier for the client side, we will convert each 
            //MessageSummary into a Message here.
            //This is specifically taylored for the UI.
            messages.stream()
                    .filter(m -> typeMessageTypeFilter.isPresent() ? typeMessageTypeClass.isInstance(m) : true)
                    .map(m -> ToJSONConverter.toJsonObject(m))
                    .forEach(jo -> results.add(jo));
            
            JsonObject result = new JsonObject();
            result.add("messages", results);
            
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error executing New Message Command: " + ex.getMessage(), ex);
        }
    }

}
