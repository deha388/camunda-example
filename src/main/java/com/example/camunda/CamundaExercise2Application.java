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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class CamundaExercise2Application {

    private static final String DB_FILE_PATH = "db.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Define a set of keys that should always appear in the db_log entries.
    // These are based on the fields seen in the first record.
    private static final List<String> ALWAYS_INCLUDED_KEYS = Arrays.asList(
            "key",
            "invoice_id",
            "number_8z5gkd",
            "number_nhd58o",
            "invoice_number",
            "textfield_19ik9",
            "selectedProducts"
    );

    public static void main(String[] args) {
        var context = SpringApplication.run(CamundaExercise2Application.class, args);
        ZeebeClient zeebeClient = context.getBean(ZeebeClient.class);
        System.out.println("Testing connection to Camunda SaaS...");
        try {
            zeebeClient.newTopologyRequest().send().join();
            System.out.println("Connected to Camunda SaaS");
            zeebeClient.newWorker()
                    .jobType("issue_invoice")
                    .handler(CamundaExercise2Application::handleIssueInvoiceJob)
                    .open();
            zeebeClient.newWorker()
                    .jobType("termin_scheduler")
                    .handler(CamundaExercise2Application::handleTerminSchedulerJob)
                    .open();
            zeebeClient.newWorker()
                    .jobType("save_to_db")
                    .handler(CamundaExercise2Application::handleSaveToDbJob)
                    .open();
            System.out.println("Workers are ready");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void handleIssueInvoiceJob(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        System.out.println("\033[32mInvoice service triggered.\033[0m");
        System.out.println("\033[32mVariables received for Issue Invoice:\033[0m");

        Object amountObj = variables.get("number_8z5gkd");
        if (amountObj == null) {
            jobClient.newFailCommand(job.getKey())
                    .retries(0)
                    .errorMessage("Required amount variable is missing")
                    .send()
                    .join();
            return;
        }

        double amount = Double.parseDouble(amountObj.toString());
        String invoiceAmount = "INV-STATIC-001";
        addInvoiceToDb(invoiceAmount, amount);

        jobClient.newCompleteCommand(job.getKey()).send().join();
        System.out.println("\033[32mInvoice created.\033[0m");
    }

    private static void handleTerminSchedulerJob(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        System.out.println("\033[32mTermin service triggered.\033[0m");
        System.out.println("\033[32mVariables received for Termin:\033[0m");

        Object cargoObj = variables.get("number_nhd58o");
        double cargoGrams = 0.0;
        if (cargoObj != null) {
            cargoGrams = Double.parseDouble(cargoObj.toString());
        }

        addTerminToDb(cargoGrams);
        jobClient.newCompleteCommand(job.getKey()).send().join();
        System.out.println("\033[32mTermin request created.\033[0m");
    }

    private static void handleSaveToDbJob(JobClient jobClient, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();

        addLogToDb(variables, ALWAYS_INCLUDED_KEYS);
        jobClient.newCompleteCommand(job.getKey()).send().join();
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
            String scheduledTime = DateTimeFormatter.ISO_INSTANT.format(now);
            newTerminNode.put("scheduled_time", scheduledTime);
            newTerminNode.put("cargo_grams", cargoGrams);
            terminArray.add(newTerminNode);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dbFile, rootNode);
        } catch (IOException e) {
            System.err.println("Failed to write termin: " + e.getMessage());
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
            System.err.println("Failed to write invoice: " + e.getMessage());
            invoiceNumber = String.valueOf((int) amount);
        }
        return invoiceNumber;
    }

    private static void addLogToDb(Map<String, Object> variables, List<String> alwaysIncludedKeys) {
        File dbFile = new File(DB_FILE_PATH);
        JsonNode rootNode;
        try {
            if (dbFile.exists()) {
                rootNode = objectMapper.readTree(dbFile);
            } else {
                rootNode = objectMapper.createObjectNode();
            }
            if (rootNode.get("db_log") == null || !rootNode.get("db_log").isArray()) {
                ((ObjectNode) rootNode).set("db_log", objectMapper.createArrayNode());
            }
            ArrayNode dbLogArray = (ArrayNode) rootNode.get("db_log");
            ObjectNode logNode = objectMapper.createObjectNode();
            Instant now = Instant.now();
            String logTime = DateTimeFormatter.ISO_INSTANT.format(now);
            logNode.put("timestamp", logTime);

            for (String key : alwaysIncludedKeys) {
                Object val = variables.get(key);
                logNode.put(key, val == null ? "" : val.toString());
            }

            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (!alwaysIncludedKeys.contains(entry.getKey())) {
                    logNode.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString());
                }
            }

            dbLogArray.add(logNode);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dbFile, rootNode);
        } catch (IOException e) {
            System.err.println("Failed to write db_log: " + e.getMessage());
        }
    }
}
