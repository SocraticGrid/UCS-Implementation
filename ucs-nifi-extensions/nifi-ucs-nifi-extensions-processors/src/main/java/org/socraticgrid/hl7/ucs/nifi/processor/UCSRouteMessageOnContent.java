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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.behavior.DynamicRelationship;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.AttributeValueDecorator;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.ObjectHolder;
import org.socraticgrid.hl7.services.uc.exceptions.ExceptionType;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.common.model.MessageWrapper;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializationException;
import org.socraticgrid.hl7.ucs.nifi.common.serialization.MessageSerializer;

/**
 * 
 * @author BasitAzeem
 *
 */
@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({ "route", "content", "regex", "regular expression", "regexp", "sms",
		"chat", "message" })
@CapabilityDescription("Applies Regular Expressions to the deserialized content (Message) of a FlowFile and routes a copy of the FlowFile to each "
		+ "destination whose Regular Expression matches. Regular Expressions are added as User-Defined Properties where the name "
		+ "of the property is the name of the relationship and the value is a Regular Expression to match against the FlowFile "
		+ "content. User-Defined properties do support the Attribute Expression Language, but the results are interpreted as "
		+ "literal values, not Regular Expressions")
@DynamicProperty(name = "Relationship Name", value = "A Regular Expression", supportsExpressionLanguage = true, description = "Routes FlowFiles whose "
		+ "content matches the regular expressoin defined by Dynamic Property's value to the Relationship defined by the Dynamic Property's key")
@DynamicRelationship(name = "Name from Dynamic Property", description = "FlowFiles that match the Dynamic Property's Regular Expression")
public class UCSRouteMessageOnContent extends AbstractProcessor {

	public static final String ROUTE_ATTRIBUTE_KEY = "UCSRouteMessageOnContent.Route";

	public static final String MATCH_ALL = "content must match exactly";
	public static final String MATCH_SUBSEQUENCE = "content must contain match";
	public static final String TRUE = "true";
	public static final String FALSE = "false";

	public static final PropertyDescriptor MATCH_REQUIREMENT = new PropertyDescriptor.Builder()
			.name("Match Requirement")
			.description(
					"Specifies whether the entire content of the file must match the regular expression exactly, or if any part of the file (up to Content Buffer Size) can contain the regular expression in order to be considered a match")
			.required(true).allowableValues(MATCH_ALL, MATCH_SUBSEQUENCE)
			.defaultValue(MATCH_ALL).build();

	public static final PropertyDescriptor CASE_SENSITIVE = new PropertyDescriptor.Builder()
			.name("Case Sensitive Match")
			.description(
					"Specifies whether the entire content of the file must match the regular expression with case sensitvity or not.")
			.required(true).allowableValues(TRUE, FALSE).defaultValue(FALSE)
			.build();

	public static final PropertyDescriptor CHARACTER_SET = new PropertyDescriptor.Builder()
			.name("Character Set")
			.description("The Character Set in which the file is encoded")
			.required(true)
			.addValidator(StandardValidators.CHARACTER_SET_VALIDATOR)
			.defaultValue("UTF-8").build();

	public static final Relationship REL_NO_MATCH = new Relationship.Builder()
			.name("unmatched")
			.description(
					"FlowFiles that do not match any of the user-supplied regular expressions will be routed to this relationship")
			.build();

	public static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure").description("").build();

	private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();
	private List<PropertyDescriptor> properties;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final Set<Relationship> relationships = new HashSet<>();
		relationships.add(REL_NO_MATCH);
		relationships.add(REL_FAILURE);
		this.relationships.set(Collections.unmodifiableSet(relationships));

		final List<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(MATCH_REQUIREMENT);
		properties.add(CASE_SENSITIVE);
		properties.add(CHARACTER_SET);
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
	protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(
			final String propertyDescriptorName) {
		if (propertyDescriptorName.equals(REL_NO_MATCH.getName())) {
			return null;
		}

		return new PropertyDescriptor.Builder()
				.required(false)
				.name(propertyDescriptorName)
				.addValidator(
						StandardValidators.createRegexValidator(0,
								Integer.MAX_VALUE, true)).dynamic(true)
				.expressionLanguageSupported(true).build();
	}

	@Override
	public void onPropertyModified(final PropertyDescriptor descriptor,
			final String oldValue, final String newValue) {
		if (descriptor.isDynamic()) {
			final Set<Relationship> relationships = new HashSet<>(
					this.relationships.get());
			final Relationship relationship = new Relationship.Builder().name(
					descriptor.getName()).build();

			if (newValue == null) {
				relationships.remove(relationship);
			} else {
				relationships.add(relationship);
			}

			this.relationships.set(relationships);
		}
	}

