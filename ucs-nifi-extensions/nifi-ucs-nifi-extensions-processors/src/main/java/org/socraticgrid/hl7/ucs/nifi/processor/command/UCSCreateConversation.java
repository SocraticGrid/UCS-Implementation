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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.codec.binary.Base64;
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
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationInfoWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationInfoSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSCreateException;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "Conversation", "Command"})
@CapabilityDescription("Creates a new Conversation in UCS. This command expects 1 parameter that"
        + "is a Base64 encoded and seralized Conversation object.")
public class UCSCreateConversation extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("If no errors, the incoming flowfile will be transfered to this relation.")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("If some error occurred, exception will be routed to this destination")
            .build();

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

        FlowFile flowFile = session.get();

        final ProcessorLog logger = getLogger();

        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

        //Get the first and only argument from the incoming flowfile: the serialized conversation
        String serializedConversation = flowFile.getAttribute("command.args");
        if (serializedConversation == null || serializedConversation.trim().isEmpty()) {
            logger.error("Missing arg #1: Conversation. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_FAILURE});
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Missing arg #1: Conversation",
                    ExceptionType.InvalidInput,
                    null,
                    null);
            return;
        }

        try {
            //deserialize the conversation from the incoming argument.
            ConversationWrapper cw = ConversationSerializer.deserializeConversationWrapper(new String(Base64.decodeBase64(serializedConversation)));

            //if the conversation doesn't have an id, set one.
            if (cw.getConversation().getConversationId() == null){
                do{
                    cw.getConversation().setConversationId(UUID.randomUUID().toString());
                } while (ucsService.getConversationById(cw.getConversation().getConversationId()).isPresent());
            } else {
                //check if the conversation id already exists.
                Optional<Conversation> conversationById = ucsService.getConversationById(cw.getConversation().getConversationId());

                if (conversationById.isPresent()){
                    logger.error("Duplicated Conversation id {}. Routing FlowFile {} to {}.", new Object[]{cw.getConversation().getConversationId(), flowFile, REL_FAILURE});
                    UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Duplicated Conversation id " + cw.getConversation().getConversationId(),
                        ExceptionType.InvalidConversation,
                        null,
                        null);
                    return;
                }
            }
            
            //pesist the conversation
            ucsService.saveConversation(cw.getConversation());
            
            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    try {
                        out.write(ConversationSerializer.serializeConversationWrapper(cw).getBytes());
                    } catch (MessageSerializationException ex) {
                        //should never happen
                    }
                }
            });
            
            logger.debug("Conversation succesfully persisted. Routing {} to {} relation.", new Object[]{flowFile, REL_SUCCESS});
            session.transfer(flowFile, REL_SUCCESS);

        } catch (Exception ex) {
            logger.error("Exception creating conversation. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_FAILURE}, ex);
            UCSCreateException.routeFlowFileToException(
                    context,
                    session,
                    logger,
                    flowFile,
                    REL_FAILURE,
                    null,
                    "Exception creating conversation: " + ex.getMessage(),
                    ExceptionType.InvalidInput,
                    null,
                    null);
            return;
        }

    }
}
