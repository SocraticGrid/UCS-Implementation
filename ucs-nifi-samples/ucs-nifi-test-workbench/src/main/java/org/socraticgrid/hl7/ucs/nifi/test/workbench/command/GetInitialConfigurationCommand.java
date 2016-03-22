/*
 * Copyright 2016 Apache NiFi Project.
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

import com.cognitivemedicine.config.utils.ConfigUtils;
import com.google.gson.JsonObject;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.util.ConfigConstants;

/**
 *
 * @author esteban
 */
public class GetInitialConfigurationCommand implements Command {

    private ConfigUtils config;
    
    @Override
    public void init(JsonObject config) {
        this.config = ConfigUtils.getInstance(ConfigConstants.CONFIG_CONTEXT_NAME);
    }

    @Override
    public JsonObject execute() {
        String serverHost = config.getString(ConfigConstants.UCS_SERVER_HOST_DEFAULT_KEY);
        String serverSendMessagePort = config.getString(ConfigConstants.UCS_SERVER_SEND_MESSAGE_PORT_DEFAULT_KEY);
        String serverClientPort = config.getString(ConfigConstants.UCS_SERVER_CLIENT_PORT_DEFAULT_KEY);
        String serverAlertingPort = config.getString(ConfigConstants.UCS_SERVER_ALERTING_PORT_DEFAULT_KEY);
        String serverManagementPort = config.getString(ConfigConstants.UCS_SERVER_MANAGEMENT_PORT_DEFAULT_KEY);
        String serverConversationPort = config.getString(ConfigConstants.UCS_SERVER_CONVERSATION_PORT_DEFAULT_KEY);
        
        
        String clientHost = config.getString(ConfigConstants.UCS_CLIENT_HOST_DEFAULT_KEY);
        
        JsonObject result = new JsonObject();
        result.addProperty("serverHost", serverHost);
        result.addProperty("serverSendMessagePort", serverSendMessagePort);
        result.addProperty("serverClientPort", serverClientPort);
        result.addProperty("serverAlertingPort", serverAlertingPort);
        result.addProperty("serverManagementPort", serverManagementPort);
        result.addProperty("serverConversationPort", serverConversationPort);
        
        result.addProperty("clientHost", clientHost);
        
        return result;
    }
    
}
