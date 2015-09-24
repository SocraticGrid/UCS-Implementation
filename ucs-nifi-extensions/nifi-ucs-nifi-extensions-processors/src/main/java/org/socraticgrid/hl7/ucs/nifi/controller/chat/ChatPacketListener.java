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

import java.util.Map;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

/**
 *
 * @author esteban
 */
public class ChatPacketListener implements PacketListener {
    private final String roomId;
    private final Map<String, ChatMessage> messageBucket;

    public ChatPacketListener(String roomId, Map<String, ChatMessage> messageBucket) {
        this.roomId = roomId;
        this.messageBucket = messageBucket;
    }
    
    @Override
    public void processPacket(Packet packet) {
        if (!(packet instanceof Message)) {
            //we are not interested in this.
            return;
        }

        Message m = (Message)packet;
        if (m.getProperty("ucs-created") != null && "true".equals(m.getProperty("ucs-created"))){
            //this is a message sent by UCS itself. Discard.
            return;
        }
        
        ChatMessage message = ChatMessageBuilder.fromSmackMessage(m, roomId);
        messageBucket.put(message.getId(), message);
    }

}
