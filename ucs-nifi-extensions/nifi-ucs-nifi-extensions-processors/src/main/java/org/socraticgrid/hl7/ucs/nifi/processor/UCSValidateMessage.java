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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.SupportsBatching;
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
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

/**
 *
 * @author esteban
 */
@EventDriven
@SupportsBatching
@Tags({"UCS", "message"})
@CapabilityDescription("This processor validates an incoming Message in the following by"
        + "checking that it doesn't contain duplicated ids. It also creates "
        + "unique ids for the recipients of the message if they are empty."
        + "If the incoming message is an AlertMessage, this processor"
        + "changes its alertStatus to 'Pending'")
public class UCSValidateMessage extends AbstractProcessor {

    public static final String VALID_ATTRIBUTE_KEY = "ucs.valid";

    public static final String FAIL = "Fail";
    public static final String UPDATE_ID = "Update Id";

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final PropertyDescriptor ON_DUPLICATED_MESSAGE_ID = new PropertyDescriptor.Builder()
            .name("On Duplicated Message Id")
            .description("Specifies the way this processor treats duplicated message ids. Duplicated message ids means both: duplicated ids in a single nested message or a message with an id that is already present in UCS.")
            .required(true).allowableValues(FAIL, UPDATE_ID)
            .defaultValue(FAIL).build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        properties.add(ON_DUPLICATED_MESSAGE_ID);
        this.properties = Collections.unmodifiableList(properties);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships.get();
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        final List<FlowFile> flowFiles = session.get(1);
        if (flowFiles.isEmpty()) {
            return;
        }

        final ProcessorLog logger = getLogger();

        for (FlowFile flowFile : flowFiles) {
            final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
            final ObjectHolder<MessageWrapper> messageWrapperHolder = new ObjectHolder<>(null);

            session.read(flowFile, (final InputStream rawIn) -> {
                try {
                    messageWrapperHolder.set(MessageSerializer.deserializeMessageWrapper(rawIn));
                } catch (MessageSerializationException ex) {
                    errorHolder.set(new RuntimeException("Error deserializing FlowFile content into a MessageWrapper instance. Routing to FAILURE", ex));
                }
            });

            if (errorHolder.get() != null) {
                logger.error(errorHolder.get().getMessage(), errorHolder.get().getCause());
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Error deserializing FlowFile: " + errorHolder.get().getCause(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                continue;
            }

            final Message message = messageWrapperHolder.get().getMessage();

            //Check if all the recipients have an id. If don't, create a new one.
            for (Recipient r : message.getHeader().getRecipientsList()) {
                //if the recipient doesn't have an id, then create one
                if (r.getRecipientId() == null) {
                    r.setRecipientId(UUID.randomUUID().toString());
                }

            }

            UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

            Set<Message> messages = UCSValidateMessage.collectNestedMessages(message);
            messages.add(message);

            //Assert that the conversation Id of the message is known in UCS.
            if (!StringUtils.isEmpty(message.getHeader().getRelatedConversationId()) && !ucsService.getConversationById(message.getHeader().getRelatedConversationId()).isPresent()) {
                logger.debug("Failing because of unknown conversation id:{} ", new Object[]{message.getHeader().getRelatedConversationId()});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Unknown Conversation Id: " + message.getHeader().getRelatedConversationId(),
                        ExceptionType.InvalidConversation,
                        null,
                        null);
                continue;
            }

            //Check for duplicated message ids. 
            //first check for duplicated ids inside the same message.
            //This is no so easy to implement beacuse messages could have a 
            //null id.
            Set<Message> duplicatedMessages = messages.stream()
                    .filter(m -> m.getHeader().getMessageId() != null)
                    .collect(Collectors.groupingBy(m -> m.getHeader().getMessageId())).entrySet().stream()
                    .filter(e -> e.getValue().size() > 1)
                    .flatMap(e -> e.getValue().stream())
                    .collect(Collectors.toSet());

            //Also include in duplicatedIds duplicated messages in UCS 
            messages.stream()
                    .filter(m -> m.getHeader().getMessageId() != null)
                    .filter(m -> ucsService.getMessageById(m.getHeader().getMessageId()).isPresent())
                    .forEach(duplicatedMessages::add);

            //process duplicated ids.
            if (!duplicatedMessages.isEmpty()) {
                logger.debug("Duplicated Message Ids found: {}", new Object[]{duplicatedMessages.stream().map(m -> m.getHeader().getMessageId()).collect(Collectors.joining(","))});

                if (context.getProperty(ON_DUPLICATED_MESSAGE_ID).getValue()
                        .equalsIgnoreCase(UPDATE_ID)) {
                    logger.debug("Updating Messages Ids.");
                    duplicatedMessages.forEach(m -> m.getHeader().setMessageId(UUID.randomUUID().toString()));
                } else {
                    logger.debug("Failing because of duplicated messages ids");
                    UCSCreateException.routeFlowFileToException(
                            context,
                            session,
                            logger,
                            flowFile,
                            REL_FAILURE,
                            null,
                            "Duplicated Message Ids found: " + duplicatedMessages.stream().map(m -> m.getHeader().getMessageId()).collect(Collectors.joining(",")),
                            ExceptionType.InvalidMessage,
                            null,
                            null);
                    continue;
                }
            }

            //If the Message is an AlertMessage, then change its alertStatus
            //to 'Pending'
            if (message instanceof AlertMessage) {
                ((AlertMessage) message).getHeader().setAlertStatus(AlertStatus.Pending);
            }

            //Write the message back to the FlowFile (in case it has been modified)
            flowFile = session.putAttribute(flowFile, VALID_ATTRIBUTE_KEY, "true");
            session.getProvenanceReporter().modifyAttributes(flowFile);

            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(OutputStream out) throws IOException {
                    try {
                        out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(message)).getBytes());
                    } catch (MessageSerializationException ex) {
                        errorHolder.set(ex);
                    }
                    out.flush();
                }
            });

            if (errorHolder.get() != null) {
                logger.error(errorHolder.get().getMessage(), errorHolder.get().getCause());
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Error deserializing FlowFile: " + errorHolder.get().getCause(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                continue;
            }

            logger.debug("Message validated. Routing message {} to {}.", new Object[]{flowFile, REL_SUCCESS.getName()});
            session.transfer(flowFile, REL_SUCCESS);
            session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
        }
    }

    protected static Set<Message> collectNestedMessages(Message message) {
        Set<Message> messages = new HashSet<>();

        messages.addAll(Optional.ofNullable(message.getHeader().getOnFailureToReachAll()).orElse(Collections.EMPTY_LIST));
        messages.addAll(Optional.ofNullable(message.getHeader().getOnFailureToReachAny()).orElse(Collections.EMPTY_LIST));
        messages.addAll(Optional.ofNullable(message.getHeader().getOnNoResponseAll()).orElse(Collections.EMPTY_LIST));
        messages.addAll(Optional.ofNullable(message.getHeader().getOnNoResponseAny()).orElse(Collections.EMPTY_LIST));

        return messages;
    }

}
