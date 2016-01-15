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
package org.socraticgrid.hl7.ucs.nifi.test.workbench;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.CancelMessagesCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.Command;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.CreateConversationCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.CreateUCSSessionCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.DiscoverChannelsCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.GetAllMessagesCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.GetInitialConfigurationCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.GetStatusCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.NewAlertMessageCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.NewMessageCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.PingUCSSessionStatusCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.QueryConversationsCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.QueryMessagesCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.RetrieveConversationCommand;
import org.socraticgrid.hl7.ucs.nifi.test.workbench.command.UpdateMessagesCommand;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author esteban
 */
@ServerEndpoint("/ucs")
public class UCSClientWS {

    public static enum MESSAGE_TYPE {
        MESSAGE,
        RESPONSE,
        EXCEPTION,
        ALERT_NEW,
        ALERT_UPDATED,
        ALERT_CANCELED
    }

    private static final Map<String, Session> sessionById = Collections.synchronizedMap(new HashMap());

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) throws IOException, InterruptedException {
        sessionById.put(session.getId(), session);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessionById.remove(session.getId());
    }

    @OnMessage
    public String onMessage(String message) {
        JsonObject response = new JsonObject();
        try{
            JsonParser parser = new JsonParser();
            JsonObject jo = parser.parse(message).getAsJsonObject();

            String type = jo.get("type").getAsString();
            
            if (jo.get("callbackId") != null){
                String callbackId = jo.get("callbackId").getAsString();
                response.addProperty("callbackId", callbackId);
            }

            Command command;
            switch(type){
                case "getInitialConfiguration":
                    command = new GetInitialConfigurationCommand();
                    break;
                case "createUCSSession":
                    command = new CreateUCSSessionCommand();
                    break;
                case "pingUCSSessionStatus":
                    command = new PingUCSSessionStatusCommand();
                    break;
                case "newMessage":
                    command = new NewMessageCommand();
                    break;
                case "newAlertMessage":
                    command = new NewAlertMessageCommand();
                    break;
                case "getAllMessages":
                    command = new GetAllMessagesCommand();
                    break;
                case "queryMessages":
                    command = new QueryMessagesCommand();
                    break;
                case "updateMessage":
                    command = new UpdateMessagesCommand();
                    break;
                case "cancelMessage":
                    command = new CancelMessagesCommand();
                    break;
                case "discoverChannels":
                    command = new DiscoverChannelsCommand();
                    break;
                case "getStatus":
                    command = new GetStatusCommand();
                    break;
                case "queryConversations":
                    command = new QueryConversationsCommand();
                    break;
                case "retrieveConversation":
                    command = new RetrieveConversationCommand();
                    break;
                case "createConversation":
                    command = new CreateConversationCommand();
                    break;
                default: 
                    throw new IllegalArgumentException("Unknown command '"+type+"'");
            }
            
            command.init(jo);
            JsonObject commandResult = command.execute();
            
            response.addProperty("success", Boolean.TRUE);
            response.add("result", commandResult);
        } catch (Exception e) {
            e.printStackTrace();
            response.addProperty("success", Boolean.FALSE);
            response.addProperty("error", e.getMessage());
        }        
        
        return new Gson().toJson(response);
    }

    public static void broadcast(MESSAGE_TYPE type, JsonObject value) {

        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("value", value);

        final String json = new Gson().toJson(message);

        sessionById.values().forEach(s -> {
            if (s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(json);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

}
