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
package org.socraticgrid.hl7.ucs.nifi.services;

import java.util.Set;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.services.uc.model.Recipient;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSControllerService;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage.TimeOutType;

/**
 * Job that represents a TimedoutMessageConfiguration.
 *
 * @author Pavan
 */
public class TimedoutMessageConfigurationJob implements Job {

    public static JobDetail createJobDetail(Message message, UCSControllerService service) {
        JobDataMap map = new JobDataMap();
        map.put("message", message);
        map.put("service", service);
        
        return JobBuilder
                .newJob(TimedoutMessageConfigurationJob.class)
                .setJobData(map)
                .build();
    }

    /**
     *
     * @param context
     * @throws JobExecutionException
     */
    @SuppressWarnings("unchecked")
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TimedOutMessage timedOutMessage = null;
        Message message = (Message) context.getMergedJobDataMap().get("message");
        UCSControllerService service = (UCSControllerService) context.getMergedJobDataMap().get("service");

        Set<Message> relatedMessages = service.getRelatedMessages(message.getHeader().getMessageId());

        //if no related messages then create TimedOutMessage with NO_RESPONSES timeout reason
        if (relatedMessages == null || relatedMessages.isEmpty()) {
            timedOutMessage = new TimedOutMessage();
            timedOutMessage.setMessage(message);
            timedOutMessage.setTimeOutReason(TimeOutType.NO_RESPONSES);
            service.notifyAboutMessageWithResponseTimeout(timedOutMessage);
        } else if (relatedMessages.size() < message.getHeader().getRecipientsList().size()) {
            timedOutMessage = new TimedOutMessage();
            timedOutMessage.setMessage(message);
            timedOutMessage.setTimeOutReason(TimeOutType.PARTIAL_RESPONSES);
            service.notifyAboutMessageWithResponseTimeout(timedOutMessage);
        } else {
            Set<Recipient> receipients = message.getHeader().getRecipientsList();
            boolean matched = true;
            for (Recipient recipient : receipients) {
                if (!matched) {
                    timedOutMessage = new TimedOutMessage();
                    timedOutMessage.setMessage(message);
                    timedOutMessage.setTimeOutReason(TimeOutType.PARTIAL_RESPONSES);
                    service.notifyAboutMessageWithResponseTimeout(timedOutMessage);
                    break;
                } else {
                    for (Message msg : relatedMessages) {
                        if (recipient.getDeliveryAddressId().equals(msg.getHeader().getSender().getDeliveryAddressId()) || recipient.getDeliveryAddress().getPhysicalAddress().getAddress().equals(msg.getHeader().getSender().getPhysicalAddress().getAddress())) {
                            matched = true;
                        } else {
                            matched = false;
                        }
                        if (matched) {
                            break;
                        }
                    }
                }
            }

        }
    }

}
