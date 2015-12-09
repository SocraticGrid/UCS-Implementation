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
import org.socraticgrid.hl7.services.uc.model.DeliveryStatus;
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
@Tags({"UCS", "Message", "Delivery Status"})
@CapabilityDescription("Updates the DeliveryStatus of a specific Recipient in a Message. This processor uses a Reference identifier in order to get a reference to the message and provider. "
        + "This processor doesn't expect a serialized message as the content of the incoming FlowFiles.\n"
        + "WARNING: The current implementation is not Thread Safe!! ")
public class UCSUpdateMessageDeliveryStatus extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final PropertyDescriptor REFERENCE_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Reference Attribute Name")
            .description("The name of the attribute where this processor will get the reference value for each of the recipients.")
            .required(true)
            .defaultValue("delivery.reference")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor DELIVERY_STATUS_ACTION = new PropertyDescriptor.Builder()
            .name("Delivery Status Action")
            .description("Delivery Status Action to be added to the message.")
            .required(true)
            .defaultValue("SEND")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor DELIVERY_STATUS = new PropertyDescriptor.Builder()
            .name("Delivery Status")
            .description("Delivery Status to be added to the message.")
            .required(true)
            .defaultValue("OK")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor WRITE_RESULT_TO_CONTENT = new PropertyDescriptor.Builder()
            .name("Write to Content")
            .description("Specifies whether the updated Message needs to be serialized as the content of the outgoing FlowFile. If false, the original content will be forwarded.")
            .required(true)
            .defaultValue("true")
            .allowableValues("true", "false")
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
        properties.add(REFERENCE_ATTRIBUTE_NAME);
        properties.add(DELIVERY_STATUS_ACTION);
        properties.add(DELIVERY_STATUS);
        properties.add(WRITE_RESULT_TO_CONTENT);
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

        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);
        String referenceAttributeName = context.getProperty(REFERENCE_ATTRIBUTE_NAME).getValue();
        String action = context.getProperty(DELIVERY_STATUS_ACTION).getValue();
        String status = context.getProperty(DELIVERY_STATUS).getValue();
        Boolean writeContent = context.getProperty(WRITE_RESULT_TO_CONTENT).asBoolean();

        final ProcessorLog logger = getLogger();

        //TODO: there are some situations where a reference doesn't exist, like in the Alerts.
        //There are 2 options: either we make the reference optional in this 
        //processor, or we create a UCSPrepareAlertMessage processor that sets
        //a reference for the message.
        String reference = flowFile.getAttribute(referenceAttributeName);
        if (reference == null || reference.isEmpty()) {
            logger.error("Reference ({}) not found in current FlowFile {}.", new Object[]{referenceAttributeName, flowFile});
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Reference not found! ",
                    ExceptionType.InvalidContext,
                    null,
                    null);
            return;
        }

        String[] references = reference.split(",");

        //all the references should belong to a single message
        Message message = null;

        for (int i = 0; i < references.length; i++) {
            String ref = references[i];

            Optional<Message> messageByReference = ucsService.getMessageByReference(ref);
            Optional<String> recipientIdByReference = ucsService.getRecipientIdByReference(ref);

            if (!messageByReference.isPresent() || !recipientIdByReference.isPresent()) {
                logger.debug("No message or recipient matching the reference id '{}'. Routing FlowFile {} to {}.", new Object[]{ref, flowFile, REL_NO_MATCH});
                session.transfer(flowFile, REL_NO_MATCH);
                session.getProvenanceReporter().route(flowFile, REL_NO_MATCH);
                return;
            }

            if (message != null && !(message.getHeader().getMessageId().equals(messageByReference.get().getHeader().getMessageId()))) {
                //all the references should belong to a single message
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "All reference must belong to the same message! " + message.getHeader().getMessageId() + " != " + messageByReference.get().getHeader().getMessageId(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                return;
            }
            message = messageByReference.get();

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
                        "The recipient "+recipientIdByReference.get()+" referenced by the reference id "+reference+" doesn't match the message referenced by the same reference id ",
                        ExceptionType.InvalidContext,
                        null,
                        null);
                return;
            }

            DeliveryStatus deliveryStatus = new DeliveryStatus();
            deliveryStatus.setTimestamp(new Date());
            deliveryStatus.setRecipient(recipient.get());
            deliveryStatus.setAddress(recipient.get().getDeliveryAddress());
            deliveryStatus.setAction(action);
            deliveryStatus.setStatus(status);

            //TODO: this part is no thread safe!
            List<DeliveryStatus> deliveryStatusList = message.getHeader().getDeliveryStatusList();
            if (deliveryStatusList == null) {
                deliveryStatusList = new ArrayList<>();
                message.getHeader().setDeliveryStatusList(deliveryStatusList);
            }
            deliveryStatusList.add(deliveryStatus);

            ucsService.saveMessage(message);
            
            logger.debug("New Delivery Status for message {} added. Recipient: '{}', Address: '{}', Action: '{}', Status: '{}'", new Object[]{
                message.getHeader().getMessageId(),
                recipient.get().getRecipientId(),
                recipient.get().getDeliveryAddress(),
                action,
                status
            });
        }

        //do we need to serialize the updated message as the content of the outgoing
        //FlowFile?
        if (writeContent && message != null) {
            Message finalMessage = message;
            final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);

            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    try {
                        out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(finalMessage)).getBytes());
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
                        "Error serializing message '"+finalMessage+"': "+errorHolder.get().getMessage(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                return;
            }

            session.getProvenanceReporter().modifyContent(flowFile, "Content updated with Message's latest version.");
        }

        //route FlowFile to SUCCESS
        logger.debug("Delivery Status updated. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_SUCCESS});
        session.transfer(flowFile, REL_SUCCESS);
        session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
    }
}
