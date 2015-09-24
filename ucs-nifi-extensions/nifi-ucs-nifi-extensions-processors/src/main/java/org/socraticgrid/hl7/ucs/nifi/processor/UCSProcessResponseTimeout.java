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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
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
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage.TimeOutType;

@TriggerWhenEmpty
@Tags({"timeout", "message"})
@CapabilityDescription("Consumption of timed out messages and starts a new FlowFile for each of them.")
public class UCSProcessResponseTimeout extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses to send messages.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final Relationship REL_NO_RESPONSES = new Relationship.Builder()
            .name("noresponse")
            .description("Each no response message found in the ChatController will be sent through this relationship.")
            .build();
    public static final Relationship REL_PARTIAL_RESPONSES = new Relationship.Builder()
            .name("partialresponse")
            .description("Each partial response message found in the ChatController will be sent through this relationship.")
            .build();
    
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("An error happened when processing the messages.")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_NO_RESPONSES);
        relationships.add(REL_PARTIAL_RESPONSES);
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
        final ProcessorLog logger = getLogger();
        Relationship relation=null;
        Collection<FlowFile> flowFiles = new ArrayList<>();
        try {

            UCSController ucsControllerService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

            Set<TimedOutMessage> messages = ucsControllerService.consumeMessagesWithResponseTimeout();
            logger.debug("{} timeout messages found.", new Object[]{messages.size()});
            
            for (TimedOutMessage message : messages) {
            	if(message.getTimeOutReason()==TimeOutType.NO_RESPONSES){
            		relation = REL_NO_RESPONSES;
            		List<Message> norespMsg = message.getMessage().getHeader().getOnNoResponseAll();
            		for (Message msg : norespMsg) {
            			final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);

                        FlowFile flowFile = session.create();
                        session.getProvenanceReporter().create(flowFile);
                        flowFile = session.write(flowFile, new OutputStreamCallback() {
                            @Override
                            public void process(final OutputStream out) throws IOException {
                                try {
                                	out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(msg)).getBytes());
                                } catch (MessageSerializationException ex) {
                                    //should never happen
                                    errorHolder.set(ex);
                                }
                            }
                        });
                        session.getProvenanceReporter().modifyContent(flowFile);

                        if (errorHolder.get() != null) {
                            logger.error(errorHolder.get().getMessage(), errorHolder.get());
                            UCSCreateException.routeFlowFileToException(
                                    context,
                                    session,
                                    logger,
                                    flowFile,
                                    REL_FAILURE,
                                    null,
                                    "Error serializing chat message: " + errorHolder.get().getMessage(),
                                    ExceptionType.InvalidInput,
                                    null,
                                    null);
                            continue;
                        }

                        logger.debug("Message received and serialized. Routing message {} to {}.", new Object[]{flowFile, relation});
                        flowFiles.add(flowFile);
					}
            	}else{
            		relation = REL_PARTIAL_RESPONSES;
            		List<Message> norespMsg = message.getMessage().getHeader().getOnNoResponseAny();
            		for (Message msg : norespMsg) {
            			final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);

                        FlowFile flowFile = session.create();
                        session.getProvenanceReporter().create(flowFile);
                        flowFile = session.write(flowFile, new OutputStreamCallback() {
                            @Override
                            public void process(final OutputStream out) throws IOException {
                                try {
                                    out.write(MessageSerializer.serializeMessageWrapper(new MessageWrapper(msg)).getBytes()); 
                                } catch (MessageSerializationException ex) {
                                    //should never happen
                                    errorHolder.set(ex);
                                }
                            }
                        });
                        session.getProvenanceReporter().modifyContent(flowFile);

                        if (errorHolder.get() != null) {
                            logger.error(errorHolder.get().getMessage(), errorHolder.get());
                            UCSCreateException.routeFlowFileToException(
                                    context,
                                    session,
                                    logger,
                                    flowFile,
                                    REL_FAILURE,
                                    null,
                                    "Error serializing chat message: " + errorHolder.get().getMessage(),
                                    ExceptionType.InvalidInput,
                                    null,
                                    null);
                            continue;
                        }

                        logger.debug("Message received and serialized. Routing message {} to {}.", new Object[]{flowFile, relation});
                        flowFiles.add(flowFile);
					}
            	}
                
            } 
            session.transfer(flowFiles, relation); 

        } catch (Exception e) {
            logger.error("Error getting messages from UCS Controller. Yielding...", e);
        } finally {
            context.yield();
        }

    }

}
