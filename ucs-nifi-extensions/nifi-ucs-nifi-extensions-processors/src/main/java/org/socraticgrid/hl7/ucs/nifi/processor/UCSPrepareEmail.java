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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

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
 * @author BasitAzeem
 *
 */
@EventDriven
@Tags({"UCS", "EMAIL"})
@CapabilityDescription("Given a serialized MessageWrapper as the content of a FlowFile, this processor will extract the require information to send an Email to each of the participants. "
        + "This processor generates single outgoing FlowFiles for each original message.")
public class UCSPrepareEmail extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description(
                    "The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class).required(true)
            .build();

    public static final PropertyDescriptor EMAIL_SERVICE_ID = new PropertyDescriptor.Builder()
            .name("Email Service Id Name")
            .description("Only recipients with this serviceId will be used.")
            .required(true).defaultValue("EMAIL")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

    public static final PropertyDescriptor EMAIL_SUBJECT_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Email Subject attribute name")
            .description("Attribute name of the Email Subject to be sent")
            .required(true)
            .defaultValue("email.subject")
            .addValidator(
                    StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor TO_EMAIL_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("To Email attribute name")
            .description(
                    "Attribute name of Recepients to which email to be sent")
            .required(true)
            .defaultValue("email.to")
            .addValidator(
                    StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor FROM_EMAIL_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("From Email attribute name")
            .description("Attribute name of sender email address")
            .required(true)
            .defaultValue("email.from")
            .addValidator(
                    StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor EMAIL_MIME_TYPE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Email Mime Type attribute name")
            .description(
                    "Attribute name of mime type in which email text would be")
            .required(true)
            .defaultValue("email.mime.type")
            .addValidator(
                    StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor REFERENCE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Reference Attribute Name")
            .description("The name of the attribute where this processor will put the reference value for each of the recipients. The reference value is a unique value.")
            .required(true)
            .defaultValue("delivery.reference")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();
    
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure").description("").build();

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
        properties.add(EMAIL_SERVICE_ID);
        properties.add(EMAIL_SUBJECT_ATTRIBUTE_NAME);
        properties.add(FROM_EMAIL_ATTRIBUTE_NAME);
        properties.add(TO_EMAIL_ATTRIBUTE_NAME);
        properties.add(EMAIL_MIME_TYPE_ATTRIBUTE_NAME);
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
    public void onTrigger(final ProcessContext context,
            final ProcessSession session) {
        final List<FlowFile> flowFiles = session.get(1);
        if (flowFiles.isEmpty()) {
            return;
        }

        final ProcessorLog logger = getLogger();

        for (FlowFile flowFile : flowFiles) {
            final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
            final ObjectHolder<MessageWrapper> messageWrapperHolder = new ObjectHolder<>(
                    null);

            session.read(
                    flowFile,
                    (final InputStream rawIn) -> {
                        try {
                            messageWrapperHolder.set(MessageSerializer
                                    .deserializeMessageWrapper(rawIn));
                        } catch (MessageSerializationException ex) {
                            errorHolder
                            .set(new RuntimeException(
                                            "Error deserializing FlowFile content into a MessageWrapper instance. Routing to FAILURE",
                                            ex));
                        }
                    });

            if (errorHolder.get() != null) {
                logger.error(errorHolder.get().getMessage(), errorHolder.get()
                        .getCause());
                UCSCreateException.routeFlowFileToException(context, session,
                        logger, flowFile, REL_FAILURE, null,
                        "Error deserializing FlowFile: "
                        + errorHolder.get().getCause().getMessage(),
                        ExceptionType.InvalidMessage, null, null);
                continue;
            }

            Message message = messageWrapperHolder.get().getMessage();

			// resolve the sender. We couldn't resolved it before because we
            // needed the specific serviceId.
            UCSController ucsService = context.getProperty(
                    UCS_CONTROLLER_SERVICE).asControllerService(
                            UCSController.class);
            UserContactInfo uci = ucsService.resolveUserContactInfo(message
                    .getHeader().getSender().getPhysicalAddress().getAddress());

            if (uci == null) {
                UCSCreateException.routeFlowFileToException(context, session,
                        logger, flowFile, REL_FAILURE, null, "Unknown User: "
                        + message.getHeader().getSender()
                        .getPhysicalAddress().getAddress(),
                        ExceptionType.UnknownUser, null, null);
                return;
            }
            String emailServiceId = context.getProperty(EMAIL_SERVICE_ID)
                    .getValue();
            PhysicalAddress senderAddress = uci.getAddressesByType().get(
                    emailServiceId);

            // we currently don't support addresses other than physical
            Map<String, List<Recipient>> recipientsByType = message
                    .getHeader()
                    .getRecipientsList()
                    .stream()
                    .collect(
                            Collectors.groupingBy(r -> r.getDeliveryAddress()
                                    .getPhysicalAddress() == null ? "NO"
                                            : "YES"));

            if (!recipientsByType.getOrDefault("NO", new ArrayList<>())
                    .isEmpty()) {
                String unsupportedAddressTypes = recipientsByType
                        .get("NO")
                        .stream()
                        .map(r -> r.getDeliveryAddress().getAddressType()
                                .name()).collect(Collectors.toSet()).stream()
                        .collect(Collectors.joining(", "));

                logger.error("Unsupported Address of types [{}]",
                        new Object[]{unsupportedAddressTypes});
                UCSCreateException.routeFlowFileToException(context, session,
                        logger, flowFile, REL_FAILURE, null,
                        "Unsupported Address of types ["
                        + unsupportedAddressTypes + "]",
                        ExceptionType.InvalidAddress, null, null);
                continue;
            }

            List<Recipient> recipients = recipientsByType
                    .getOrDefault("YES", new ArrayList<>())
                    .stream()
                    .filter(r -> context
                            .getProperty(EMAIL_SERVICE_ID)
                            .getValue()
                            .equals(r.getDeliveryAddress().getPhysicalAddress()
                                    .getServiceId()))
                    .collect(Collectors.toList());

            if (recipients.isEmpty()) {
                // We shouldn't reach this point ideally. If somebody routed
                // a
                // message to this processor, it must be because we at least
                // have 1 Email address.
                logger.error(
                        "The message didn't contain any Address with ServiceId '{}'.",
                        new Object[]{context.getProperty(EMAIL_SERVICE_ID)
                            .getValue()});
                UCSCreateException
                        .routeFlowFileToException(
                                context,
                                session,
                                logger,
                                flowFile,
                                REL_FAILURE,
                                null,
                                "Error processing Message: The message didn't contain any Address with ServiceId '"
                                + context.getProperty(EMAIL_SERVICE_ID)
                                .getValue() + "'.",
                                ExceptionType.InvalidContext, null, null);
                continue;
            }

            String emailSubjectAttributeName = context.getProperty(
                    EMAIL_SUBJECT_ATTRIBUTE_NAME).getValue();
            String emailToAttributeName = context.getProperty(
                    TO_EMAIL_ATTRIBUTE_NAME).getValue();
            String emailFromAttributeName = context.getProperty(
                    FROM_EMAIL_ATTRIBUTE_NAME).getValue();
            String emailMimeTypeAttributeName = context.getProperty(
                    EMAIL_MIME_TYPE_ATTRIBUTE_NAME).getValue();

            Recipient firstRecpient = recipients.get(0);
            MessageBody body = MessageBodyResolver.resolveMessagePart(message,
                    context.getProperty(EMAIL_SERVICE_ID).getValue(),
                    firstRecpient.getRecipientId(), firstRecpient
                    .getDeliveryAddress().getPhysicalAddress()
                    .getAddress());
            String text = body.getContent();
            String mimeType = body.getType();
            if (mimeType == null || mimeType.trim().length() <= 0) {
                mimeType = "text/plain";
            }
            String subject = message.getHeader().getSubject();
            if (subject == null || subject.trim().length() <= 0) {
                subject = "<NO-SUBJECT>";
            }
            subject += "::[" + message.getHeader().getMessageId() + "]";

            // Converting recipient address to comma separated email
            // addresses.
            String toEmails = recipients.stream()
                .map(r -> r.getDeliveryAddress().getPhysicalAddress().getAddress())
                .collect(joining(","));
            
            // To write email body as content of FlowFile
            flowFile = session.write(flowFile, new OutputStreamCallback() {

                @Override
                public void process(OutputStream out) throws IOException {
                    out.write(text.getBytes());

                }
            });

            Map<String, String> attributes = new HashMap<>();
            attributes.put(emailSubjectAttributeName, subject);
            attributes.put(emailToAttributeName, toEmails);
            attributes.put(emailFromAttributeName, senderAddress.getAddress());
            attributes.put(emailMimeTypeAttributeName, mimeType);
            
            //Create a reference for each recipient, persist it and set it as
            //an attribute of the outgoing flow
            Map<String, String> generatedReferences = new HashMap<>();
            recipients.stream().forEach((recipient) -> {
                generatedReferences.put(recipient.getRecipientId(), UUID.randomUUID().toString());
            });
            
            if (message.getHeader().isReceiptNotification()){
                logger.debug("The message has ReceiptNotification flag enabled -> We are persisting its references.");
                generatedReferences.entrySet().stream().forEach((gr) -> {
                    ucsService.saveMessageReference(message, gr.getKey(), gr.getValue());
                });
            } else {
                logger.debug("The message doesn't have ReceiptNotification flag enabled -> We are not persisting its references.");
            }
            attributes.put(context.getProperty(REFERENCE_ATTRIBUTE_NAME).getValue(), generatedReferences.values().stream().collect(Collectors.joining(",")));

            flowFile = session.putAllAttributes(flowFile, attributes);
            session.getProvenanceReporter().modifyAttributes(flowFile);

            session.transfer(flowFile, REL_SUCCESS);
            session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
            logger.debug("Original FlowFile routed through {}", new Object[]{
                flowFile, REL_SUCCESS});
        }
    }
}
