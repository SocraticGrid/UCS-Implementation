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
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageHeader;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.processor.model.MessageWithUnreachableHandlers;

/**
 *
 * @author esteban
 */
@TriggerWhenEmpty
@Tags({"message"})
@CapabilityDescription("Consumes any existing MessageWithUnreachableHandlers from UCSControllerService.consumeMessagesWithUnreachableHandlers() and processes them."
        + "For each MessageWithUnreachableHandlers, this processor extracts the corresponding Message and inserts all of the Messages in onFailureToReachAll or onFailureToReachAny"
        + "collections according to the reason of unreachableness.")
public class UCSProcessMessagesWithUnreachableHandlers extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final Relationship REL_ALL_HANDLERS = new Relationship.Builder()
            .name("all handlers")
            .description("If the reason of the MessageWithUnreachableHandlers being processed is ALL_HANDLERS, a new FlowFile "
                    + "for each of the Messages in MessageWithUnreachableHandlers.message.onFailureToReachAll will be created "
                    + "and reouted through this relationship.")
            .build();

    public static final Relationship REL_SOME_HANDLERS = new Relationship.Builder()
            .name("some handlers")
            .description("If the reason of the MessageWithUnreachableHandlers being processed is SOME_HANDLERS, a new FlowFile "
                    + "for each of the Messages in MessageWithUnreachableHandlers.message.onFailureToReachAny will be created "
                    + "and reouted through this relationship.")
            .build();

    public static final Relationship REL_NO_ALTERNATIVE_MESSAGE = new Relationship.Builder()
            .name("no alternative")
            .description("If the MessageWithUnreachableHandlers has a reason of ALL_HANDLERS but the message.onFailureToReachAll"
                    + "is empty OR if it has a reason of SOME_HANDLERS but the message.onFailureToReachAny is empty; a new FlowFile "
                    + "is created and transfered to this relationship.")
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
        properties.add(UCS_CONTROLLER_SERVICE);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_ALL_HANDLERS);
        relationships.add(REL_SOME_HANDLERS);
        relationships.add(REL_NO_ALTERNATIVE_MESSAGE);
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

        final ProcessorLog logger = getLogger();

        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);
        Set<MessageWithUnreachableHandlers> messagesWithUnreachableHandlers = ucsService.consumeMessagesWithUnreachableHandlers();

        try{
            logger.debug("{} Messages with unreachable handlers found.", new Object[]{messagesWithUnreachableHandlers.size()});
            for (MessageWithUnreachableHandlers messageWithUnreachableHandler : messagesWithUnreachableHandlers) {
                logger.debug("MessageWithUnreachableHandlers has a reason of {}.", new Object[]{messageWithUnreachableHandler.getReason()});

                MessageHeader header = messageWithUnreachableHandler.getMessage().getHeader();

                List<Message> messages = Collections.EMPTY_LIST;
                String messagesName = "";
                Relationship targetRelationship = null;
                switch (messageWithUnreachableHandler.getReason()) {
                    case ALL_HANDLERS:
                        messagesName = "onFailureToReachAll";
                        messages = header.getOnFailureToReachAll() == null ? Collections.EMPTY_LIST : header.getOnFailureToReachAll();
                        targetRelationship = REL_ALL_HANDLERS;
                        break;
                    case SOME_HANDLERS:
                        messagesName = "onFailureToReachAny";
                        messages = header.getOnFailureToReachAny() == null ? Collections.EMPTY_LIST : header.getOnFailureToReachAny();
                        targetRelationship = REL_SOME_HANDLERS;
                        break;
                }

                logger.debug("Message has {} elements in its {} collection.",
                        new Object[]{messages, messagesName}
                );

                if (messages.isEmpty()) {
                    FlowFile flowFile = session.create();
                    flowFile = session.putAttribute(flowFile, "reason.isEmpty", messagesName);
                    session.getProvenanceReporter().route(flowFile, REL_NO_ALTERNATIVE_MESSAGE);
                    session.transfer(flowFile, REL_NO_ALTERNATIVE_MESSAGE);
                    logger.info("Couldn't reach Message recipients. Routing {} to {}", new Object[]{flowFile, REL_NO_ALTERNATIVE_MESSAGE});
                    continue;
                }

                final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
                for (Message message : messages) {
                    FlowFile flowFile = session.create();
                    session.getProvenanceReporter().create(flowFile);
                    flowFile = session.write(flowFile, new OutputStreamCallback() {
                        @Override
                        public void process(final OutputStream out) throws IOException {
                            try {
                                out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(message)).getBytes());
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
                                ExceptionType.InvalidMessage,
                                null,
                                null);
                        return;
                    }

                    logger.debug("Message from {} serialized into FlowFile {} and transfered to {}.", new Object[]{messagesName, flowFile, targetRelationship});
                    session.transfer(flowFile, targetRelationship);
                    session.getProvenanceReporter().route(flowFile, targetRelationship);
                }

            }
        } catch (Exception e) {
            logger.error("Error getting messages from ChatController. Yielding...", e);
        } finally {
            context.yield();
        }
    }

}
