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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
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
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.SimpleMessage;
import org.socraticgrid.hl7.services.uc.model.SimpleMessageHeader;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessage;
import org.socraticgrid.hl7.ucs.nifi.controller.chat.ChatMessageSerializer;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "SMS", "Message"})
@CapabilityDescription("Converts a Chat response into a UCS Message. This processor"
        + "uses the roomId from the incoming Chat message to retrieve the "
        + "message that originated this response (if any).")
public class UCSConvertChatResponseToMessage extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();
    
    public static final PropertyDescriptor GROUP_SUFIX = new PropertyDescriptor.Builder()
            .name("Group Sufix")
            .description("Sufix to be appended to the group name.")
            .required(false)
            .defaultValue("@conference.socraticgrid.org")
            .expressionLanguageSupported(true)
            .addValidator(Validator.VALID)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();
    public static final Relationship REL_NO_MATCH = new Relationship.Builder().name("no match").description("We couldn't identify the message that originated this response.").build();

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
        properties.add(GROUP_SUFIX);
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
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final ProcessorLog logger = getLogger();

        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

        final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
        final ObjectHolder<ChatMessage> messageObjectHolder = new ObjectHolder<>(null);

        session.read(flowFile, (final InputStream rawIn) -> {
            try {
                ChatMessage m = ChatMessageSerializer.deserializeChatMessage(rawIn);
                messageObjectHolder.set(m);
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
                            "Error deserializing chat message: " + errorHolder.get().getCause(),
                            ExceptionType.InvalidInput,
                            null,
                            null);
            return;
        }
        
        ChatMessage chatMessage = messageObjectHolder.get();
        
        logger.debug("ChatMessage received: {}.", new Object[]{chatMessage});
        
        //Finding the message that originated this response turned out to be 
        //really hard. We tried to use XMPP's Thread field to keep references
        //between outgoing and incoming messages, but the XMPP clients we tried
        //didn't send the Thread field in their messages.
        //The best solution, so far is this:
        //1.- Get the group of the incoming message.
        //2.- If there is a convertsation going on whose conversationID is equals
        //to the group of the incoming message, then add this conversationID
        //as the conversationID of the incoming message. We can't tell the 
        //specific recipients, but at least this is something. In this case,
        //mark this message as a response.
        //3.- If there is no conversation going on whose conversationID is equals
        //to the group of the incoming message, then don't set any conversationID
        //to the incoming message and mark it as a regular message instead of a
        //response.

        String conversationId = ucsService.isKnownConversation(chatMessage.getRoomId())? chatMessage.getRoomId() : null;
        logger.debug("Related conversation id for roomId {}: {}.", new Object[]{chatMessage.getRoomId(), conversationId});
        
        //until we standarize things a little bit more, we need to double-check
        //the conversationId with and without GROUP_SUFIX
        if (conversationId == null && chatMessage.getRoomId() != null){
            String groupSufixAttribute = context.getProperty(GROUP_SUFIX).isSet()?context.getProperty(GROUP_SUFIX).evaluateAttributeExpressions(flowFile).getValue():null;
            if (groupSufixAttribute != null && chatMessage.getRoomId().endsWith(groupSufixAttribute)){
                String roomId = chatMessage.getRoomId().substring(0, chatMessage.getRoomId().length()-groupSufixAttribute.length());
                conversationId = ucsService.isKnownConversation(roomId)? chatMessage.getRoomId() : null;
                logger.debug("Related conversation id for roomId {}: {}.", new Object[]{roomId, conversationId});
            } else if (groupSufixAttribute != null && !chatMessage.getRoomId().endsWith(groupSufixAttribute)){
                String roomId = chatMessage.getRoomId()+groupSufixAttribute;
                conversationId = ucsService.isKnownConversation(roomId)? chatMessage.getRoomId() : null;
                logger.debug("Related conversation id for roomId {}: {}.", new Object[]{roomId, conversationId});
            }
        }
        
        DeliveryAddress sender = new DeliveryAddress();
        sender.setAddress(new PhysicalAddress("CHAT", chatMessage.getSenderId()));
        
        //convert the ChatMessage into a UCSMessage
        final Message ucsMessage = this.createSimpleMessage(chatMessage.getId(), sender, Optional.empty(), Optional.ofNullable(conversationId), Optional.empty(), Optional.empty(), chatMessage.getMessage(), "text/plain", true, Optional.empty(), Optional.empty());
        
        flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    try {
                        out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(ucsMessage)).getBytes());
                    } catch (MessageSerializationException ex) {
                        //should never happen
                        errorHolder.set(ex);
                    }
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
                            "Error serializing ucs message: " + errorHolder.get().getCause(),
                            ExceptionType.InvalidMessage,
                            null,
                            null);
                return;
            }
            
            session.getProvenanceReporter().modifyContent(flowFile);
            
            //persist the new message in UCSController
            ucsService.saveMessage(ucsMessage);

            //If the generated message has a conversation id, route FlowFile 
            //to REL_SUCCESS, otherwise route it through REL_NO_MATCH.
            Relationship target = conversationId != null ? REL_SUCCESS : REL_NO_MATCH;
            logger.debug("Chat meessage converted into a UCS Message and set as FlowFile content. Routing FlowFile {} to {}.", new Object[]{flowFile, target});
            session.transfer(flowFile, target);
            session.getProvenanceReporter().route(flowFile, target);

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
    private Message createSimpleMessage(String messageId, DeliveryAddress sender, Optional<Recipient> recipient, Optional<String> conversationId, Optional<String> relatedMessageId, Optional<String> subject, String content, String contentType, boolean receiptNotification, Optional<Date> createdDate, Optional<Integer> timeout) {

        Set<Recipient> recipients = new HashSet<>();
        
        if (recipient.isPresent()){
            recipients.add(recipient.get());
        }
        
        SimpleMessageHeader header = new SimpleMessageHeader();
        header.setMessageId(messageId);
        header.setCreated(createdDate.orElse(new Date()));
        header.setTimeout(timeout.orElse(-1));
        header.setRelatedConversationId(conversationId.orElse(null));
        header.setRelatedMessageId(relatedMessageId.orElse(null));
        header.setSender(sender);
        header.setSubject(subject.orElse(null));
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
