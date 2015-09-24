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

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSController;

@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({"UCS", "address", "route", "content"})
@CapabilityDescription("")
public class UCSRouteMessageByServiceId extends AbstractProcessor {

    public static final String ROUTE_ATTRIBUTE_KEY = "UCSRouteMessageByServiceId.Route";
    public static final String ROUTE_ATTRIBUTE_SERVICE_ID = "UCSRouteMessageByServiceId.ServiceId";

    public static final PropertyDescriptor UCS_CONTROLLER_SERVICE = new PropertyDescriptor.Builder()
            .name("UCS Controller Service")
            .description("The UCS Controller Service that this Processor uses behind the scenes.")
            .identifiesControllerService(UCSController.class)
            .required(true)
            .build();

    public static final PropertyDescriptor REL_NO_MATCH_AS_EXCEPTION = new PropertyDescriptor.Builder()
            .name("Treat 'unmatched' as exception")
            .description("If true, the flowfiles transfered to 'unmatched' relationship will contain all the necessary attributes required by UCSCreateException processor.")
            .required(true)
            .defaultValue("true")
            .allowableValues("true", "false")
            .addValidator(Validator.VALID)
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();
    public static final Relationship REL_ORIGINAL = new Relationship.Builder().name("original")
            .description("The original FlowFile will be sent through this relationships.").build();
    public static final Relationship REL_NO_MATCH = new Relationship.Builder().name("unmatched")
            .description("FlowFiles that do not match any of the user-supplied named relationships").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_ORIGINAL);
        relationships.add(REL_NO_MATCH);
        relationships.add(REL_FAILURE);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(UCS_CONTROLLER_SERVICE);
        properties.add(REL_NO_MATCH_AS_EXCEPTION);
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
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
        if (propertyDescriptorName.equals(REL_NO_MATCH.getName())) {
            return null;
        }

        return new PropertyDescriptor.Builder()
                .required(false)
                .name(propertyDescriptorName)
                .addValidator(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR)
                .addValidator(new EmptyValidator())
                .dynamic(true)
                .expressionLanguageSupported(false)
                .build();
    }

    @Override
    public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue) {
        if (descriptor.isDynamic()) {
            final Set<Relationship> relationships = new HashSet<>(this.relationships.get());
            final Relationship relationship = new Relationship.Builder().name(descriptor.getName()).build();

            if (newValue == null) {
                relationships.remove(relationship);
            } else {
                relationships.add(relationship);
            }

            this.relationships.set(relationships);
        }
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

            final boolean treatNoMatchAsException = context.getProperty(REL_NO_MATCH_AS_EXCEPTION).asBoolean();

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
                        "Error deserializing FlowFile: " + errorHolder.get().getCause().getMessage(),
                        ExceptionType.InvalidMessage,
                        null,
                        null);
                continue;
            }

            Message message = messageWrapperHolder.get().getMessage();

            //separate each Recipient according to its serviceId
            Map<String, List<Recipient>> recipientsByServiceId = message.getHeader().getRecipientsList().stream()
                    .collect(Collectors.groupingBy(r -> r.getDeliveryAddress().getPhysicalAddress().getServiceId()));

            //Send each service type trhough the configured relationship (if any).
            ObjectHolder<FlowFile> originalFlowFile = new ObjectHolder<>(flowFile);
            recipientsByServiceId.entrySet().stream()
                    .forEach(e -> {

                        String serviceId = e.getKey();

                        Optional<Relationship> rel = this.relationships.get().stream()
                        .filter(r -> r.getName().equals(serviceId))
                        .findFirst();

                        if (!rel.isPresent() && treatNoMatchAsException) {
                            UCSCreateException.routeFlowFileToException(
                                    context,
                                    session,
                                    logger,
                                    session.clone(flowFile),
                                    REL_NO_MATCH,
                                    null,
                                    "No Service configured for id '"+serviceId+"'",
                                    ExceptionType.UnknownService,
                                    null,
                                    null);
                        } else {
                            Relationship targetRelationship = rel.isPresent() ? rel.get() : REL_NO_MATCH;

                            Map<String, String> attributes = new HashMap<>();
                            attributes.put(ROUTE_ATTRIBUTE_KEY, targetRelationship.getName());
                            attributes.put(ROUTE_ATTRIBUTE_SERVICE_ID, serviceId);

                            FlowFile clone = session.clone(originalFlowFile.get());
                            clone = session.putAllAttributes(clone, attributes);
                            session.getProvenanceReporter().route(clone, targetRelationship);
                            session.transfer(clone, targetRelationship);
                            logger.info("Cloning {} to {} and routing clone to {}", new Object[]{originalFlowFile.get(), clone, targetRelationship.getName()});
                        }
                    }
                    );

            //Send Original FlowFile to REL_ORIGINAL
            session.transfer(flowFile, REL_ORIGINAL);
            logger.info("Routing {} to {}", new Object[]{flowFile, REL_ORIGINAL});

        }

    }

    private static class EmptyValidator implements Validator {

        @Override
        public ValidationResult validate(final String subject, final String input, final ValidationContext validationContext) {
            return new ValidationResult.Builder().input(input).subject(subject).valid(input.isEmpty()).explanation("Must be emtpy").build();
        }
    }
}
