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
package org.socraticgrid.hl7.ucs.nifi.processor.command;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
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
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSCreateException;

/**
 *
 * @author esteban
 */

@EventDriven
@Tags({"UCS", "Message", "Command"})
@CapabilityDescription("Changes the status of an AlertMessage in UCSControllerService to 'Retracted'")
public class UCSCancelMessage extends AbstractProcessor {
    
    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();
    
    public static final Relationship REL_CANCELLED = new Relationship.Builder()
            .name("message cancelled")
            .description("If the status of the AlertMessage was succesfuly updated to 'Retracted', a flowfile containing the updated message will be transfered to this relationship.")
            .build();
    
    public static final Relationship REL_UNKNOWN_MESSAGE = new Relationship.Builder()
            .name("unknown message")
            .description("If the message that this processor is trying to update is not present in UCSControllerService, the original flowfile is transfered through this relationship.")
            .build();
    
    public static final Relationship REL_BAD_MESSAGE_TYPE = new Relationship.Builder()
            .name("bad message type")
            .description("If the message that this processor is trying to update is not an AlertMessage, a flowfile containing the message is transfered through this relationship.")
            .build();
    
    public static final Relationship REL_NO_UPDATE = new Relationship.Builder()
            .name("no update")
            .description("If the status of the message that this processor is trying to update is already 'Retracted', a flowfile containing the message is transfered through this relationship.")
            .build();
    
    public static final Relationship REL_INVALID_STATE = new Relationship.Builder()
            .name("invalid state")
            .description("If the status of the message that this processor is trying to update is other than 'Retracted' or 'Pending', a flowfile containing the message is transfered through this relationship.")
            .build();
    
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("If there is an error during the execution of this processor, the original flowfile will be transfered to this relationship.")
            .build();
    
    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_CANCELLED);
        relationships.add(REL_UNKNOWN_MESSAGE);
        relationships.add(REL_BAD_MESSAGE_TYPE);
        relationships.add(REL_NO_UPDATE);
        relationships.add(REL_INVALID_STATE);
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

        FlowFile flowFile = session.get();
        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);
        
        final ProcessorLog logger = getLogger();

        //Get first and only argument from the incoming flowfile: the message id.
        String messageId = flowFile.getAttribute("command.args"); 
        if (messageId == null || messageId.trim().isEmpty()) {
            logger.error("Missing arg #1: Message Id. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_FAILURE});
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Missing arg #1: Message Id.",
                    ExceptionType.InvalidInput,
                    null,
                    null);
            return;
        }
        
        //Retrieve the message from UCSControllerService.
        Optional<Message> originalMessageO = ucsService.getMessageById(messageId);
        if (!originalMessageO.isPresent()){
            logger.error("No Message with id {} found in UCSControllerService. Routing FlowFile {} to {}.", new Object[]{messageId, flowFile, REL_FAILURE});
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_UNKNOWN_MESSAGE,
                    null,
                    "No Message with id "+messageId+" found in UCSControllerService.",
                    ExceptionType.InvalidMessage,
                    null,
                    null);
            return;
        }
        
        Message originalMessage = originalMessageO.get();
        
        //if there tpye of message is not AlertMessage -> REL_BAD_MESSAGE_TYPE
        if (!(originalMessage instanceof AlertMessage)){
            logger.error("The referenced Message with id {} is not an AlertMessage. Expected {} found {}. Routing FlowFile {} to {}.", new Object[]{messageId, AlertMessage.class, originalMessage.getClass(), flowFile, REL_FAILURE});
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_BAD_MESSAGE_TYPE,
                    null,
                    "The Message with id "+messageId+" is not an AlertMessage but a "+originalMessage.getClass().getName(),
                    ExceptionType.InvalidContext,
                    null,
                    null);
            return;
        }
        
        final AlertMessage originalAlertMessage = (AlertMessage) originalMessage;
        
        
        switch (originalAlertMessage.getHeader().getAlertStatus()){
            case Retracted: {
                //The message is already Retracted. The original flowfile is 
                //removed and a new FlowFile containing the message is transfered
                //to REL_NO_UPDATE
                FlowFile newFlowFile = session.create(flowFile);
                session.getProvenanceReporter().create(newFlowFile);
                session.remove(flowFile);
                
                logger.debug("The message was already 'Retracted'.");
                
                newFlowFile = session.write(newFlowFile, new OutputStreamCallback() {
                    @Override
                    public void process(final OutputStream out) throws IOException {
                        try {
                            out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(originalAlertMessage)).getBytes());
                        } catch (MessageSerializationException ex) {
                            //should never happen
                        }
                    }
                });
                session.getProvenanceReporter().modifyContent(newFlowFile);
                session.transfer(newFlowFile, REL_NO_UPDATE);
                logger.debug("AlertMessage status was not updated because it was already 'Retracted'. Routing a new FlowFile {} containing the message through {}", new Object[]{newFlowFile, REL_NO_UPDATE});
                
                break;
            }
            case Pending: {
                //The message's status is changed toRetracted and persisted in
                //UCSControllerService. The original flowfile is 
                //removed and a new FlowFile containing the message is transfered
                //to REL_CANCELLED

                
                originalAlertMessage.getHeader().setAlertStatus(AlertStatus.Retracted);
                ucsService.updateMessage(originalAlertMessage);
                logger.debug("The message was 'Pending'. The new status is now 'Retracted'.");
                
                FlowFile newFlowFile = session.create(flowFile);
                session.getProvenanceReporter().create(newFlowFile);
                session.remove(flowFile);
                
                newFlowFile = session.write(newFlowFile, new OutputStreamCallback() {
                    @Override
                    public void process(final OutputStream out) throws IOException {
                        try {
                            out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(originalAlertMessage)).getBytes());
                        } catch (MessageSerializationException ex) {
                            //should never happen
                        }
                    }
                });
                session.getProvenanceReporter().modifyContent(newFlowFile);
                session.transfer(newFlowFile, REL_CANCELLED);
                logger.debug("AlertMessage status updated to 'Retracted'. Routing a new FlowFile {} containing the message through {}", new Object[]{newFlowFile, REL_CANCELLED});
                
                break;
            }
            default: {
                //The message's status is not Retracted nor Pending. According
                //to the current implementation, we can't change the status to
                //Retracted in these cases. The original flowfile is sent 
                //to REL_INVALID_STATE.
                logger.debug("The status of the Message with id '{}' can't be updated to 'Retracted' because its current value is '{}'", new Object[]{messageId, originalAlertMessage.getHeader().getAlertStatus()});
                UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_INVALID_STATE,
                    null,
                    "The status of the Message with id "+messageId+" can't be updated to 'Retracted' because its current value is '"+originalAlertMessage.getHeader().getAlertStatus()+"'",
                    ExceptionType.ReadOnly,
                    null,
                    null);
                break;
            }
        }
    }
    
}
