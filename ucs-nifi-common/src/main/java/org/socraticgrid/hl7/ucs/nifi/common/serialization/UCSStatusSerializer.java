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
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.ucs.nifi.common.model.UCSStatus;

/**
 * Utility class used to de/serialize UCSStatus objects.
 * @author esteban
 */
public class UCSStatusSerializer {

    private static final Logger logger = LoggerFactory.getLogger(UCSStatusSerializer.class);

    public static String serializeUCSStatus(UCSStatus ucsStatus) throws MessageSerializationException {
        try {
            logger.debug("Serializing UCSStatus {}", ucsStatus);
            JAXBContext context = JAXBContext.newInstance(UCSStatus.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(ucsStatus, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in UCSStatus serialization.", e);
        }
    }

    public static UCSStatus deserializeUCSStatus(InputStream ucsStatus) throws MessageSerializationException {
        try {
            logger.debug("Deserializing UCSStatus");
            JAXBContext context = JAXBContext.newInstance(UCSStatus.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (UCSStatus)u.unmarshal(ucsStatus);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in UCSStatus deserialization.", e);
        }
    }
    
    public static UCSStatus deserializeUCSStatus(String ucsStatus) throws MessageSerializationException {
        return deserializeUCSStatus(new ByteArrayInputStream(ucsStatus.getBytes()));
    }
}
