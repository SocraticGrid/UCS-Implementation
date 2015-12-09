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
import org.socraticgrid.hl7.services.uc.model.Recipient;
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
@Tags({"UCS", "SMS"})
@CapabilityDescription("Given a serialized MessageWrapper as the content of a FlowFile, this processor will extract the require information to send an SMS to each of the participants. "
        + "This processor generates as many outgoing FlowFiles as SMS Recipients the original message has.")
public class UCSPrepareSMS extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();
    
    public static final PropertyDescriptor SMS_SERVICE_ID = new PropertyDescriptor.Builder()
            .name("SMS Service Id Name")
            .description("Only recipients with this serviceId will be used.")
            .required(true)
            .defaultValue("SMS")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PHONE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Phone Attribute Name")
            .description("The name of the attribute where this processor will put the phone number of each of the recipients")
            .required(true)
            .defaultValue("sms.phone")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor TEXT_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Text Attribute Name")
            .description("The name of the attribute where this processor will put the text for each of the recipients")
            .required(true)
            .defaultValue("sms.text")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor REFERENCE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Reference Attribute Name")
            .description("The name of the attribute where this processor will put the reference value for each of the recipients. The reference value is a unique value.")
            .required(true)
            .defaultValue("delivery.reference")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

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
        properties.add(SMS_SERVICE_ID);
        properties.add(PHONE_ATTRIBUTE_NAME);
        properties.add(TEXT_ATTRIBUTE_NAME);
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
                        "Error deserializing FlowFile: " + errorHolder.get().getCause().getMessage(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                continue;
            }

            Message message = messageWrapperHolder.get().getMessage();

            //we currently don't support addresses other than physical
            Map<String, List<Recipient>> recipientsByType = message.getHeader().getRecipientsList().stream()
                    .collect(Collectors.groupingBy(r -> r.getDeliveryAddress().getPhysicalAddress() == null ? "NO" : "YES"));

            if (!recipientsByType.getOrDefault("NO", new ArrayList<>()).isEmpty()) {
                String unsupportedAddressTypes = recipientsByType.get("NO").stream()
                        .map(r -> r.getDeliveryAddress().getAddressType().name())
                        .collect(Collectors.toSet()).stream()
                        .collect(Collectors.joining(", "));

                logger.error("Unsupported Address of types [{}]", new Object[]{unsupportedAddressTypes});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Unsupported Address of types [" + unsupportedAddressTypes + "]",
                        ExceptionType.InvalidAddress,
                        null,
                        null);
                continue;
            }

            List<Recipient> recipients = recipientsByType.getOrDefault("YES", new ArrayList<>()).stream()
                    .filter(r -> context.getProperty(SMS_SERVICE_ID).getValue().equals(r.getDeliveryAddress().getPhysicalAddress().getServiceId()))
                    .collect(Collectors.toList());

            if (recipients.isEmpty()) {
                //We shouldn't reach this point ideally. If somebody routed a message
                //to this processor, it must be because we at least have 1 SMS address.
                logger.error("The message didn't contain any Address with ServiceId '{}'.", new Object[]{context.getProperty(SMS_SERVICE_ID).getValue()});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Error processing Message: The message didn't contain any Address with ServiceId '"+context.getProperty(SMS_SERVICE_ID).getValue()+"'.",
                        ExceptionType.InvalidContext,
                        null,
                        null);
                continue;
            }

            Map<String, String> generatedReferences = new HashMap<>();
            
            String phoneAttributeName = context.getProperty(PHONE_ATTRIBUTE_NAME).getValue();
            String textAttributeName = context.getProperty(TEXT_ATTRIBUTE_NAME).getValue();
            String referenceAttributeName = context.getProperty(REFERENCE_ATTRIBUTE_NAME).getValue();

            Recipient firstRecpient = recipients.remove(0);
            MessageBody body = MessageBodyResolver.resolveMessagePart(message, context.getProperty(SMS_SERVICE_ID).getValue(), firstRecpient.getRecipientId(), firstRecpient.getDeliveryAddress().getPhysicalAddress().getAddress());
            String text = body.getContent();
            
            for (Recipient r : recipients) {
            	
            	MessageBody msgBody = MessageBodyResolver.resolveMessagePart(message, context.getProperty(SMS_SERVICE_ID).getValue(), r.getRecipientId(), r.getDeliveryAddress().getPhysicalAddress().getAddress());
                String content = msgBody.getContent();
                
                generatedReferences.put(r.getRecipientId(), UUID.randomUUID().toString());
                String phone = r.getDeliveryAddress().getPhysicalAddress().getAddress();

                FlowFile clone = session.clone(flowFile);

                Map<String, String> attributes = new HashMap<>();
                attributes.put(phoneAttributeName, phone);
                attributes.put(textAttributeName, content);
                attributes.put(referenceAttributeName, generatedReferences.get(r.getRecipientId()));

                clone = session.putAllAttributes(clone, attributes);
                session.getProvenanceReporter().modifyAttributes(flowFile);
                
                session.transfer(clone, REL_SUCCESS);
                session.getProvenanceReporter().route(clone, REL_SUCCESS);
                logger.debug("FlowFile cloned into {} and routed through {}", new Object[]{clone, REL_SUCCESS});
            }

            generatedReferences.put(firstRecpient.getRecipientId(), UUID.randomUUID().toString());
            String phone = firstRecpient.getDeliveryAddress().getPhysicalAddress().getAddress();

            Map<String, String> attributes = new HashMap<>();
            attributes.put(phoneAttributeName, phone);
            attributes.put(textAttributeName, text);
            attributes.put(referenceAttributeName, generatedReferences.get(firstRecpient.getRecipientId()));

            flowFile = session.putAllAttributes(flowFile, attributes);
            session.getProvenanceReporter().modifyAttributes(flowFile);

            session.transfer(flowFile, REL_SUCCESS);
            session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
            logger.debug("Original FlowFile routed through {}", new Object[]{flowFile, REL_SUCCESS});
            
            //if we expect a response from these SMS messages we need to 
            //keep track of the generated references
            //TODO: is this check correct/enough?
            UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);
            if (message.getHeader().isReceiptNotification()){
                logger.debug("The message has ReceiptNotification flag enabled -> We are persisting it.");
                generatedReferences.entrySet().stream().forEach((gr) -> {
                    ucsService.saveMessageReference(message, gr.getKey(), gr.getValue());
                });
            } else {
                logger.debug("The message doesn't have ReceiptNotification flag enabled -> We are not persisting it.");
            }

        }
    }
}
