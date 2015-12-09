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

import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
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
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatController;

@Tags({"chat", "message"})
@CapabilityDescription("Sends chat message to provided chat group")
public class SendGroupChatMessage extends AbstractProcessor {

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

    public static final PropertyDescriptor CHAT_SENDER = new PropertyDescriptor.Builder()
            .name("Sender")
            .description("The user who actually sends the message")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CHAT_GROUP = new PropertyDescriptor.Builder()
            .name("Group Name")
            .description("Chat group name where the message is going to be sent.")
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
        properties.add(CHAT_GROUP);
        properties.add(CHAT_SENDER);
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
            final String chatGroup = context.getProperty(CHAT_GROUP).evaluateAttributeExpressions(flowFile).getValue();
            final String chatSender = context.getProperty(CHAT_SENDER).evaluateAttributeExpressions(flowFile).getValue();

            logger.info("chatGroup:" + chatGroup);
            logger.info("chatGroup:" + chatGroup);
            logger.info("chatSender:" + chatSender);
            
            ChatController chatService = context.getProperty(CHAT_CONTROLLER_SERVICE).asControllerService(ChatController.class);
            chatService.sendMessageToFixedRoom(chatGroup, chatMessage, chatSender);

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

}
