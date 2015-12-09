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
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socraticgrid.hl7.ucs.nifi.common.model.ExceptionWrapper;

/**
 * Utility class used to de/serialize ExceptionWrapper objects.
 * @author esteban
 */
public class ExceptionWrapperSerializer {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionWrapperSerializer.class);

    public static String serializeExceptionWrapper(ExceptionWrapper exceptionWrapper) throws MessageSerializationException {
        try {
            logger.debug("Serializing ExceptionWrapper {}", exceptionWrapper);
            JAXBContext context = JAXBContext.newInstance(ExceptionWrapper.class);
            Marshaller m = context.createMarshaller();

            StringWriter result = new StringWriter();
            m.marshal(exceptionWrapper, result);

            return result.toString();

        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ExceptionWrapper serialization.", e);
        }
    }

    public static ExceptionWrapper deserializeExceptionWrapper(InputStream exceptionWrapper) throws MessageSerializationException {
        try {
            logger.debug("Deserializing ExceptionWrapper");
            JAXBContext context = JAXBContext.newInstance(ExceptionWrapper.class);
            Unmarshaller u = context.createUnmarshaller();
            
            return (ExceptionWrapper)u.unmarshal(exceptionWrapper);
        } catch (Exception e) {
            throw new MessageSerializationException("Exception in ExceptionWrapper deserialization.", e);
        }
    }
    
    public static ExceptionWrapper deserializeExceptionWrapper(String exceptionWrapper) throws MessageSerializationException {
        return deserializeExceptionWrapper(new ByteArrayInputStream(exceptionWrapper.getBytes()));
    }
}
