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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.MessageBody;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.services.MessageBodyResolver;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "Chat"})
@CapabilityDescription("Given a serialized MessageWrapper as the content of a FlowFile, this processor will extract the require information to send CHAT messages to each of the participants. "
        + "This processor handles 3 different situations: messages to a pre-stablished chat group, messages to a group of recipients and 1-1 chat messages.\n"
        + "For pre-stablished chat groups, this processor will search any PhysicalAddress whose systemId is 'CHAT' and whose Address starts with 'GROUP:'. What follows after he prefix is considered to be"
        + "the name of the chat group. The processor will redirect a single FlowFile to 'permanent group' relationship for each indivitdual group name found.\n"
        + "For (dynamically specified) group chat messages, this processor will collect all the PhysicalAddress with systemId 'CHAT'. "
        + "NOTE that this means that this implementation doesn't allow multiple groups for a single message."
        + "The subject of the message is used as the name and subject of the group. The processor will redirect a single FlowFile to 'dynamic group'"
        + "no matter how many recipients the message has.\n"
        + "1-1 messages are treated as a sub-set of a group chat messages that only has 1 recipient."
        + "The processor will redirect a single FlowFile to 'direct message' on this case.")
public class UCSPrepareChat extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();
    
    public static final PropertyDescriptor SERVICE_ID = new PropertyDescriptor.Builder()
            .name("Service Id Name")
            .description("Only recipients with this serviceId will be used.")
            .required(true)
            .defaultValue("CHAT")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor SENDER_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Sender Attribute Name")
            .description("The name of the attribute where this processor will put the sender's username.")
            .required(true)
            .defaultValue("chat.sender.username")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor GROUP_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Group Attribute Name")
            .description("The name of the attribute where this processor will put the group name where this message should be sent.")
            .required(true)
            .defaultValue("chat.group.name")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor GROUP_SUFIX = new PropertyDescriptor.Builder()
            .name("Group Sufix")
            .description("Sufix to be appended to the group name.")
            .required(false)
            .defaultValue("@conference.socraticgrid.org")
            .expressionLanguageSupported(true)
            .addValidator(Validator.VALID)
            .build();
    
    public static final PropertyDescriptor GROUP_SUBJECT_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Group Subject Attribute Name")
            .description("The name of the attribute where this processor will put the group subject where this message should be sent.")
            .required(true)
            .defaultValue("chat.group.subject")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor PARTICIPANTS_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Participants Attribute Name")
            .description("The name of the attribute where this processor will put the participants to which this message should be sent.")
            .required(true)
            .defaultValue("chat.group.participants")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor MESSAGE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Message Attribute Name")
            .description("The name of the attribute where this processor will put the message (text) be sent.")
            .required(true)
            .defaultValue("chat.message")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor REFERENCE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Reference Attribute Name")
            .description("The name of the attribute where this processor will put the reference value for each of the recipients. The reference value is a unique value.")
            .required(true)
            .defaultValue("delivery.reference")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final Relationship REL_PERMANENT_GROUP = new Relationship.Builder().name("permanent group").description("").build();
    public static final Relationship REL_DYNAMIC_GROUP = new Relationship.Builder().name("dynamic group").description("").build();
    public static final Relationship REL_DIRECT_MESSAGE = new Relationship.Builder().name("direct message").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_PERMANENT_GROUP);
        relationships.add(REL_DYNAMIC_GROUP);
        relationships.add(REL_DIRECT_MESSAGE);
        relationships.add(REL_FAILURE);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        properties.add(SERVICE_ID);
        properties.add(SENDER_ATTRIBUTE_NAME);
        properties.add(GROUP_ATTRIBUTE_NAME);
        properties.add(GROUP_SUFIX);
        properties.add(GROUP_SUBJECT_ATTRIBUTE_NAME);
        properties.add(PARTICIPANTS_ATTRIBUTE_NAME);
        properties.add(MESSAGE_ATTRIBUTE_NAME);
        properties.add(REFERENCE_ATTRIBUTE_NAME);
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
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Error in message deserialization: " + errorHolder.get().getCause() != null ? errorHolder.get().getCause().getMessage() : errorHolder.get().getMessage(),
                    ExceptionType.InvalidMessage,
                    null,
                    null);
            return;
        }

        Message message = messageWrapperHolder.get().getMessage();

        //resolve the sender. We couldn't resolved it before because we needed
        //the specific serviceId.
        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);
        UserContactInfo uci = ucsService.resolveUserContactInfo(message.getHeader().getSender().getPhysicalAddress().getAddress());
        
        if (uci == null){
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Unknown User: " + message.getHeader().getSender().getPhysicalAddress().getAddress(),
                    ExceptionType.UnknownUser,
                    null,
                    null);
            return;
        }
        
        PhysicalAddress senderAddress = uci.getAddressesByType().get(context.getProperty(SERVICE_ID).getValue());
        
        if (senderAddress == null){
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Unknown Service "+ context.getProperty(SERVICE_ID).getValue() +" for  User: " + message.getHeader().getSender().getPhysicalAddress().getAddress(),
                    ExceptionType.UnknownService,
                    null,
                    null);
            return;
        }
        
        String senderUsernameAttribute = context.getProperty(SENDER_ATTRIBUTE_NAME).getValue();
        String messageAttribute = context.getProperty(MESSAGE_ATTRIBUTE_NAME).getValue();
        
        
        flowFile = session.putAttribute(flowFile, senderUsernameAttribute, senderAddress.getAddress());
        MessageBody body = MessageBodyResolver.resolveMessagePart(message, context.getProperty(SERVICE_ID).getValue(), null, null);
        flowFile = session.putAttribute(flowFile, messageAttribute, body.getContent());
        
        
        //extract all the CHAT addresses. Let's also separate the pre-stablished
        //groups (address starts with "GROUP:").
        Map<Boolean, List<Recipient>> chatRecipients = message.getHeader().getRecipientsList().stream()
                .filter(r -> r.getDeliveryAddress() != null && r.getDeliveryAddress().getPhysicalAddress() != null)
                .filter(r -> context.getProperty(SERVICE_ID).getValue().equals(r.getDeliveryAddress().getPhysicalAddress().getServiceId()))
                .collect(Collectors.groupingBy(r -> r.getDeliveryAddress().getPhysicalAddress().getAddress().startsWith("GROUP:")));

        Map<String, String> generatedReferences = new HashMap<>();
        
        if (chatRecipients.containsKey(Boolean.TRUE)) {
            generatedReferences.putAll(this.sendMessagesToPreStablishedGroups(context, session, flowFile, logger, chatRecipients.get(Boolean.TRUE)));
        }

        if (chatRecipients.containsKey(Boolean.FALSE)) {
            List<Recipient> recipients = chatRecipients.get(Boolean.FALSE);
            if (recipients.size() == 1) {
                generatedReferences.putAll(this.sendDirectMessage(context, session, flowFile, logger, message, recipients.get(0)));
            } else {
                generatedReferences.putAll(this.sendMessageToDynamicGroup(context, session, flowFile, logger, message, recipients));
            }
        }

        logger.debug("Removing original FlowFile");
        session.remove(flowFile);
        
        //keep track of the generated references
        //TODO: is this check correct/enough?
        if (message.getHeader().isReceiptNotification()){
            logger.debug("The message has ReceiptNotification flag enabled -> We are persisting its references.");
            generatedReferences.entrySet().stream().forEach((gr) -> {
                ucsService.saveMessageReference(message, gr.getKey(), gr.getValue());
            });
        } else {
            logger.debug("The message doesn't have ReceiptNotification flag enabled -> We are not persisting its references.");
        }
    }

    private Map<String, String> sendMessagesToPreStablishedGroups(final ProcessContext context, final ProcessSession session, final FlowFile flowFile, final ProcessorLog logger, List<Recipient> recipients) {

        String groupAttribute = context.getProperty(GROUP_ATTRIBUTE_NAME).getValue();
        String groupSufixAttribute = context.getProperty(GROUP_SUFIX).isSet()?context.getProperty(GROUP_SUFIX).evaluateAttributeExpressions(flowFile).getValue():"";
        String referenceAttributeName = context.getProperty(REFERENCE_ATTRIBUTE_NAME).getValue();
        
        Map<String, String> generatedReferences = new HashMap<>();
        
        recipients.stream()
                .forEach(r -> {
                    generatedReferences.put(r.getRecipientId(), UUID.randomUUID().toString());
                    
                    String address = r.getDeliveryAddress().getPhysicalAddress().getAddress();

                    FlowFile clone = session.clone(flowFile);
                    session.getProvenanceReporter().clone(flowFile, clone);

                    clone = session.putAttribute(clone, groupAttribute, convertAddressToGroupName(address, groupSufixAttribute));
                    clone = session.putAttribute(clone, referenceAttributeName, generatedReferences.get(r.getRecipientId()));
                    session.getProvenanceReporter().modifyAttributes(clone);

                    logger.debug("Routing message {} to {}.", new Object[]{clone, REL_PERMANENT_GROUP});
                    session.transfer(clone, REL_PERMANENT_GROUP);
                    session.getProvenanceReporter().route(clone, REL_PERMANENT_GROUP);
                });
        
        return generatedReferences;
    }
    
    private String convertAddressToGroupName(String address, String sufix){
        if (address.startsWith("GROUP:")){
            address = address.substring("GROUP:".length());
        }
        
        if (!address.endsWith(sufix)){
            address += sufix;
        }
        
        return address;
    }

    private Map<String, String> sendMessageToDynamicGroup(final ProcessContext context, final ProcessSession session, final FlowFile flowFile, final ProcessorLog logger, Message message, List<Recipient> recipients) {

        String groupAttribute = context.getProperty(GROUP_ATTRIBUTE_NAME).getValue();
        String groupSufixAttribute = context.getProperty(GROUP_SUFIX).isSet()?context.getProperty(GROUP_SUFIX).evaluateAttributeExpressions(flowFile).getValue():"";
        String groupSubjectAttribute = context.getProperty(GROUP_SUBJECT_ATTRIBUTE_NAME).getValue();
        String participantsAttribute = context.getProperty(PARTICIPANTS_ATTRIBUTE_NAME).getValue();
        String groupName = message.getHeader().getRelatedConversationId() != null ? message.getHeader().getRelatedConversationId() : UUID.randomUUID().toString();
        groupName+=groupSufixAttribute;
        String groupSubject = message.getHeader().getSubject() != null ? message.getHeader().getSubject() : "Group Message";
        
        String referenceAttributeName = context.getProperty(REFERENCE_ATTRIBUTE_NAME).getValue();
        
        Map<String, String> generatedReferences = new HashMap<>();
        
        //create references
        recipients.stream()
                .forEach(r -> generatedReferences.put(r.getRecipientId(), UUID.randomUUID().toString()));

        //convert the recipients insto a JSON array.
        JsonArray participants = new JsonArray();
        recipients.stream()
                .map(r -> {
                    JsonObject jo = new JsonObject();
                    jo.addProperty("participant", r.getDeliveryAddress().getPhysicalAddress().getAddress());
                    return jo;
                })
                .forEach(participants::add);

        FlowFile clone = session.clone(flowFile);
        session.getProvenanceReporter().clone(flowFile, clone);

        clone = session.putAttribute(clone, groupAttribute, groupName);
        clone = session.putAttribute(clone, groupSubjectAttribute, groupSubject);
        clone = session.putAttribute(clone, referenceAttributeName, generatedReferences.values().stream().collect(Collectors.joining(",")));
        clone = session.putAttribute(clone, participantsAttribute, new Gson().toJson(participants));
        session.getProvenanceReporter().modifyAttributes(clone);

        logger.debug("Routing message {} to {}.", new Object[]{clone, REL_DYNAMIC_GROUP});
        session.transfer(clone, REL_DYNAMIC_GROUP);
        session.getProvenanceReporter().route(clone, REL_DYNAMIC_GROUP);
        
        return generatedReferences;
    }
    
    private Map<String, String> sendDirectMessage(final ProcessContext context, final ProcessSession session, final FlowFile flowFile, final ProcessorLog logger, Message message, Recipient recipient) {

        String groupAttribute = context.getProperty(GROUP_ATTRIBUTE_NAME).getValue();
        String groupSufixAttribute = context.getProperty(GROUP_SUFIX).isSet()?context.getProperty(GROUP_SUFIX).evaluateAttributeExpressions(flowFile).getValue():"";
        String groupSubjectAttribute = context.getProperty(GROUP_SUBJECT_ATTRIBUTE_NAME).getValue();
        String participantsAttribute = context.getProperty(PARTICIPANTS_ATTRIBUTE_NAME).getValue();
        String groupName = message.getHeader().getRelatedConversationId() != null ? message.getHeader().getRelatedConversationId() : UUID.randomUUID().toString();
        groupName+=groupSufixAttribute;
        String groupSubject = message.getHeader().getSubject() != null ? message.getHeader().getSubject() : "Private Message";
        
        String referenceAttributeName = context.getProperty(REFERENCE_ATTRIBUTE_NAME).getValue();
        
        Map<String, String> generatedReferences = new HashMap<>();
        
        //create references
        generatedReferences.put(recipient.getRecipientId(), UUID.randomUUID().toString());

        FlowFile clone = session.clone(flowFile);
        session.getProvenanceReporter().clone(flowFile, clone);

        clone = session.putAttribute(clone, groupAttribute, groupName);
        clone = session.putAttribute(clone, groupSubjectAttribute, groupSubject);
        clone = session.putAttribute(clone, referenceAttributeName, generatedReferences.values().stream().collect(Collectors.joining(",")));
        clone = session.putAttribute(clone, participantsAttribute, recipient.getDeliveryAddress().getPhysicalAddress().getAddress());
        session.getProvenanceReporter().modifyAttributes(clone);

        logger.debug("Routing message {} to {}.", new Object[]{clone, REL_DIRECT_MESSAGE});
        session.transfer(clone, REL_DIRECT_MESSAGE);
        session.getProvenanceReporter().route(clone, REL_DIRECT_MESSAGE);
        
        return generatedReferences;
    }
}
