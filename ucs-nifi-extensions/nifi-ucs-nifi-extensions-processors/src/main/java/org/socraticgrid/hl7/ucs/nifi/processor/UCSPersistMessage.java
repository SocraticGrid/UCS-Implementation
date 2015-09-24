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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.util.ObjectHolder;
import org.quartz.SchedulerException;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

@EventDriven
@SupportsBatching
@Tags({"UCS", "message"})
@CapabilityDescription("Persists the message represented by the content of the current FlowFile. This processor also configures any timeout/escalation mechanism "
        + "for the incoming message.")
public class UCSPersistMessage extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();
    
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();
    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("").build();

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
    public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue) {
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
                            "Error deserializing FlowFile: "+errorHolder.get().getCause(), 
                            ExceptionType.InvalidMessage, 
                            null, 
                            null);
                continue;
            }
            
            Message message = messageWrapperHolder.get().getMessage();
            UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

            logger.debug("Persisting message {}.", new Object[]{message});
            ucsService.saveMessage(message);
            
            //if the message meets the requirements for escalation, a timeout 
            //job is setup.
            if (message.getHeader().isReceiptNotification() && message.getHeader().getRespondBy() > 0){
                logger.debug("The message met the requirements for escalation. A timeout job is going to be setup for it.");
                try {
                    ucsService.setupResponseTimeout(message);
                } catch (SchedulerException ex) {
                    logger.error(errorHolder.get().getMessage(), errorHolder.get().getCause());
                    UCSCreateException.routeFlowFileToException(
                            context, 
                            session, 
                            logger, 
                            flowFile, 
                            REL_FAILURE,
                            null, 
                            "Error setting up Escalation Job: "+ex.getMessage(), 
                            ExceptionType.SystemFault, 
                            null, 
                            null);
                    continue;
                }
            }
            
            //Send Original FlowFile to REL_ORIGINAL
            session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
            session.transfer(flowFile, REL_SUCCESS);
            logger.info("Message persisted. Routing {} to {}", new Object[]{flowFile, REL_SUCCESS});
            
        }
        
    }
    
}