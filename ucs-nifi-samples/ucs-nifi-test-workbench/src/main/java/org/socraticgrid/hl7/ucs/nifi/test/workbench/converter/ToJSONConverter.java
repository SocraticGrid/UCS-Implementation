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
package org.socraticgrid.hl7.ucs.nifi.test.workbench.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.socraticgrid.hl7.services.uc.exceptions.ProcessingException;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.Conversation;
import org.socraticgrid.hl7.services.uc.model.ConversationInfo;
import org.socraticgrid.hl7.services.uc.model.DeliveryAddress;
import org.socraticgrid.hl7.services.uc.model.DeliveryStatus;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.ServiceInfo;
import org.socraticgrid.hl7.services.uc.model.Status;

/**
 *
 * @author esteban
 */
public class ToJSONConverter {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

    public static JsonObject toJsonObject(Message message) {
        if(message instanceof AlertMessage){
            return ToJSONConverter.alertMessageToJsonObject((AlertMessage) message);
        }
        
        return ToJSONConverter.messageToJsonObject(message);
    }
    
    public static JsonObject toJsonObject(Conversation conversation) {
        JsonObject result = new JsonObject();
        
        result.addProperty("id", conversation.getConversationId());
        
        return result;
    }
    
    public static JsonObject toJsonObject(ConversationInfo conversationInfo) {
        JsonObject result = new JsonObject();
        
        result.addProperty("conversationId", conversationInfo.getConversation().getConversationId());
        
        JsonArray messages = new JsonArray();
        if (conversationInfo.getMessages() != null){
            conversationInfo.getMessages().stream()
                .map(JsonPrimitive::new)
                .forEach(messages::add);
        }
        result.add("messages", messages);
        
        return result;
    }
    
    private static JsonObject alertMessageToJsonObject(AlertMessage message) {
        JsonObject result = ToJSONConverter.messageToJsonObject(message);
        
        result.addProperty("status", message.getHeader().getAlertStatus().name());
        
        JsonArray properties = new JsonArray();
        if (message.getHeader().getProperties() != null){
            for (Map.Entry<Object, Object> entry : message.getHeader().getProperties().entrySet()) {
                JsonObject prop = new JsonObject();
                prop.addProperty("key", (String)entry.getKey());
                prop.addProperty("value", (String)entry.getValue());
                properties.add(prop);
            }
        }
        result.add("properties", properties);
        
        return result;
    }
    
    private static JsonObject messageToJsonObject(Message message) {
        JsonObject result = new JsonObject();

        JsonObject from = toJsonObject(message.getHeader().getSender());
        String timestamp = "<unknown>";
        if (message.getHeader().getCreated() != null) {
            Instant instant = Instant.ofEpochMilli(message.getHeader().getCreated().getTime());
            LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            timestamp = formatter.format(localDateTime);
        }
        String body = "<empty>";
        String bodyMime = "text/plain";
        if (message.getParts() != null && message.getParts().length > 0) {
            body = message.getParts()[0].getContent();
            bodyMime = message.getParts()[0].getType();
        }
        
        String subject = message.getHeader().getSubject();

        JsonArray services = new JsonArray();
        message.getHeader().getRecipientsList().stream()
                .map(r -> r.getDeliveryAddress().getPhysicalAddress().getServiceId())
                .sorted()
                .distinct()
                .map(JsonPrimitive::new)
                .forEach(services::add);
        result.add("services", services);
        
        result.add("sender", from);
        result.addProperty("messageId", message.getHeader().getMessageId());
        result.addProperty("conversationId", message.getHeader().getRelatedConversationId());
        result.addProperty("relatedMessageId", message.getHeader().getRelatedMessageId());
        result.addProperty("timestamp", timestamp);
        result.addProperty("bodyMime", bodyMime);
        result.addProperty("subject", subject != null ? subject : "<none>");
        result.addProperty("body", body);

        Map<String, List<DeliveryStatus>> statuses = new HashMap<>();
        if (message.getHeader().getDeliveryStatusList() != null){
            statuses.putAll(message.getHeader().getDeliveryStatusList().stream()
                .collect(Collectors.groupingBy(ds -> ds.getRecipient().getRecipientId()))
            );
        }
        
        
        final JsonArray recipients = new JsonArray();
        message.getHeader().getRecipientsList().stream()
                .map(r -> toJsonObject(r.getDeliveryAddress(), statuses.get(r.getRecipientId())))
                .forEach(recipients::add);
                
        result.add("recipients", recipients);
        
        
        return result;
    }

    public static JsonObject toJsonObject(DeliveryAddress address) {
        return toJsonObject(address, null);
    }
    
    public static JsonObject toJsonObject(DeliveryAddress address, List<DeliveryStatus> statuses) {
        if (address == null){
            return new JsonObject();
        }
        
        String who = address.getPhysicalAddress().getAddress();
        String type = address.getPhysicalAddress().getServiceId();

        JsonObject addressAsJson = new JsonObject();
        addressAsJson.addProperty("who", who);
        addressAsJson.addProperty("type", type);
        addressAsJson.addProperty("status", statuses == null? "<unknown>" : statuses.get(0).getAction()+": "+statuses.get(0).getStatus());
        
        return addressAsJson;
    }
    
    public static JsonObject toJsonObject(ProcessingException e) {
        JsonObject result = new JsonObject();
        
        result.addProperty("exceptionId", e.getProcessingExceptionId());
        result.addProperty("messageId", e.getGeneratingMessageId());
        result.addProperty("fault", e.getFault());
        result.addProperty("context", e.getTypeSpecificContext());
        result.addProperty("serviceId", e.getIssuingService());
        result.addProperty("type", e.getExceptionType().name());
        
        return result;
    }
    
    public static JsonObject toJsonObject(ServiceInfo si) {
        JsonObject result = new JsonObject();
        result.addProperty("name", si.getServiceName());
        return result;
    }
    
    public static JsonObject toJsonObject(Status s) {
        JsonObject result = new JsonObject();
        result.addProperty("capability", s.getCapability());
        result.addProperty("available", s.isAvailable());
        result.addProperty("supported", s.isSupported());
        return result;
    }
    
}
