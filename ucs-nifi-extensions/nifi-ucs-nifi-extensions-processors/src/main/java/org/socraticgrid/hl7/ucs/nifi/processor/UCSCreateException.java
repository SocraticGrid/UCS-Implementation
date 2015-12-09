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
package org.socraticgrid.hl7.ucs.nifi.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.AttributeExpression;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.ExceptionWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.ExceptionWrapperSerializer;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 *
 * @author esteban
 */
@EventDriven
@Tags({"UCS", "UCSClient", "Exception"})
@CapabilityDescription("Creates an instance of ExceptionWrapper and put it as the content of the outgoing flow.")
public class UCSCreateException extends AbstractProcessor {
    public static final String TYPE_DEFAULT_ATTRIBUTE_NAME = "ucs.exception.type";
    public static final String FAULT_DEFAULT_ATTRIBUTE_NAME = "ucs.exception.fault";
    public static final String SERVER_ID_DEFAULT_ATTRIBUTE_NAME = "ucs.exception.serverId";
    public static final String CONTEXT_DEFAULT_ATTRIBUTE_NAME = "ucs.exception.context";
    public static final String RECEIVER_ID_DEFAULT_ATTRIBUTE_NAME = "ucs.exception.receiverId";

    public static final PropertyDescriptor TYPE = new PropertyDescriptor.Builder()
            .name("Type")
            .description("The type of exception. Used as the processingException.exceptionType of the generated ExceptionWrapper.")
            .required(true)
            .allowableValues(Arrays.asList(ExceptionType.values()).stream().map(et -> et.name()).collect(Collectors.toSet()))
            .defaultValue(ExceptionType.General.name())
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor TYPE_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("Type Attribute")
            .description("If we don't want to hardcode the TYPE of the exception using the 'Type' attribute, we can specify an expression here to get the Type.")
            .required(false)
            .defaultValue("${"+TYPE_DEFAULT_ATTRIBUTE_NAME+"}")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
            .build();
    
    public static final PropertyDescriptor FAULT = new PropertyDescriptor.Builder()
            .name("Fault")
            .description("Used as the processingException.fault of the generated ExceptionWrapper.")
            .defaultValue("${"+FAULT_DEFAULT_ATTRIBUTE_NAME+"}")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
            .build();

    public static final PropertyDescriptor SERVER_ID = new PropertyDescriptor.Builder()
            .name("Server Id")
            .description("Used as the serverId of the generated ExceptionWrapper.")
            .defaultValue("${"+SERVER_ID_DEFAULT_ATTRIBUTE_NAME+"}")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
            .build();

    public static final PropertyDescriptor CONTEXT = new PropertyDescriptor.Builder()
            .name("Context")
            .description("A descriptive context about the exception. Used as the processingException.typeSpecificContext of the generated ExceptionWrapper.")
            .defaultValue("${"+CONTEXT_DEFAULT_ATTRIBUTE_NAME+"}")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
            .build();

    public static final PropertyDescriptor RECEIVER_ID = new PropertyDescriptor.Builder()
            .name("Receiver Id")
            .description("If the exception was originated by a specific receiver, this attribute specifies its id.")
            .required(false)
            .defaultValue("${"+RECEIVER_ID_DEFAULT_ATTRIBUTE_NAME+"}")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("").build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        this.relationships.set(Collections.unmodifiableSet(relationships));

        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(TYPE);
        properties.add(TYPE_ATTRIBUTE);
        properties.add(FAULT);
        properties.add(SERVER_ID);
        properties.add(CONTEXT);
        properties.add(RECEIVER_ID);
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
        if (flowFile == null) {
            return;
        }

        final ProcessorLog logger = getLogger();

        String type = context.getProperty(TYPE).getValue();
        String fault = context.getProperty(FAULT).evaluateAttributeExpressions(flowFile).getValue();
        String serverId = context.getProperty(SERVER_ID).evaluateAttributeExpressions(flowFile).getValue();
        String exceptionContext = context.getProperty(CONTEXT).evaluateAttributeExpressions(flowFile).getValue();