	@Override
	public void onTrigger(final ProcessContext context,
			final ProcessSession session) {
		final List<FlowFile> flowFiles = session.get(1);
		if (flowFiles.isEmpty()) {
			return;
		}

		final AttributeValueDecorator quoteDecorator = new AttributeValueDecorator() {
			@Override
			public String decorate(final String attributeValue) {
				return (attributeValue == null) ? null : Pattern
						.quote(attributeValue);
			}
		};

		final Map<FlowFile, Set<Relationship>> flowFileDestinationMap = new HashMap<>();
		final ProcessorLog logger = getLogger();

		boolean caseSensitive = context.getProperty(CASE_SENSITIVE).asBoolean();

		for (final FlowFile flowFile : flowFiles) {
			final Set<Relationship> destinations = new HashSet<>();
			flowFileDestinationMap.put(flowFile, destinations);

			final ObjectHolder<Throwable> errorHolder = new ObjectHolder<>(null);
			final ObjectHolder<MessageWrapper> messageWrapperHolder = new ObjectHolder<>(
					null);

			session.read(
					flowFile,
					(final InputStream rawIn) -> {
						try {
							messageWrapperHolder.set(MessageSerializer
									.deserializeMessageWrapper(rawIn));
						} catch (MessageSerializationException ex) {
							errorHolder
									.set(new RuntimeException(
											"Error deserializing FlowFile content into a MessageWrapper instance. Routing to FAILURE",
											ex));
						}
					});

			if (errorHolder.get() != null) {
				logger.error(errorHolder.get().getMessage(), errorHolder.get()
						.getCause());
				UCSCreateException.routeFlowFileToException(context, session,
						logger, flowFile, REL_FAILURE, null,
						"Error deserializing FlowFile: "
								+ errorHolder.get().getCause(),
						ExceptionType.InvalidMessage, null, null);
				flowFileDestinationMap.remove(flowFile);
				continue;
			}

			Message message = messageWrapperHolder.get().getMessage();

			// MessageSerializer
			for (final PropertyDescriptor descriptor : context.getProperties()
					.keySet()) {
				if (!descriptor.isDynamic()) {
					continue;
				}

				final String regex = context.getProperty(descriptor)
						.evaluateAttributeExpressions(flowFile, quoteDecorator)
						.getValue();
				Pattern pattern = Pattern.compile(regex);
				if (!caseSensitive) {
					pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
				}
				final boolean matches;
				if (context.getProperty(MATCH_REQUIREMENT).getValue()
						.equalsIgnoreCase(MATCH_ALL)) {
					matches = pattern.matcher(
							message.getParts()[0].getContent()).matches();
				} else {
					matches = pattern.matcher(
							message.getParts()[0].getContent()).find();
				}

				if (matches) {
					final Relationship relationship = new Relationship.Builder()
							.name(descriptor.getName()).build();
					destinations.add(relationship);
				}
			}
		}

		for (final Map.Entry<FlowFile, Set<Relationship>> entry : flowFileDestinationMap
				.entrySet()) {
			FlowFile flowFile = entry.getKey();
			final Set<Relationship> destinations = entry.getValue();

			if (destinations.isEmpty()) {
				flowFile = session.putAttribute(flowFile, ROUTE_ATTRIBUTE_KEY,
						REL_NO_MATCH.getName());
				session.transfer(flowFile, REL_NO_MATCH);
				session.getProvenanceReporter().route(flowFile, REL_NO_MATCH);
				logger.info("Routing {} to 'unmatched'",
						new Object[] { flowFile });
			} else {
				final Relationship firstRelationship = destinations.iterator()
						.next();
				destinations.remove(firstRelationship);

				for (final Relationship relationship : destinations) {
					FlowFile clone = session.clone(flowFile);
					clone = session.putAttribute(clone, ROUTE_ATTRIBUTE_KEY,
							relationship.getName());
					session.getProvenanceReporter().route(clone, relationship);
					session.transfer(clone, relationship);
					logger.info("Cloning {} to {} and routing clone to {}",
							new Object[] { flowFile, clone, relationship });
				}

				flowFile = session.putAttribute(flowFile, ROUTE_ATTRIBUTE_KEY,
						firstRelationship.getName());
				session.getProvenanceReporter().route(flowFile,
						firstRelationship);
				session.transfer(flowFile, firstRelationship);
				logger.info("Routing {} to {}", new Object[] { flowFile,
						firstRelationship });
			}
		}
	}
}
