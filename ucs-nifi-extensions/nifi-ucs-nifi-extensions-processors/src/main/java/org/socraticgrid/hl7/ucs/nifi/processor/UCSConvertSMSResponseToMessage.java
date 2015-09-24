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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.annotation.behavior.EventDriven;
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
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageBody;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.SimpleMessage;
import org.socraticgrid.hl7.services.uc.model.SimpleMessageHeader;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "SMS", "Message"})
@CapabilityDescription("Converts an SMS response into a UCS Message. This processor"
        + "uses the incoming reference in the SMS message to retrieve the "
        + "message that originated this response.")
public class UCSConvertSMSResponseToMessage extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();
    public static final Relationship REL_NO_MATCH = new Relationship.Builder().name("no match").description("We don't have any message or recipient matching the given reference id.").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_NO_MATCH);
        relationships.add(REL_FAILURE);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
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

        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

        for (FlowFile flowFile : flowFiles) {
            final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
            final ObjectHolder<JsonObject> jsonObjectHolder = new ObjectHolder<>(null);

            session.read(flowFile, (final InputStream rawIn) -> {
                try {
                    JsonObject jsonObject = new JsonParser().parse(new InputStreamReader(rawIn)).getAsJsonObject();
                    jsonObjectHolder.set(jsonObject);
                } catch (Exception ex) {
                    errorHolder.set(new RuntimeException("Error deserializing SMS response from FlowFile content. Routing to FAILURE", ex));
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
                        "Error deserializing incoming SMS message: "+errorHolder.get().getCause().getMessage(),
                        ExceptionType.InvalidInput,
                        null,
                        null);
                continue;
            }

            final JsonObject smsResponse = jsonObjectHolder.get();

            JsonElement referenceAttribute = smsResponse.get("Reference");
            if (referenceAttribute == null) {
                logger.debug("SMS Response doesn't contain any Reference attribute. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_NO_MATCH});
                session.transfer(flowFile, REL_NO_MATCH);
                session.getProvenanceReporter().route(flowFile, REL_NO_MATCH);
                continue;
            }

            String reference = referenceAttribute.getAsString();
            if (reference == null || reference.trim().isEmpty()) {
                logger.debug("SMS Response's Reference attribute is empty. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_NO_MATCH});
                session.transfer(flowFile, REL_NO_MATCH);
                session.getProvenanceReporter().route(flowFile, REL_NO_MATCH);
                continue;
            }

            String responseContent = smsResponse.get("Message") != null ? smsResponse.get("Message").getAsString() : "<No Response>";

            //Get the message that originated this response.
            Optional<Message> messageByReference = ucsService.getMessageByReference(reference);
            Optional<String> recipientIdByReference = ucsService.getRecipientIdByReference(reference);

            if (!messageByReference.isPresent() || !recipientIdByReference.isPresent()) {
                logger.debug("No message or recipient matching the reference id '{}'. Routing FlowFile {} to {}.", new Object[]{reference, flowFile, REL_NO_MATCH});
                session.transfer(flowFile, REL_NO_MATCH);
                session.getProvenanceReporter().route(flowFile, REL_NO_MATCH);
                continue;
            }

            final Message message = messageByReference.get();

            Optional<Recipient> recipient = message.getHeader().getRecipientsList().stream()
                    .filter(r -> r.getRecipientId().equals(recipientIdByReference.get()))
                    .findFirst();

            if (!recipient.isPresent()) {
                //Strange situation: the reference id makes reference to a recipient
                //that doesn't belong to the message it also makes reference... 
                //We should never reach this point... But you never know.
                logger.debug("The recipient {} referenced by the reference id {} doesn't match the message {} referenced by the same reference id. Routing FlowFile {} to {}.", new Object[]{recipientIdByReference.get(), reference, message.getHeader().getMessageId(), flowFile, REL_FAILURE});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "The recipient '"+recipientIdByReference.get()+"' referenced by the reference id '"+reference+"' doesn't match the message '"+message.getHeader().getMessageId()+"' referenced by the same reference id. Routing FlowFile '"+flowFile+"' to '"+REL_FAILURE+"'.",
                        ExceptionType.InvalidInput,
                        null,
                        null);
                continue;
            }

            //Create a new message
            //The sender is the original recipient
            DeliveryAddress messageSender = recipient.get().getDeliveryAddress();
            //The recipient is the original sender
            Recipient messageRecipient = new Recipient();
            messageRecipient.setDeliveryAddress(message.getHeader().getSender());

            final Message responseMessage = this.createSimpleMessage(messageSender, messageRecipient, message.getHeader().getRelatedConversationId(), message.getHeader().getRelatedMessageId(),responseContent, responseContent, "text/plain", false, Optional.empty(), Optional.empty());

            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    try {
                        out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(responseMessage)).getBytes());
                    } catch (MessageSerializationException ex) {
                        //should never happen
                        errorHolder.set(ex);
                    }
                }
            });

            if (errorHolder.get() != null) {
                logger.error(errorHolder.get().getMessage(), errorHolder.get());
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Error serializing generated message: "+errorHolder.get().getMessage(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                continue;
            }

            session.getProvenanceReporter().modifyContent(flowFile);
            
            //persist the new message in UCSController
            ucsService.saveMessage(responseMessage);

            //route FlowFile to SUCCESS
            logger.debug("SMS Response converted into a Message and set as FlowFile content. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_SUCCESS});
            session.transfer(flowFile, REL_SUCCESS);
            session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
        }
    }

    /**
     * TODO: move this into a Builder class.
     *
     * @param sender
     * @param recipient
     * @param subject
     * @param content
     * @param contentType
     * @param receiptNotification
     * @param createdDate
     * @param timeout
     * @return
     */
    private Message createSimpleMessage(DeliveryAddress sender, Recipient recipient, String conversationId, String relatedMessageId, String subject, String content, String contentType, boolean receiptNotification, Optional<Date> createdDate, Optional<Integer> timeout) {

        Set<Recipient> recipients = new HashSet<>();
        recipients.add(recipient);

        String messageId = UUID.randomUUID().toString();
        SimpleMessageHeader header = new SimpleMessageHeader();
        header.setMessageId(messageId);
        header.setCreated(createdDate.orElse(new Date()));
        header.setTimeout(timeout.orElse(-1));
        header.setRelatedConversationId(conversationId);
        header.setRelatedMessageId(relatedMessageId);
        header.setSender(sender);
        header.setSubject(subject);
        header.setRecipientsList(recipients);
        header.setReceiptNotification(receiptNotification);

        MessageBody body = new MessageBody();
        body.setContent(content);
        body.setType(contentType);

        SimpleMessage message = new SimpleMessage(header);
        message.setParts(new MessageBody[]{body});
        
        return message;
    }
}
