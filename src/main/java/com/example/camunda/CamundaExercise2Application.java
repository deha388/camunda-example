package com.example.camunda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@SpringBootApplication
public class CamundaExercise2Application {

    private static final String DB_FILE_PATH = "db.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

            zeebeClient.newWorker()
                    .jobType("termin_scheduler")
                    .handler(CamundaExercise2Application::handleTerminSchedulerJob)
                    .open();

            System.out.println("Workers are ready to handle jobs...");

            System.out.println("Starting a process instance...");
            zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId("sid-BA5951D1-59EB-47F9-845C-86A953A2E19E") // BPMN Process ID
                    .latestVersion()
                    .variables("{\"key\":\"value\", \"invoice_number\": \"5000\", \"invoice_id\": \"12345\", \"number_8z5gkd\":\"1234.56\", \"number_nhd58o\":\"6000\", \"textfield_19ik9\":\"express\", \"selectedProducts\":[\"product3\"]}")
                    .send()
                    .join();
            System.out.println("Process instance started successfully!");

            Thread.sleep(60000);
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }

    private static void handleIssueInvoiceJob(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        Object amountObj = variables.get("number_8z5gkd");

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

        String invoiceAmount = "INV-STATIC-001";
        String invoiceNumber = addInvoiceToDb(invoiceAmount, amount);

        System.out.println("Processing Issue Invoice job: " + job.getKey());
        System.out.println("Invoice Number: " + invoiceNumber);
        System.out.println("Invoice Amount: " + amount);

        jobClient.newCompleteCommand(job.getKey()).send().join();
        System.out.println("Issue Invoice job completed successfully!");
    }

    private static void handleTerminSchedulerJob(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        System.out.println("Received Termin Variables: " + variables);

        variables.forEach((key, val) -> System.out.println(key + ": " + val));

        Object cargoObj = variables.get("number_nhd58o");
        double cargoGrams = 0.0;
        if (cargoObj != null) {
            cargoGrams = Double.parseDouble(cargoObj.toString());
        }

        addTerminToDb(cargoGrams);

        jobClient.newCompleteCommand(job.getKey()).send().join();
        System.out.println("Termin Scheduler job completed successfully!");
    }

    private static void addTerminToDb(double cargoGrams) {
        File dbFile = new File(DB_FILE_PATH);
        JsonNode rootNode;
        try {
            if (dbFile.exists()) {
                rootNode = objectMapper.readTree(dbFile);
            } else {
                rootNode = objectMapper.createObjectNode();
            }

            if (rootNode.get("termin") == null || !rootNode.get("termin").isArray()) {
                ((ObjectNode) rootNode).set("termin", objectMapper.createArrayNode());
            }

            ArrayNode terminArray = (ArrayNode) rootNode.get("termin");

            ObjectNode newTerminNode = objectMapper.createObjectNode();

            Instant now = Instant.now();
            Instant oneHourLater = now.plus(1, ChronoUnit.HOURS);
            String scheduledTime = DateTimeFormatter.ISO_INSTANT.format(oneHourLater);

            newTerminNode.put("scheduled_time", scheduledTime);
            newTerminNode.put("cargo_grams", cargoGrams);

            terminArray.add(newTerminNode);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dbFile, rootNode);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to write termin to db.json: " + e.getMessage());
        }
    }

    private static String addInvoiceToDb(String invoiceAmount, double amount) {
        File dbFile = new File(DB_FILE_PATH);
        JsonNode rootNode;
        ArrayNode invoiceArray;
        String invoiceNumber;

        try {
            if (dbFile.exists()) {

                rootNode = objectMapper.readTree(dbFile);
                invoiceArray = (ArrayNode) rootNode.get("invoice");

                if (invoiceArray == null) {
                    invoiceArray = objectMapper.createArrayNode();
                    ((ObjectNode) rootNode).set("invoice", invoiceArray);
                }

                if (invoiceArray.size() > 0) {
                    JsonNode lastInvoice = invoiceArray.get(invoiceArray.size() - 1);
                    String lastInvoiceNumberStr = lastInvoice.get("invoice_number").asText();
                    int lastInvoiceNumber = Integer.parseInt(lastInvoiceNumberStr);
                    invoiceNumber = String.valueOf(lastInvoiceNumber + 1);
                } else {
                    invoiceNumber = String.valueOf((int) amount);
                }
            } else {
                rootNode = objectMapper.createObjectNode();
                invoiceArray = objectMapper.createArrayNode();
                ((ObjectNode) rootNode).set("invoice", invoiceArray);

                invoiceNumber = String.valueOf((int) amount);
            }

            ObjectNode newInvoiceNode = objectMapper.createObjectNode();
            newInvoiceNode.put("invoice_number", invoiceNumber);
            newInvoiceNode.put("invoice_amount", invoiceAmount);
            newInvoiceNode.put("amount", amount);
            invoiceArray.add(newInvoiceNode);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dbFile, rootNode);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to write to db.json: " + e.getMessage());
            invoiceNumber = String.valueOf((int) amount);
        }

        return invoiceNumber;
    }
}
