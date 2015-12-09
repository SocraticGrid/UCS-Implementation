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
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationInfoWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;

/**
 *
 * @author esteban
 */
public class ConversationInfoSerializer {

    private static final Logger logger = LoggerFactory.getLogger(ConversationInfoSerializer.class);

    public static String serializeConversationInfoWrappers(XMLListWrapper<ConversationInfoWrapper> conversations) throws MessageSerializationException {
        try {
            logger.debug("Serializing XMLListWrapper {}", conversations);
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, ConversationInfoWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(conversations, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Conversation serialization.", e);
        }
    }
    
    public static String serializeConversationInfoWrappers(List<ConversationInfoWrapper> conversations) throws MessageSerializationException {
        return serializeConversationInfoWrappers(new XMLListWrapper<>(conversations));
    }
    
    public static String serializeConversationInfoWrapper(ConversationInfoWrapper conversation) throws MessageSerializationException {
        try {
            logger.debug("Serializing ConversationInfoWrapper {}", conversation);
            JAXBContext context = JAXBContext.newInstance(ConversationInfoWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(conversation, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Conversation serialization.", e);
        }
    }

    public static XMLListWrapper<ConversationInfoWrapper> deserializeConversationInfoWrappers(InputStream conversations) throws MessageSerializationException {
        try {
            logger.debug("Deserializing ConversationInfoWrapper");
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, ConversationInfoWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (XMLListWrapper<ConversationInfoWrapper>)u.unmarshal(conversations);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ConversationInfoWrapper deserialization.", e);
        }
    }
    
    public static XMLListWrapper<ConversationInfoWrapper> deserializeConversationInfoWrappers(String conversations) throws MessageSerializationException {
        return deserializeConversationInfoWrappers(new ByteArrayInputStream(conversations.getBytes()));
    }
    
    public static ConversationInfoWrapper deserializeConversationInfoWrapper(InputStream conversation) throws MessageSerializationException {
        try {
            logger.debug("Deserializing ConversationInfoWrapper");
            JAXBContext context = JAXBContext.newInstance(ConversationInfoWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (ConversationInfoWrapper)u.unmarshal(conversation);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ConversationInfoWrapper deserialization.", e);
        }
    }
    
    public static ConversationInfoWrapper deserializeConversationInfoWrapper(String conversation) throws MessageSerializationException {
        return deserializeConversationInfoWrapper(new ByteArrayInputStream(conversation.getBytes()));
    }
}
