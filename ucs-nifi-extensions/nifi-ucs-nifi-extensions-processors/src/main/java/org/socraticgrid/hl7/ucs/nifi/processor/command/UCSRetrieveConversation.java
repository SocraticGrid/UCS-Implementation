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
import static java.util.stream.Collectors.toList;
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
import org.socraticgrid.hl7.services.uc.model.ConversationInfo;
import org.socraticgrid.hl7.ucs.nifi.common.model.ConversationInfoWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ConversationInfoSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSCreateException;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "Conversation", "Command"})
@CapabilityDescription("Retrieve information about a specific Conversation in UCS.")
public class UCSRetrieveConversation extends AbstractProcessor {

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

    public static final Relationship REL_NOT_FOUND = new Relationship.Builder()
            .name("not found")
            .description("If there is no conversation with the specified id, an exception will be routed to this destination")
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
        relationships.add(REL_NOT_FOUND);
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

        try{
            
            //Get the first and only argument from the incoming flowfile: the conversation id.
            String conversationId = flowFile.getAttribute("command.args");
            if (conversationId == null || conversationId.trim().isEmpty()) {
                logger.error("Missing arg #1: Conversation Id. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_FAILURE});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_FAILURE,
                        null,
                        "Missing arg #1: Conversation Id",
                        ExceptionType.InvalidInput,
                        null,
                        null);
                return;
            }
            
            Optional<Conversation> conversation = ucsService.getConversationById(conversationId);
            
            if (!conversation.isPresent()){
                logger.error("Conversation with Id {} not found. Routing FlowFile {} to {}.", new Object[]{conversationId, flowFile, REL_FAILURE});
                UCSCreateException.routeFlowFileToException(
                        context,
                        session,
                        logger,
                        flowFile,
                        REL_NOT_FOUND,
                        null,
                        "Conversation with Id "+conversationId+" not found",
                        ExceptionType.InvalidConversation,
                        null,
                        null);
                return;
            }
            
            logger.debug("Conversation found: {}", new Object[]{conversation.get()});

            //convert the conversation into a ConversationInfo
            final ConversationInfo ci = new ConversationInfo();
            ci.setConversation(conversation.get());
            
            ci.setMessages(ucsService.listMessagesByConversationId(conversationId).stream()
                    .map(m -> m.getHeader().getMessageId())
                    .collect(toList())
            );
            
            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    try {
                        out.write(ConversationInfoSerializer.serializeConversationInfoWrapper(new ConversationInfoWrapper(ci)).getBytes());
                    } catch (MessageSerializationException ex) {
                        //should never happen
                    }
                }
            });

            session.getProvenanceReporter().create(flowFile);
            session.transfer(flowFile, REL_SUCCESS);
            logger.debug("ConversationInfo retrieved and sent in a single FlowFile {} through {}", new Object[]{flowFile, REL_SUCCESS});
        } catch (Exception ex){
            logger.error("Exception geting conversation information. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_FAILURE}, ex);
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
        }

    }

}
