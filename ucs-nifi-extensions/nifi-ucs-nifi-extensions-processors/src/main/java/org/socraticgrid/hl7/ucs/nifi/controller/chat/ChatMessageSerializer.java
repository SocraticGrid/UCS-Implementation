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
package org.socraticgrid.hl7.ucs.nifi.controller.chat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
public class ChatMessageSerializer {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageSerializer.class);

    private ChatMessageSerializer() {
    }
    
    public static String serializeChatMessage(ChatMessage message) throws MessageSerializationException {
        try {
            logger.debug("Serializing ChatMessage {}", message);
            JAXBContext context = JAXBContext.newInstance(ChatMessage.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(message, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ChatMessage serialization.", e);
        }
    }
    
    public static ChatMessage deserializeChatMessage(InputStream chatMessage) throws MessageSerializationException {
        try {
            logger.debug("Deserializing ChatMessage");
            JAXBContext context = JAXBContext.newInstance(ChatMessage.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (ChatMessage)u.unmarshal(chatMessage);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ChatMessage deserialization.", e);
        }
    }
    
    public static ChatMessage deserializeChatMessage(String chatMessage) throws MessageSerializationException {
        return deserializeChatMessage(new ByteArrayInputStream(chatMessage.getBytes()));
    }
}
