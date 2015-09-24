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
import java.util.Set;
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
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.XMLListWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "Message", "Command"})
@CapabilityDescription("Retrieve messages from UCSController and starts a new FlowFile for each of them.")
public class UCSGetMessages extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final PropertyDescriptor KEEP_ORIGINAL_ATTRIBUTES = new PropertyDescriptor.Builder()
            .name("Keep Attributes")
            .description("If true, all the attributes present in the incoming FlowFile will be kept in the created FlowFile/s.")
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("true")
            .build();
    
    public static final PropertyDescriptor UNIQUE_FLOWFILE = new PropertyDescriptor.Builder()
            .name("Group Messages")
            .description("If true, all messages found in UCSController will be set into a unique FlowFile. If false, each message found in UCSController will be placed into a separate FlowFile.")
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("true")
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("Each message found in UCS Controller will start a new FileFlow through this relationship.").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        properties.add(KEEP_ORIGINAL_ATTRIBUTES);
        properties.add(UNIQUE_FLOWFILE);
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

        FlowFile originalFlowFile = session.get();
        
        final ProcessorLog logger = getLogger();
        
        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

        //TODO: add filtering capabilities.
        List<Message> messages = ucsService.listMessages();

        //wrap each Message with a MessageWrapper and Serialize them
        final List<MessageWrapper> result = messages.parallelStream()
                .map(m -> new MessageWrapper(m))
                .collect(Collectors.toList());

        logger.debug("Number of messages found: {}", new Object[]{result.size()});
        logger.debug("Generate a single FlowFile? {}", new Object[]{context.getProperty(UNIQUE_FLOWFILE).asBoolean()});
        if (context.getProperty(UNIQUE_FLOWFILE).asBoolean()) {
            logger.debug("Kep original attributes of FileFlow {}? {}", new Object[]{originalFlowFile, context.getProperty(KEEP_ORIGINAL_ATTRIBUTES).asBoolean()});
            FlowFile flowFile = context.getProperty(KEEP_ORIGINAL_ATTRIBUTES).asBoolean() && originalFlowFile != null ? session.create(originalFlowFile) : session.create();
            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    try {
                        out.write(MessageSerializer.serializeMessageWrappers(new XMLListWrapper<>(result)).getBytes());
                    } catch (MessageSerializationException ex) {
                        //should never happen
                    }
                }
            });
            
            session.getProvenanceReporter().create(flowFile);
            session.transfer(flowFile, REL_SUCCESS);
            logger.debug("Messages retrieved and sent in a single FlowFile {} through {}", new Object[]{flowFile, REL_SUCCESS});
        } else {
            for (final MessageWrapper mw : result) {
                logger.debug("Keep original attributes of FileFlow {}? {}", new Object[]{originalFlowFile, context.getProperty(KEEP_ORIGINAL_ATTRIBUTES).asBoolean()});
                FlowFile flowFile = context.getProperty(KEEP_ORIGINAL_ATTRIBUTES).asBoolean() && originalFlowFile != null ? session.create(originalFlowFile) : session.create();
                flowFile = session.write(flowFile, new OutputStreamCallback() {
                    @Override
                    public void process(final OutputStream out) throws IOException {
                        try {
                            out.write(MessageSerializer.serializeMessageWrapper(mw).getBytes());
                        } catch (MessageSerializationException ex) {
                            //should never happen
                        }
                    }
                });
                
                session.getProvenanceReporter().create(flowFile);
                session.transfer(flowFile, REL_SUCCESS);
                logger.debug("Message retrieved and sent in an individual FlowFile {} through {}", new Object[]{flowFile, REL_SUCCESS});
                
            }
        }
        
        if (originalFlowFile != null){
            logger.debug("Removing original FlowFile");
            session.remove(originalFlowFile);
        }

    }
}
