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
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;

/**
 *
 * @author esteban
 */
public class MessageSerializer {

    private static final Logger logger = LoggerFactory.getLogger(MessageSerializer.class);

    public static String serializeMessageWrappers(XMLListWrapper<MessageWrapper> messageWrappers) throws MessageSerializationException {
        try {
            logger.debug("Serializing XMLListWrapper {}", messageWrappers);
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, MessageWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(messageWrappers, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Message serialization.", e);
        }
    }
    
    public static String serializeMessageWrappers(List<MessageWrapper> messageWrappers) throws MessageSerializationException {
        return serializeMessageWrappers(new XMLListWrapper<>(messageWrappers));
    }
    
    public static String serializeMessageWrapper(MessageWrapper messageWrapper) throws MessageSerializationException {
        try {
            logger.debug("Serializing MessageWrapper {}", messageWrapper);
            JAXBContext context = JAXBContext.newInstance(MessageWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(messageWrapper, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Message serialization.", e);
        }
    }

    public static XMLListWrapper<MessageWrapper> deserializeMessageWrappers(InputStream messageWrappers) throws MessageSerializationException {
        try {
            logger.debug("Deserializing MessageWrapper");
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, MessageWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (XMLListWrapper<MessageWrapper>)u.unmarshal(messageWrappers);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Message deserialization.", e);
        }
    }
    
    public static XMLListWrapper<MessageWrapper> deserializeMessageWrappers(String messageWrappers) throws MessageSerializationException {
        return deserializeMessageWrappers(new ByteArrayInputStream(messageWrappers.getBytes()));
    }
    
    public static MessageWrapper deserializeMessageWrapper(InputStream messageWrapper) throws MessageSerializationException {
        try {
            logger.debug("Deserializing MessageWrapper");
            JAXBContext context = JAXBContext.newInstance(MessageWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (MessageWrapper)u.unmarshal(messageWrapper);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Message deserialization.", e);
        }
    }
    
    public static MessageWrapper deserializeMessageWrapper(String messageWrapper) throws MessageSerializationException {
        return deserializeMessageWrapper(new ByteArrayInputStream(messageWrapper.getBytes()));
    }
}
