package com.wisestep.emailing.config;

import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private QuartzProperties quartzProperties; // Spring Boot's auto-configured properties

    // Optional: If using JDBC JobStore, you might need DataSource
    // @Autowired
    // private DataSource dataSource;

    @Bean
    public AutowiringSpringBeanJobFactory springBeanJobFactory() {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean schedulerFactoryBean = new SchedulerFactoryBean();
        schedulerFactoryBean.setJobFactory(springBeanJobFactory());

        // Apply properties from application.properties (spring.quartz.*)
        Properties props = new Properties();
        props.putAll(quartzProperties.getProperties());
        schedulerFactoryBean.setQuartzProperties(props);

        // If using JDBC JobStore, configure DataSource
        // schedulerFactoryBean.setDataSource(dataSource);

        // Other common configurations:
        // schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(true);
        // schedulerFactoryBean.setOverwriteExistingJobs(true); // Useful for development if job definitions change
        // schedulerFactoryBean.setStartupDelay(5); // Delay scheduler startup by 5 seconds

        return schedulerFactoryBean;
    }

    // Expose the Scheduler itself as a bean for direct injection if needed elsewhere
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory) throws Exception {
        Scheduler scheduler = factory.getScheduler();
        // You could start it manually here if autoStartup is false, but SchedulerFactoryBean handles it.
        // if (!scheduler.isStarted()) {
        //     scheduler.start();
        // }
        return scheduler;
    }
}
