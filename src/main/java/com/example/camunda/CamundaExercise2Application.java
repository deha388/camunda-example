package com.example.camunda;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

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
                    .jobType("issue_invoice")
                    .handler(CamundaExercise2Application::handleIssueInvoiceJob)
                    .open();

            System.out.println("Workers are ready to handle jobs...");

            System.out.println("Starting a process instance...");
            zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId("sid-BA5951D1-59EB-47F9-845C-86A953A2E19E") // BPMN Process ID
                    .latestVersion()
                    .variables("{\"key\":\"value\", \"invoice_number\": \"INV-12345\"}")
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

    private static void handleIssueInvoiceJob(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        System.out.println("Received Variables: " + variables);

        Object amountObj = variables.get("number_8z5gkd");

        String invoiceNumber = "INV-STATIC-001";

        if (amountObj == null) {
            System.err.println("Required amount variable is missing.");
            jobClient.newFailCommand(job.getKey())
                    .retries(0)
                    .errorMessage("Required amount variable is missing")
                    .send()
                    .join();
            return;
        }

        double amount = Double.parseDouble(amountObj.toString());

        System.out.println("Processing Issue Invoice job: " + job.getKey());
        System.out.println("Invoice Number: " + invoiceNumber);
        System.out.println("Invoice Amount: " + amount);

        jobClient.newCompleteCommand(job.getKey()).send().join();
        System.out.println("Issue Invoice job completed successfully!");
    }

}
