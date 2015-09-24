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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.io.IOException;
import java.io.OutputStream;
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
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatController;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessage;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessageSerializer;

@TriggerWhenEmpty
@Tags({"chat", "message"})
@CapabilityDescription("Listens for chat messages of a particulat ChatController and starts a new FlowFile for each of them.")
public class ReceiveChatMessage extends AbstractProcessor {

    public static final PropertyDescriptor CHAT_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("CHAT Controller Service")
            .description("The CHAT Controller Service that this Processor uses to send messages.")
            .identifiesControllerService(ChatController.class)
            .required(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("Each message found in the ChatController will be sent through this relationship")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("An error happened when processing the messages.")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(CHAT_CONTROLLER_SERVICE);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
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
    public void onTrigger(final ProcessContext context,
            final ProcessSession session) {
        final ProcessorLog logger = getLogger();
        try {

            ChatController chatService = context.getProperty(CHAT_CONTROLLER_SERVICE).asControllerService(ChatController.class);

            Set<ChatMessage> messages = chatService.consumeMessages();
            logger.debug("{} chat messages found.", new Object[]{messages.size()});
            for (ChatMessage message : messages) {
                final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);

                FlowFile flowFile = session.create();
                session.getProvenanceReporter().create(flowFile);
                flowFile = session.write(flowFile, new OutputStreamCallback() {
                    @Override
                    public void process(final OutputStream out) throws IOException {
                        try {
                            out.write(ChatMessageSerializer.serializeChatMessage(message).getBytes());
                        } catch (MessageSerializationException ex) {
                            //should never happen
                            errorHolder.set(ex);
                        }
                    }
                });
                session.getProvenanceReporter().modifyContent(flowFile);

                if (errorHolder.get() != null) {
                    logger.error(errorHolder.get().getMessage(), errorHolder.get());
                    UCSCreateException.routeFlowFileToException(
                            context,
                            session,
                            logger,
                            flowFile,
                            REL_FAILURE,
                            null,
                            "Error serializing chat message: " + errorHolder.get().getMessage(),
                            ExceptionType.InvalidInput,
                            null,
                            null);
                    return;
                }

                logger.debug("Message received and serialized. Routing message {} to {}.", new Object[]{flowFile, REL_SUCCESS});
                session.transfer(flowFile, REL_SUCCESS);
                session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
            }

        } catch (Exception e) {
            logger.error("Error getting messages from ChatController. Yielding...", e);
        } finally {
            context.yield();
        }

    }

}