        if (context.getProperty(TYPE_ATTRIBUTE).isSet() && !StringUtils.isEmpty(context.getProperty(TYPE_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue())){
            logger.debug("Type Attribute is set to {} and it resolves to {}", new Object[]{context.getProperty(TYPE_ATTRIBUTE).getValue(), context.getProperty(TYPE_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue()});
            //override type
            type= context.getProperty(TYPE_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue();
        }
        logger.debug("Type to be used: "+type);
        
        final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
        final ObjectHolder<MessageWrapper> messageWrapperHolder = new ObjectHolder<>(null);

        session.read(flowFile, (final InputStream rawIn) -> {
            try {
                messageWrapperHolder.set(MessageSerializer.deserializeMessageWrapper(rawIn));
            } catch (MessageSerializationException ex) {
                //this should never happen
                logger.error("Error deserializing FlowFile content into a MessageWrapper instance: {}.", new Object[]{ex.getMessage()}, ex);
                errorHolder.set(new RuntimeException("Error deserializing FlowFile content into a MessageWrapper instance: " + ex.getMessage(), ex));
            }
        });

        if (errorHolder.get() != null) {
            logger.error(errorHolder.get().getMessage(), errorHolder.get().getCause());
            session.transfer(flowFile, REL_FAILURE);
            session.getProvenanceReporter().route(flowFile, REL_FAILURE);
            return;
        }

        Message message = messageWrapperHolder.get().getMessage();

        DeliveryAddress receiver = null;
        if (context.getProperty(RECEIVER_ID).isSet()) {
            String receiverId = context.getProperty(RECEIVER_ID).evaluateAttributeExpressions(flowFile).getValue();
            receiver = message.getHeader().getRecipientsList().stream()
                    .filter(r -> r.getRecipientId().equals(receiverId))
                    .map(r -> r.getDeliveryAddress())
                    .findFirst().orElse(null);
        }

        ProcessingException pe = new ProcessingException();
        pe.setFault(fault);
        pe.setTypeSpecificContext(exceptionContext);
        pe.setExceptionType(ExceptionType.valueOf(type));
        pe.setIssuingService(serverId);
        pe.setProcessingExceptionId(UUID.randomUUID().toString());
        pe.setGeneratingMessageId(message.getHeader().getMessageId());

        final ExceptionWrapper ew = new ExceptionWrapper();
        ew.setServerId(serverId);
        ew.setMessage(message);
        ew.setProcessingException(pe);
        ew.setSender(message.getHeader().getSender());
        ew.setReceiver(receiver);

        flowFile = session.write(flowFile, new OutputStreamCallback() {
            @Override
            public void process(final OutputStream out) throws IOException {
                try {
                    out.write(ExceptionWrapperSerializer.serializeExceptionWrapper(ew).getBytes());
                } catch (MessageSerializationException ex) {
                    logger.error("Error serializing ExceptionWrapper instance: {}.", new Object[]{ex.getMessage()}, ex);
                    errorHolder.set(new RuntimeException("Error serializing ExceptionWrapper instance: " + ex.getMessage(), ex));
                }
            }
        });

        if (errorHolder.get() != null) {
            logger.error(errorHolder.get().getMessage(), errorHolder.get().getCause());
            session.transfer(flowFile, REL_FAILURE);
            session.getProvenanceReporter().route(flowFile, REL_FAILURE);
            return;
        }
        
        logger.debug("ExceptionWrapper created and written as FlowFile content. Routing FlowFile to {}", new Object[]{REL_SUCCESS});
        session.transfer(flowFile, REL_SUCCESS);
        session.getProvenanceReporter().route(flowFile, REL_SUCCESS);
    }
    
    /**
     * Utility method to set all the required attributes to a FlowFile
     * and route it to an instance of UCSCreateException processor.
     * @param context
     * @param session
     * @param logger
     * @param flowFile
     * @param target
     * @param serverId
     * @param fault
     * @param type
     * @param exceptionContext
     * @param receiverId
     * @return 
     */
    public static FlowFile routeFlowFileToException(
            final ProcessContext context, 
            final ProcessSession session, 
            final ProcessorLog logger, 
            FlowFile flowFile, 
            final Relationship target,
            final String serverId,
            final String fault,
            final ExceptionType type,
            final String exceptionContext,
            final String receiverId){
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVER_ID_DEFAULT_ATTRIBUTE_NAME, serverId);
        attributes.put(FAULT_DEFAULT_ATTRIBUTE_NAME, fault);
        attributes.put(TYPE_DEFAULT_ATTRIBUTE_NAME, type.name());
        attributes.put(CONTEXT_DEFAULT_ATTRIBUTE_NAME, exceptionContext);
        attributes.put(RECEIVER_ID_DEFAULT_ATTRIBUTE_NAME, receiverId);
        
        flowFile = session.putAllAttributes(flowFile, attributes);
        session.getProvenanceReporter().modifyAttributes(flowFile);
        
        logger.debug("Exception attributes set. Routing FlowFile to {}", new Object[]{target});
        session.transfer(flowFile, target);
        session.getProvenanceReporter().route(flowFile, target);
        
        return flowFile;
    }

}
