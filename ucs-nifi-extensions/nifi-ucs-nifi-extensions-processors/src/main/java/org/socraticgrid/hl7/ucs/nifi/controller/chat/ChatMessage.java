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

import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author esteban
 */
@XmlRootElement
public class ChatMessage {
    
    private String id;
    private String roomId;
    private String senderId;
    private String message;

    public ChatMessage() {
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
    
    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ChatMessage{" + "id=" + id + ", roomId=" + roomId + ", senderId=" + senderId + ", message=" + message + '}';
    }
    
}
