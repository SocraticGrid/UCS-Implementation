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
package org.socraticgrid.hl7.ucs.nifi.controller.chat;

import org.jivesoftware.smack.packet.Message;

/**
 *
 * @author esteban
 */
public class ChatMessageBuilder {
    
    public static ChatMessage fromValues(String id, String sender, String body, String roomId){
        ChatMessage message = new ChatMessage();
        
        message.setId(id);
        message.setRoomId(roomId);
        message.setSenderId(sender);
        message.setMessage(body);
        
        return message;
    }
    
    public static ChatMessage fromSmackMessage(Message sm, String roomId){
        ChatMessage message = new ChatMessage();
        
        message.setId(sm.getPacketID());
        message.setRoomId(roomId);
        message.setMessage(sm.getBody());
        
        //m.getFrom() format: <room_id>@<domain>/<user_id>
        //i.e.: 
        //rooma@conference.socraticgrid.org/ealiverti
        //rooma@conference.socraticgrid.org/nifi@socraticgrid.org
        message.setSenderId(extractSenderId(sm.getFrom()));
        
        return message;
    }
    
    private static String extractSenderId(String fromFieldValue){
        if (fromFieldValue == null){
            return null;
        }
        
        String[] fromPart = fromFieldValue.split("/");
        
        return fromPart.length < 2 ? null : fromPart[1];
    }
}
