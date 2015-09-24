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
import org.apache.commons.lang3.StringUtils;

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
 * @author pavan
 */
@Tags({"UCS", "alerting", "message"})
@CapabilityDescription("Receives alert message and updates message with status")
public class UCSAlertingUpdateAlertMessage extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final Relationship REL_STATUS_MISSMATCH = new Relationship.Builder()
            .name("mismatch")
            .description(
                    "Original alert message in UCS is other than Pending or Acknowledged/New alert message is other than Acknowledged")
            .build();
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description(
                    "AlertStatus property of the new message is different than the alertStatus property of the original message.")
            .build();
    public static final Relationship REL_NO_UPDATE = new Relationship.Builder()
            .name("noupdate")
            .description(
                    "AlertStatus property of the new message is equals to the alertStatus property of the original message.")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description(
                    "If status can not update for some reason.")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_STATUS_MISSMATCH);
        relationships.add(REL_SUCCESS);
        relationships.add(REL_NO_UPDATE);
        relationships.add(REL_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public void onTrigger(final ProcessContext context,
            final ProcessSession session) {
        final FlowFile flowFile = session.get();

        if (flowFile == null) {
            return;
        }
        final ProcessorLog logger = getLogger();

        try {
            UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

            String messageId = flowFile.getAttribute("command.args.1");
            String newStatusAsString = flowFile.getAttribute("command.args.2");

            if (StringUtils.isEmpty(messageId) || StringUtils.isEmpty(newStatusAsString)) {
                logger.error("Missing arguments: messageId and newStatus arguments are both mandatory. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_FAILURE});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Missing arguments: messageId and newStatus arguments are both mandatory.",
                        ExceptionType.InvalidInput,
                        null,
                        null);
                return;
            }

            //If the alertStatus property of the new alert message is other than "Acknowledged", the processor will fail. 
            //The incoming flowfile is redirected to REL_STATUS_MISSMATCH
            AlertStatus newStatus = AlertStatus.valueOf(newStatusAsString);
            if (AlertStatus.Acknowledged != newStatus) {
                logger.error("The status of af AlertMessage can only be updated to Acknowledged. Expected {} found {}. Routing FlowFile {} to {}.", new Object[]{AlertStatus.Acknowledged, newStatus, flowFile, REL_STATUS_MISSMATCH});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_STATUS_MISSMATCH,
                        null,
                        "The status of af AlertMessage can only be updated to Acknowledged. Expected " + AlertStatus.Acknowledged + " found " + newStatus + ".",
                        ExceptionType.UpdateError,
                        null,
                        null);
                return;
            }

            //Retrieve the message from UCSControllerService.
            Optional<Message> originalMsg = ucsService.getMessageById(messageId);
            if (!originalMsg.isPresent()) {
                logger.error("No Message with id {} found in UCSControllerService.", new Object[]{messageId, REL_FAILURE});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "No Message with id " + messageId + " found in UCSControllerService.",
                        ExceptionType.UpdateError,
                        null,
                        null);
                return;
            }

            Message originalMessage = originalMsg.get();
            //if there tpye of message is not AlertMessage
            if (!(originalMessage instanceof AlertMessage)) {
                logger.error("The referenced Message with id {} is not an AlertMessage. Expected {} found {}. Routing FlowFile {} to {}.", new Object[]{messageId, AlertMessage.class, originalMessage.getClass(), flowFile, REL_FAILURE});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "The Message with id " + messageId + " is not an AlertMessage but a " + originalMessage.getClass().getName(),
                        ExceptionType.UpdateError,
                        null,
                        null);
                return;
            }

            final AlertMessage originalAlertMessage = (AlertMessage) originalMessage;

            //If the alertStatus property of the original alert message in UCS is other than "Pending" or "Acknowledged", the processor will fail. 
            //The incoming flowfile is redirected to REL_STATUS_MISSMATCH
            if (!AlertStatus.Acknowledged.equals(originalAlertMessage.getHeader().getAlertStatus()) && !AlertStatus.Pending.equals(originalAlertMessage.getHeader().getAlertStatus())) {
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_STATUS_MISSMATCH,
                        null,
                        "AlertStatus property of the original alert message in UCS is other than Pending or Acknowledged",
                        ExceptionType.UpdateError,
                        null,
                        null);
                logger.debug("AlertStatus property of the original alert message in UCS is other than Pending or Acknowledged");
                return;
            }

            //If the alertStatus property of the new message is different than the alertStatus property of the original message, 
            //the property in the original message is updated. Use REL_SUCCESS.
            if (newStatus != originalAlertMessage.getHeader().getAlertStatus()) {
                //Keep the original version so it can be added to the final flow file.
                String originalMessageAsString = MessageSerializer.serializeMessageWrapper(new MessageWrapper(originalMessage));

                originalAlertMessage.getHeader().setAlertStatus(newStatus);
                ucsService.updateMessage(originalAlertMessage);
                logger.debug("Status of AlertMessage updated to {}. Message was also updated in UCSControllerService.", new Object[]{newStatus});

                FlowFile newFlowFile = session.create(flowFile);
                session.getProvenanceReporter().create(newFlowFile);

                newFlowFile = session.write(newFlowFile, new OutputStreamCallback() {
                    @Override
                    public void process(final OutputStream out) throws IOException {
                        try {
                            List<MessageWrapper> messages = new ArrayList<>();
                            //original message
                            messages.add(MessageSerializer.deserializeMessageWrapper(originalMessageAsString));
                            //updated message.
                            messages.add(new MessageWrapper(originalAlertMessage));
                            out.write(MessageSerializer.serializeMessageWrappers(messages).getBytes());
                        } catch (MessageSerializationException ex) {
                            //should never happen
                        }
                    }
                });
                session.remove(flowFile);
                session.getProvenanceReporter().modifyContent(newFlowFile);
                session.transfer(newFlowFile, REL_SUCCESS);
                session.getProvenanceReporter().route(newFlowFile, REL_SUCCESS);
                logger.debug("AlertStatus property of the original alert message updated.");
                return;
            }

            //If the alertStatus property of the new message is equals to the alertStatus property of the original message, 
            //the flowfile will be directed to a REL_NO_UPDATE relationship. 
            if (newStatus == originalAlertMessage.getHeader().getAlertStatus()) {
                session.getProvenanceReporter().modifyContent(flowFile);
                session.transfer(flowFile, REL_NO_UPDATE);
                logger.debug("AlertStatus is same in new and orginal message.");
                return;
            }

            //We should never reach this point.
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Command couldn't be processed. We didn't know how to do it!",
                    ExceptionType.InvalidInput,
                    null,
                    null);
            logger.debug("Command couldn't be processed. We didn't know how to do it!");

        } catch (Exception e) {
            logger.error("Some error occurred while updating alert message status", e);
            session.transfer(flowFile, REL_FAILURE);
            session.getProvenanceReporter().route(flowFile, REL_FAILURE);
        }

    }
}
