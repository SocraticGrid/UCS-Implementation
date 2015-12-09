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
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;

/**
 * Utility class used to de/serialize Adapter objects.
 * @author esteban
 */
public class AdapterSerializer {

    private static final Logger logger = LoggerFactory.getLogger(AdapterSerializer.class);

    public static String serializeAdapters(XMLListWrapper<Adapter> adapter) throws MessageSerializationException {
        try {
            logger.debug("Serializing XMLListWrapper {}", adapter);
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, Adapter.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(adapter, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Adapter serialization.", e);
        }
    }
    
    public static String serializeAdapters(List<Adapter> adapter) throws MessageSerializationException {
        return serializeAdapters(new XMLListWrapper<>(adapter));
    }
    
    public static String serializeAdapter(Adapter adapter) throws MessageSerializationException {
        try {
            logger.debug("Serializing Adapter {}", adapter);
            JAXBContext context = JAXBContext.newInstance(Adapter.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(adapter, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Adapter serialization.", e);
        }
    }
    
    public static XMLListWrapper<Adapter> deserializeAdapters(InputStream adapter) throws MessageSerializationException {
        try {
            logger.debug("Deserializing Adapter");
            JAXBContext context = JAXBContext.newInstance(XMLListWrapper.class, Adapter.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (XMLListWrapper<Adapter>)u.unmarshal(adapter);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Message deserialization.", e);
        }
    }
    
    public static XMLListWrapper<Adapter> deserializeAdapters(String adapter) throws MessageSerializationException {
        return deserializeAdapters(new ByteArrayInputStream(adapter.getBytes()));
    }

    public static Adapter deserializeAdapter(InputStream adapter) throws MessageSerializationException {
        try {
            logger.debug("Deserializing Adapter");
            JAXBContext context = JAXBContext.newInstance(Adapter.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (Adapter)u.unmarshal(adapter);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in Adapter deserialization.", e);
        }
    }
    
    public static Adapter deserializeAdapter(String adapter) throws MessageSerializationException {
        return deserializeAdapter(new ByteArrayInputStream(adapter.getBytes()));
    }
}
