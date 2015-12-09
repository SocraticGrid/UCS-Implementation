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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatController;

@Tags({"chat", "message"})
@CapabilityDescription("Create chat group dynamically and send a chat message")
public class SendDynamicGroupChatMessage extends AbstractProcessor {

    public static final PropertyDescriptor CHAT_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("CHAT Controller Service")
            .description("The CHAT Controller Service that this Processor uses to send messages.")
            .identifiesControllerService(ChatController.class)
            .required(true)
            .build();
    
    public static final PropertyDescriptor CHAT_MESSAGE = new PropertyDescriptor.Builder()
            .name("Message")
            .description("Chat message")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CHAT_PARTICIPANTS = new PropertyDescriptor.Builder()
            .name("Participants")
            .description("Participants who will join into chat group")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CHAT_NEW_GROUP_NAME = new PropertyDescriptor.Builder()
            .name("Group Name")
            .description("The name to set to the created room. This implementation requires a valid JID value.")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor CHAT_NEW_GROUP_SUBJECT = new PropertyDescriptor.Builder()
            .name("Group Subject")
            .description("The subject to set to the created room")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor CHAT_SENDER = new PropertyDescriptor.Builder()
            .name("Sender")
            .description("The user who actually sends the message")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final Relationship REL_CHAT_SEND = new Relationship.Builder().name("success").description("The Chat message has benn sent to group ").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("Chat message failed to deliver to the group").build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(CHAT_CONTROLLER_SERVICE);
        properties.add(CHAT_NEW_GROUP_NAME);
        properties.add(CHAT_NEW_GROUP_SUBJECT);
        properties.add(CHAT_SENDER);
        properties.add(CHAT_PARTICIPANTS);
        properties.add(CHAT_MESSAGE);

        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_CHAT_SEND);
        relationships.add(REL_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        final FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }
        final ProcessorLog logger = getLogger();
        try {
            //fetch property values
            final String chatMessage = context.getProperty(CHAT_MESSAGE).evaluateAttributeExpressions(flowFile).getValue();
            final String newGroupName = context.getProperty(CHAT_NEW_GROUP_NAME).evaluateAttributeExpressions(flowFile).getValue();
            final String newGroupSubject = context.getProperty(CHAT_NEW_GROUP_SUBJECT).evaluateAttributeExpressions(flowFile).getValue();
            final String participants = context.getProperty(CHAT_PARTICIPANTS).evaluateAttributeExpressions(flowFile).getValue();
            final String sender = context.getProperty(CHAT_SENDER).evaluateAttributeExpressions(flowFile).getValue();

            logger.info("chatMessage:" + chatMessage);
            logger.info("newGroupName:" + newGroupName);
            logger.info("newGroupSubject:" + newGroupSubject);
            logger.info("sender:" + sender);
            logger.info("participants:" + participants);
            
            List<String> finalParticipants = this.parseParticipants(participants);

            ChatController chatService = context.getProperty(CHAT_CONTROLLER_SERVICE).asControllerService(ChatController.class);
            chatService.sendMessageToDynamicRoom(newGroupName, newGroupSubject, chatMessage, sender, finalParticipants);

            session.getProvenanceReporter().route(flowFile, REL_CHAT_SEND);
            session.transfer(flowFile, REL_CHAT_SEND);
            logger.info("Message sent. Routing {} to {}", new Object[]{flowFile, REL_CHAT_SEND});

        } catch (Exception e) {
            e.printStackTrace();
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Error sending Chat message: " + e.getMessage(),
                    ExceptionType.Delivery,
                    null,
                    null);
        }

    }

    private List<String> parseParticipants(String participants) {
        List<String> participantList = new ArrayList<>();
        JsonParser jsonParser = new JsonParser();
        JsonArray jsonArray = (JsonArray) jsonParser.parse(participants);

        for (JsonElement jsonElement : jsonArray) {
            final JsonObject jsonObj = jsonElement.getAsJsonObject();
            participantList.add(jsonObj.get("participant").getAsString());
        }
        return participantList;
    }
}
