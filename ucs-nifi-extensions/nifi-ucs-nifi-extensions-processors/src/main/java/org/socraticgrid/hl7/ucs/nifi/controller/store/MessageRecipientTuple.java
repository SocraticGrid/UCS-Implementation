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
package org.socraticgrid.hl7.ucs.nifi.controller.store;

/**
 *
 * @author esteban
 */
public class MessageRecipientTuple {
    private final String messageId;
    private final String recipientId;

    public MessageRecipientTuple(String messageId, String recipientId) {
        this.messageId = messageId;
        this.recipientId = recipientId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRecipientId() {
        return recipientId;
    }
    
}
