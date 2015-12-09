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
package org.socraticgrid.hl7.ucs.nifi.services;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.quartz.DateBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 *
 * @author pavan
 */
public class JobScheduler { 
    private static final JobScheduler IN_MEMORY_INSTANCE;

    static {
        try { 
            IN_MEMORY_INSTANCE = new JobScheduler("/ram-quartz.properties");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    } 
    public static JobScheduler getInMemoryInstance() {
        return IN_MEMORY_INSTANCE;
    }

    private Scheduler scheduler;
    private final String configFile;

    private JobScheduler(String configFile) throws SchedulerException, IOException {
        this.configFile = configFile;
        this.initScheduler();
    }
    
    private void initScheduler() throws IOException, SchedulerException{
        //read props from file
        Properties props = new Properties();
        props.load(JobScheduler.class.getResourceAsStream(this.configFile));

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        scheduler = factory.getScheduler();
    }

    public void startScheduler() throws SchedulerException, IOException {
        if (scheduler.isShutdown()){
            this.initScheduler();
        }
        if (scheduler.isStarted()){
            return;
        }
        scheduler.start();
    }

    public void schedule(JobDetail job, Trigger trigger) throws SchedulerException {
        scheduler.scheduleJob(job, trigger);
    }
    
    public void scheduleOneTimeJob(JobDetail job, int intervalTime, DateBuilder.IntervalUnit intervalUnit) throws SchedulerException {
        
        Date startDate = DateBuilder.futureDate(intervalTime, intervalUnit);
        
        SimpleTrigger trigger = TriggerBuilder
                .newTrigger()
                .forJob(job)
                .startAt(startDate)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0))
                .build();
        
        scheduler.scheduleJob(job, trigger);
    }
    
    public void deleteJob(JobKey jobKey) throws SchedulerException {
        scheduler.deleteJob(jobKey);
    }
    
    public List<? extends Trigger> getTriggersOfJob(JobKey jobKey) throws SchedulerException{
        return scheduler.getTriggersOfJob(jobKey);
    }
    
    public void triggerJobNow(JobKey jobKey) throws SchedulerException{
        scheduler.triggerJob(jobKey);
    }

    public void stopScheduler() throws SchedulerException {
        scheduler.shutdown();
    }
    
    public ListenerManager getListenerManager() throws SchedulerException{
        return scheduler.getListenerManager();
    }

}
