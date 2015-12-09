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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.SideEffectFree;
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
import org.socraticgrid.hl7.services.uc.model.DeliveryStatus;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.PhysicalAddress;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.services.uc.model.UserContactInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

/**
 *
 * @author esteban
 */
@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({"UCS", "address", "content"})
@CapabilityDescription("Resolves any recipient addess found in a message.")
public class UCSResolveRecipientAddresses extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("").build();
    public static final Relationship REL_UNKNOWN_RECIPIENT = new Relationship.Builder().name("unknown recipient").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_UNKNOWN_RECIPIENT);
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

            Message message = messageWrapperHolder.get().getMessage();

            //resolve each PhysicalAddress
            UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);
            for (Recipient r : message.getHeader().getRecipientsList()) {
                PhysicalAddress physicalAddress = r.getDeliveryAddress().getPhysicalAddress();

                //we currently don't support Addresses other than PhysicalAddress
                if (physicalAddress == null) {
                    //Add the error into the message header.
                    DeliveryStatus status = new DeliveryStatus();
                    status.setDeliveryStatusId(UUID.randomUUID().toString());
                    status.setRecipient(r);
                    status.setAddress(r.getDeliveryAddress());
                    status.setStatus("Not supported");
                    status.setTimestamp(new Date());

                    List<DeliveryStatus> deliveryStatusList = message.getHeader().getDeliveryStatusList();
                    if (deliveryStatusList == null) {
                        deliveryStatusList = new ArrayList<>();
                        message.getHeader().setDeliveryStatusList(deliveryStatusList);
                    }
                    deliveryStatusList.add(status);

                    //remove the address from the recipients list
                    r.setDeliveryAddress(null);
                    continue;
                }

                //Awful solution for Group Chat.
                if ("CHAT".equals(physicalAddress.getServiceId()) && physicalAddress.getAddress().startsWith("GROUP:")) {
                    //We decided to treat GROUP CHAT as a special case of a 
                    //PhysicalAddress whose address starts with "GROUP:".
                    //We could have also used a GroupAddress, but this class
                    //doesn't allow us to specify the systemId.
                    
                    //This address will not resolve into any UserContactInfo.
                    //This address will be treated in a special way later.
                    continue;
                }
                
                
                UserContactInfo uci = ucsService.resolveUserContactInfo(physicalAddress.getAddress());

                //We don't know this recipient
                if (uci == null) {
                    FlowFile clone = session.clone(flowFile);
                    UCSCreateException.routeFlowFileToException(
                            context,
                            session,
                            logger,
                            clone,
                            REL_UNKNOWN_RECIPIENT,
                            null,
                            "Unknown User: " + physicalAddress.getAddress(),
                            ExceptionType.UnknownUser,
                            null,
                            physicalAddress.getAddress());
                    
                    //remove the address from the recipients list
                    r.setDeliveryAddress(null);
                    
                    continue;
                }

                PhysicalAddress resolvedAddress = null;
                //For now, don't resolve the address if the service is ALERT.
                //TODO: revisit this once we have all the Alerting processors
                //in place.
                if (physicalAddress.getServiceId().equals("ALERT")){
                    logger.debug("We are not currently resoling addresses for Service ALERT. Remember to revisit this behavior!!");
                    resolvedAddress = physicalAddress;
                } else {
                    resolvedAddress = uci.getAddressesByType().get(physicalAddress.getServiceId());
                }

                if (resolvedAddress == null){
                    FlowFile clone = session.clone(flowFile);
                    UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        clone,
                        REL_FAILURE,
                        null,
                        "Unknown Address for service '"+physicalAddress.getServiceId()+"': ",
                        ExceptionType.UnknownService,
                        null,
                        r.getRecipientId());
                    
                    continue;
                }
                
                //if the resolved address doesn't have an id, then create one
                if (resolvedAddress.getAddressId() == null) {
                    resolvedAddress.setAddressId(UUID.randomUUID().toString());
                }

                //replace recipient's address
                r.getDeliveryAddress().setAddress(resolvedAddress);
            }

            String serializedMessage;
            try {
                //serialize the new version of the message
                serializedMessage = MessageSerializer.serializeMessageWrapper(messageWrapperHolder.get());
            } catch (MessageSerializationException ex) {
                logger.error("Error serializing result message:", ex);
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Error deserializing FlowFile: " + ex.getMessage(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                continue;
            }

            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(OutputStream out) throws IOException {
                    out.write(serializedMessage.getBytes());
                    out.flush();
                }
            });

            logger.debug("Addresses resolved. Routing message {} to {}.", new Object[]{flowFile, REL_SUCCESS.getName()});
            session.transfer(flowFile, REL_SUCCESS);
            session.getProvenanceReporter().route(flowFile, REL_SUCCESS);

        }
    }
}
