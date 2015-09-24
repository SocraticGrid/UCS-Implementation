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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "Client"})
@CapabilityDescription("Gets any previously registered UCSCLientCallback. For each callback found, this processor will clone the original FlowFile adding the callback URL as an attribute.")
public class UCSGetUCSClientCallbacks extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final PropertyDescriptor CALLBACK_ATTRIBUTE_NAME = new PropertyDescriptor.Builder()
            .name("Callback Attribute Name")
            .description("Attribute Name used to store the callback URL")
            .required(true)
            .defaultValue("ucs.client.callback")
            .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
            .build();

    public static final PropertyDescriptor REMOVE_PROTOCOL_FROM_URL = new PropertyDescriptor.Builder()
            .name("Remove callback protocol")
            .description("Sometimes is useful to remove the protocol part from the callback URLs")
            .required(true)
            .defaultValue("true")
            .allowableValues("true", "false")
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("For each callback found, this processor will clone the original FlowFile adding the callback URL as an attribute and routing it through this relationship").build();
    public static final Relationship REL_EMPTY = new Relationship.Builder().name("empty").description("This relationship is used in the case that no callback is currently registered in UCSController.").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_EMPTY);
        relationships.add(REL_FAILURE);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        properties.add(CALLBACK_ATTRIBUTE_NAME);
        properties.add(REMOVE_PROTOCOL_FROM_URL);
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

        final ProcessorLog logger = getLogger();

        UCSController ucsService = context.getProperty(UCS_CONTROLLER_SERVICE).asControllerService(UCSController.class);

        final String callbackAttributeName = context.getProperty(CALLBACK_ATTRIBUTE_NAME).getValue();
        final Boolean removeProtocol = context.getProperty(REMOVE_PROTOCOL_FROM_URL).asBoolean();

        Set<URL> ucsClientCallbacks = ucsService.getUCSClientCallbacks();

        if (ucsClientCallbacks.isEmpty()) {
            logger.debug("No UCS CLient callback registered in UCSController. Routing FlowFile {} to {}.", new Object[]{flowFile, REL_EMPTY});
            session.transfer(flowFile, REL_EMPTY);
            session.getProvenanceReporter().route(flowFile, REL_EMPTY);
            return;
        }
        
        Iterator<URL> iterator = ucsClientCallbacks.iterator();

        ObjectHolder<FlowFile> originalFlowFileHolder = new ObjectHolder<>(flowFile);
        URL firstCallback = iterator.next();
        iterator.forEachRemaining(url -> {
            FlowFile clone = session.clone(originalFlowFileHolder.get());
            String callbackAttributeValue = processURL(url, removeProtocol);
            clone = session.putAttribute(clone, callbackAttributeName, callbackAttributeValue);
            
            session.getProvenanceReporter().clone(originalFlowFileHolder.get(), clone);
            logger.debug("FlowFile {} cloned into {} and attribute {} set to '{}'. Routing FlowFile {} to {}.", new Object[]{originalFlowFileHolder.get(), clone, callbackAttributeName, callbackAttributeValue, clone, REL_SUCCESS});
            session.transfer(clone, REL_SUCCESS);
            session.getProvenanceReporter().route(clone, REL_SUCCESS);
        });
        
        
        String callbackAttributeValue = processURL(firstCallback, removeProtocol);
        flowFile = session.putAttribute(flowFile, callbackAttributeName, callbackAttributeValue);

        session.getProvenanceReporter().modifyAttributes(flowFile);

        logger.debug("Attribute {} set to '{}'. Routing FlowFile {} to {}.", new Object[]{callbackAttributeName, callbackAttributeValue, flowFile, REL_SUCCESS});
        session.transfer(flowFile, REL_SUCCESS);
        session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
        
    }
    
    private String processURL(URL url, boolean removeProtocol){
        String urlAsString = url.toString();
        
        if(removeProtocol){
            urlAsString = urlAsString.substring(urlAsString.indexOf("//")+2);
        }
        
        return urlAsString;
    }
}
