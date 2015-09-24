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
package org.socraticgrid.hl7.ucs.nifi.services;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageBody;

/**
 *
 * @author pavan
 *
 */
public class MessageBodyResolver {

    public static final String SERVICE_PREFIX = "[SERVICE-ID]:";
    public static final String RECIPIENT_PREFIX = "[RECIPIENT-ID]:";

    /**
     * Method to determine which part of a Message should be used by a processor
     * given a context. The context required by this resolution is the service
     * Id, the recipient id and the recipient address being processed.
     *
     * @param message
     * @param serviceId
     * @param recipientId
     * @param recipientAddress
     * @return
     */
    public static MessageBody resolveMessagePart(Message message, String serviceId, String recipientId, String recipientAddress) {
        MessageBody body = null;

        List<MessageBody> bodies = Arrays.asList(message.getParts());

        //First check using recipientId
        if (recipientId != null) {
            Optional<MessageBody> first = bodies.stream()
                    .filter(b -> b.getTag() != null)
                    .filter(b -> b.getTag().equals(RECIPIENT_PREFIX + recipientId))
                    .findFirst();

            if (first.isPresent()) {
                return first.get();
            }
        }

        //now let's check using recipientAddress
        if (recipientAddress != null) {
            Optional<MessageBody> first = bodies.stream()
                    .filter(b -> b.getTag() != null)
                    .filter(b -> b.getTag().equals(RECIPIENT_PREFIX + recipientAddress))
                    .findFirst();

            if (first.isPresent()) {
                return first.get();
            }
        }

        //no match yet. Let's check using the serviceId
        if (serviceId != null) {
            Optional<MessageBody> first = bodies.stream()
                    .filter(b -> b.getTag() != null)
                    .filter(b -> b.getTag().equals(SERVICE_PREFIX + serviceId))
                    .findFirst();

            if (first.isPresent()) {
                return first.get();
            }
        }

        //no match. Let's return the first body of the message
        return bodies.get(0);

    }
}
