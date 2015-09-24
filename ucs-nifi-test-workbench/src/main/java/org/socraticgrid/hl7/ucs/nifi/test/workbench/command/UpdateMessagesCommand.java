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
package org.socraticgrid.hl7.ucs.nifi.test.workbench.command;

import com.google.gson.JsonObject;
import java.util.List;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.MessageSummary;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.util.AlertMessageBuilder;

/**
 *
 * @author esteban
 */
public class UpdateMessagesCommand implements Command {
    private AlertStatus status;
    private String messageId;

    
    @Override
    public void init(JsonObject config) {
        messageId = config.get("messageId").getAsString();
        status = AlertStatus.valueOf(config.get("status").getAsString());
    }

    @Override
    public JsonObject execute() {
        try {
            
            MessageWrapper messageWrapper = new AlertMessageBuilder().withStatus(status)
                    .withMessageId(messageId)
                    .buildMessageWrapper();
            
            AlertMessage alertMessage = (AlertMessage) messageWrapper.getMessage();
            
            CreateUCSSessionCommand.getLastSession().getNewAlerting().updateAlert(alertMessage);

            
            JsonObject result = new JsonObject();
            
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error executing Update Message Command: " + ex.getMessage(), ex);
        }
    }

}
