package com.example.camunda;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CamundaExercise2Application {

    public static void main(String[] args) {
        var context = SpringApplication.run(CamundaExercise2Application.class, args);

        ZeebeClient zeebeClient = context.getBean(ZeebeClient.class);

        System.out.println("Testing connection to Camunda SaaS...");
        try {
            zeebeClient.newTopologyRequest().send().join();
            System.out.println("Successfully connected to Camunda SaaS!");

            zeebeClient.newWorker()
                    .jobType("print-hello") // BPMN Model'deki Job Type ile aynı olmalı
                    .handler(CamundaExercise2Application::handlePrintHelloJob)
                    .open();

            System.out.println("Worker is ready to handle jobs...");

            System.out.println("Starting a process instance...");
            zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId("Process_1f1wxpi") // BPMN Process ID
                    .latestVersion()
                    .variables("{\"key\":\"value\"}")
                    .send()
                    .join();
            System.out.println("Process instance started successfully!");

            Thread.sleep(60000);
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }

    private static void handlePrintHelloJob(JobClient jobClient, ActivatedJob job) {
        System.out.println("Processing job: " + job.getKey());
        System.out.println("Hello, this is your print task!");

        jobClient.newCompleteCommand(job.getKey()).send().join();
        System.out.println("Job completed successfully!");
    }
}
