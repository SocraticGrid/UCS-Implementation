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
import java.util.Set;

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
import org.socraticgrid.hl7.ucs.nifi.common.model.Adapter;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.AdapterSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;
import org.socraticgrid.hl7.ucs.nifi.processor.UCSCreateException;

/**
 *
 * @author BasitAzeem
 *
 */
@Tags({"adapters", "UCS"})
@CapabilityDescription("Fetches all the Adapters supported by UCS Nifi Workflow.")
public class UCSGetSupportedAdapters extends AbstractProcessor {

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description(
                    "The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class).required(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success").description("List of supported UCS Adapters.")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description(
                    "If some error occurred, exception will be routed to this destination")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
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

        FlowFile flowFile = session.get();

        if (flowFile == null) {
            return;
        }

        try {

            UCSController ucsService = context.getProperty(
                    UCS_CONTROLLER_SERVICE).asControllerService(
                            UCSController.class);

            List<Adapter> supportedAdapters = ucsService.getSupportedAdapters();
            logger.debug("Number of Supported Adapters : " + supportedAdapters.size());

            final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);

            getLogger().debug("Adding content to FlowFile");
            // Writing List of Adapters into FlowFile
            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(OutputStream out) throws IOException {
                    try {
                        out.write(AdapterSerializer.serializeAdapters(supportedAdapters).getBytes());
                    } catch (Exception e) {
                        // should never happen
                        getLogger().error(
                                "Some error occurred while writing a flowfile!", e);
                        errorHolder.set(e);
                    }
                }
            });
            if (errorHolder.get() != null) {
                throw errorHolder.get();
            }
            session.getProvenanceReporter().modifyContent(flowFile);

            session.transfer(flowFile, REL_SUCCESS);
        } catch (Throwable e) {
            logger.error("Exception while processing Supported Adapters.", e);
            UCSCreateException.routeFlowFileToException(context, session,
                    logger, flowFile, REL_FAILURE, null,
                    "Exception while processing Supported Adapters: " + e.getMessage(),
                    ExceptionType.ServerAdapterFault, null, null);
        }

    }

}
