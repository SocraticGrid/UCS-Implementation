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
import com.google.gson.JsonObject;
import java.util.List;
import org.socraticgrid.hl7.services.uc.interfaces.ConversationIntf;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.converter.ToJSONConverter;

/**
 *
 * @author esteban
 */
public class QueryConversationsCommand implements Command {

    @Override
    public void init(JsonObject config) {
    }

    @Override
    public JsonObject execute() {
        try {
            
            JsonObject result = new JsonObject();
            final JsonArray results = new JsonArray();
            if (CreateUCSSessionCommand.getLastSession() != null){
                ConversationIntf conversation = CreateUCSSessionCommand.getLastSession().getNewConversation();
                List<Conversation> conversations = conversation.queryConversions(null, null);

                conversations.stream()
                        .map(c -> ToJSONConverter.toJsonObject(c))
                        .forEach(results::add);
            }
            
            result.add("conversations", results);
            
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error executing Query Conversations Command: " + ex.getMessage(), ex);
        }
    }
    
}
