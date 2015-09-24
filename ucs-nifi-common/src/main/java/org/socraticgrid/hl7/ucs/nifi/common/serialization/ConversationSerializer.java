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
package org.socraticgrid.hl7.ucs.nifi.common.serialization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;

/**
 *
 * @author esteban
 */
public class ConversationSerializer {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSerializer.class);

    public static String serializeConversationWrappers(XMLListWrapper<ConversationWrapper> conversations) throws MessageSerializationException {
        try {
            logger.debug("Serializing XMLListWrapper {}", conversations);
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, ConversationWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(conversations, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Conversation serialization.", e);
        }
    }
    
    public static String serializeConversationWrappers(List<ConversationWrapper> conversations) throws MessageSerializationException {
        return serializeConversationWrappers(new XMLListWrapper<>(conversations));
    }
    
    public static String serializeConversationWrapper(ConversationWrapper conversation) throws MessageSerializationException {
        try {
            logger.debug("Serializing ConversationWrapper {}", conversation);
            JAXBContext context = JAXBContext.newInstance(ConversationWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(conversation, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Conversation serialization.", e);
        }
    }

    public static XMLListWrapper<ConversationWrapper> deserializeConversationWrappers(InputStream conversations) throws MessageSerializationException {
        try {
            logger.debug("Deserializing ConversationWrapper");
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, ConversationWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (XMLListWrapper<ConversationWrapper>)u.unmarshal(conversations);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ConversationWrapper deserialization.", e);
        }
    }
    
    public static XMLListWrapper<ConversationWrapper> deserializeConversationWrappers(String conversations) throws MessageSerializationException {
        return deserializeConversationWrappers(new ByteArrayInputStream(conversations.getBytes()));
    }
    
    public static ConversationWrapper deserializeConversationWrapper(InputStream conversation) throws MessageSerializationException {
        try {
            logger.debug("Deserializing ConversationWrapper");
            JAXBContext context = JAXBContext.newInstance(ConversationWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (ConversationWrapper)u.unmarshal(conversation);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ConversationWrapper deserialization.", e);
        }
    }
    
    public static ConversationWrapper deserializeConversationWrapper(String conversation) throws MessageSerializationException {
        return deserializeConversationWrapper(new ByteArrayInputStream(conversation.getBytes()));
    }
}
