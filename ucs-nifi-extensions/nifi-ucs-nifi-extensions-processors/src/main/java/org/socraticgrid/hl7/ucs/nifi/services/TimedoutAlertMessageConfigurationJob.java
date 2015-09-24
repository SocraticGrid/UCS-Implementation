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

import java.util.List;
import java.util.Optional;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.socraticgrid.hl7.services.uc.model.AlertMessage;
import org.socraticgrid.hl7.services.uc.model.AlertStatus;
import org.socraticgrid.hl7.services.uc.model.Message;
import org.socraticgrid.hl7.ucs.nifi.controller.UCSControllerService;
import org.socraticgrid.hl7.ucs.nifi.services.TimedOutMessage.TimeOutType;

/**
 * Job that represents a TimedoutAlertMessageConfiguration.
 *
 * @author Pavan
 */
public class TimedoutAlertMessageConfigurationJob implements Job {

    public static JobDetail createJobDetail(String messageId, UCSControllerService service) {
        JobDataMap map = new JobDataMap();
        map.put("messageId", messageId);
        map.put("service", service);

        return JobBuilder
                .newJob(TimedoutAlertMessageConfigurationJob.class)
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
        String messageId = (String) context.getMergedJobDataMap().get("messageId");
        UCSControllerService service = (UCSControllerService) context.getMergedJobDataMap().get("service");

        Optional<Message> message = service.getMessageById(messageId);

        if (!message.isPresent()) {
            //the message is no longer in the system.
            return;
        }

        AlertMessage alertMessage = (AlertMessage) message.get();
        if (AlertStatus.Pending == alertMessage.getHeader().getAlertStatus()) {
            List<Message> onNoResponseAll = alertMessage.getHeader().getOnNoResponseAll();
            
            if (onNoResponseAll != null && onNoResponseAll.size() > 0) {
                timedOutMessage = new TimedOutMessage();
                timedOutMessage.setMessage(message.get());
                timedOutMessage.setTimeOutReason(TimeOutType.NO_RESPONSES);
                service.notifyAboutMessageWithResponseTimeout(timedOutMessage);
            }

        }
    }

}
