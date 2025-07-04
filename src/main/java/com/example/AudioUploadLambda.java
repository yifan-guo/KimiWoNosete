package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.secretsmanager.*;
import com.amazonaws.services.secretsmanager.model.*;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.StartExecutionRequest;
import com.amazonaws.services.stepfunctions.model.StartExecutionResult;
import org.apache.commons.fileupload.MultipartStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import java.util.Arrays;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class AudioUploadLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String BUCKET_NAME = "python-lilypond-bucket"; // Replace with your S3 bucket name
    private static final String STEP_FUNCTION_ARN = "arn:aws:states:us-east-2:105411766712:stateMachine:GeneratePDF-UploadAudio"; // Replace with your Step Function ARN

    private static final String DB_SECRET_ID = "rds/sheetmusic/password";
    private static final String DB_URL = "jdbc:postgresql://sheetmusic-db.cnwoaowq0gyw.us-east-2.rds.amazonaws.com:5432/postgres";
    private static final String DB_USER = "postgres";

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final AWSStepFunctions stepFunctionsClient = AWSStepFunctionsClientBuilder.defaultClient();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            System.out.println("Lambda triggered: Processing request...");

            String awsRegion = System.getenv("AWS_REGION");
            if (awsRegion != null) {
            System.out.println("AWS Region: " + awsRegion);
            } else {
            System.out.println("AWS_REGION environment variable is not set.");
            }

            Instant startTime = Instant.now();

            Map<String, String> headers = event.getHeaders();
            System.out.println("📜 Request Headers:");
            headers.forEach((key, value) -> System.out.println(key + ": " + value));

            // System.out.println("Full request event: " + event); // This will log everything, including headers.

            // Check if body is base64 encoded
            boolean isBase64Encoded = event.getIsBase64Encoded();
            System.out.println("Is request body Base64 encoded? " + isBase64Encoded);

            // Retrieve and decode the audio file
            String body = event.getBody();
            if (body == null || body.isEmpty()) {
                System.out.println("Error: Request body is empty.");
                response.setStatusCode(400);
                response.setBody("Error: No file data received.");
                return response;
            }

            byte[] byteArray;
            if (isBase64Encoded) {
                System.out.println("Decoding Base64-encoded body...");
                byteArray = Base64.getDecoder().decode(body);
            } else {
                System.out.println("Using raw body as byte array...");
                byteArray = body.getBytes(StandardCharsets.UTF_8);
            }

            System.out.println("Successfully extracted file data, size: " + byteArray.length + " bytes");

            body = new String(byteArray, StandardCharsets.UTF_8);

            // Print the first few bytes of the data for debugging
            System.out.print("First few bytes: ");
            for (int i = 0; i < Math.min(50, byteArray.length); i++) {
                System.out.printf("%02X ", byteArray[i]);
            }
            System.out.println();

            // System.out.println("📜 Full Decoded Request Body:\n" + formatMultipartBody(body));

            System.out.println("📜 First 100 bytes: " + body.substring(0, Math.min(body.length(), 100)));
            System.out.println("📜 Last 100 bytes: " + body.substring(Math.max(0, body.length() - 100)));

            // extract the boundary
            String boundary = extractBoundary(event.getHeaders().get("content-type"));
            if (boundary == null) {
                System.out.println("Error: Boundary not found in Content-Type header.");
                response.setStatusCode(400);
                response.setBody("Error: Missing or malformed boundary in Content-Type header.");
                return response;
            }
            System.out.println("Extracted boundary: " + boundary);

            // Extract WAV file from multipart data
            ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);
            
            // String boundary = "Boundary-12345";
            // String mockInput = "--" + boundary + "\r\n"
            //     + "Content-Disposition: form-data; name=\"file\"; filename=\"test.wav\"\r\n"
            //     + "Content-Type: audio/wav\r\n\r\n"
            //     + "FAKE_BINARY_DATA_HERE\r\n"
            //     + "--" + boundary + "--\r\n";
            // InputStream inputStream = new ByteArrayInputStream(mockInput.getBytes(StandardCharsets.UTF_8));

            byte[] extractedAudio = parseMultipartData(inputStream, boundary);
            if (extractedAudio == null) {
                System.out.println("Error: Failed to extract file from multipart data.");
                response.setStatusCode(400);
                response.setBody("Error: Could not extract file from multipart request.");
                return response;
            }

            System.out.println("Successfully extracted raw audio file, size: " + extractedAudio.length + " bytes");

            // Convert byte array to InputStream
            InputStream audioFileInputStream = new ByteArrayInputStream(extractedAudio);

            // Generate a unique file name
            String fileName = "audio/" + UUID.randomUUID().toString() + ".wav";
            System.out.println("Generated unique file name: " + fileName);

            // Upload the file to S3
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("audio/wav");
            metadata.setContentLength(extractedAudio.length);

            System.out.println("Uploading file to S3 bucket: " + BUCKET_NAME);
            s3Client.putObject(BUCKET_NAME, fileName, audioFileInputStream, metadata);
            System.out.println("File successfully uploaded to S3: " + fileName);

            // Get deviceToken from query parameters
            Map<String, String> queryParams = event.getQueryStringParameters();
            String deviceToken = queryParams != null ? queryParams.get("deviceToken") : null;
            System.out.println("Extracted deviceToken: " + deviceToken);

            if (deviceToken == null) {
                System.out.println("Warning: deviceToken not provided in query parameters.");
            }

            // Prepare input for the Step Function
            Map<String, String> stepFunctionInput = new HashMap<>();
            stepFunctionInput.put("bucket_name", BUCKET_NAME);
            stepFunctionInput.put("file_key", fileName);
            stepFunctionInput.put("deviceToken", deviceToken != null ? deviceToken : "unknown");

            // Convert the Map to a JSON string using Jackson
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonInput = objectMapper.writeValueAsString(stepFunctionInput);

            System.out.println("Triggering Step Function: " + STEP_FUNCTION_ARN);
            StartExecutionRequest startExecutionRequest = new StartExecutionRequest()
                .withStateMachineArn(STEP_FUNCTION_ARN)
                .withInput(jsonInput);

            StartExecutionResult result = stepFunctionsClient.startExecution(startExecutionRequest);
            String executionArn = result.getExecutionArn();
            String jobId = executionArn.substring(executionArn.lastIndexOf(":") + 1);
            System.out.println("Step Function execution started: " + executionArn);

            // can't move the 
            // db-write to a separate try block because the jobId comes from the step function result 
            System.out.println("Inserting submission record in database: " + jobId);
            insertSubmissionRecord(jobId, extractedAudio.length, Timestamp.from(startTime), awsRegion);

            // Return a success response
            response.setStatusCode(200);
            response.setBody(String.format(
                "Audio file uploaded successfully. Step Function execution started: %s",
                result.getExecutionArn()
            ));
            System.out.println("Lambda execution completed successfully.");

        } catch (Exception e) {
            // Log error details
            System.out.println("Error during execution: " + e.getMessage());
            e.printStackTrace();

            response.setStatusCode(500);
            response.setBody("Error during execution: " + e.getMessage());
        }
            
        return response;
    }

    private void insertSubmissionRecord(String submissionId, int inputSizeBytes, Timestamp createdAt, String awsRegion) throws Exception {
        String password = getDbPasswordFromSecretsManager(awsRegion);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, password)) {
            String sql = "INSERT INTO public.\"Submission\" (id, \"inputType\", \"inputSizeBytes\", \"createdAt\") VALUES (?, CAST(? AS \"InputType\"), ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, submissionId);
                stmt.setString(2, "AUDIO");
                stmt.setInt(3, inputSizeBytes);
                stmt.setTimestamp(4, createdAt);
                stmt.executeUpdate();
            }
        }
    }

    private String getDbPasswordFromSecretsManager(String awsRegion) throws Exception {
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
                .withRegion(awsRegion)
                .build();

        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(DB_SECRET_ID);
        GetSecretValueResult getSecretValueResult = client.getSecretValue(getSecretValueRequest);

        String secretString = getSecretValueResult.getSecretString();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode secretJson = objectMapper.readTree(secretString);

        if (!secretJson.has("password")) {
            throw new RuntimeException("Password not found in secret");
        }

        return secretJson.get("password").asText();
    }

    private String formatMultipartBody(String body) {
        // Replace boundaries with a line break for clarity
        return body.replaceAll("--", "\n--").replaceAll("\r", "").trim();
    }


    // Extract the boundary string from the content type header
    private String extractBoundary(String contentType) {
        if (contentType == null || !contentType.contains("boundary=")) {
            System.out.println("Error: Content-Type header is missing or malformed.");
            return null; // Handle the error case here
        }
        String[] parts = contentType.split("boundary=");
        return parts.length > 1 ? parts[1] : null;
    }

    // For Java 8
    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }



    private byte[] parseMultipartData(InputStream inputStream, String boundary) {
        try {
            // Read full stream into memory to avoid partial reads
            byte[] rawData = readAllBytes(inputStream);
            System.out.println("📜 Full Raw InputStream:\n" + new String(rawData, StandardCharsets.UTF_8));

            // Reset stream for parsing
            inputStream = new ByteArrayInputStream(rawData);
            System.out.println("🔍 Reset input stream, available bytes: " + inputStream.available());

            // Verify boundary
            System.out.println("Formatted boundary: " + boundary);

            // Initialize MultipartStream
            MultipartStream multipartStream = new MultipartStream(inputStream, boundary.getBytes(StandardCharsets.UTF_8));
            System.out.println("✅ MultipartStream initialized");
        
            // Read first boundary manually
            boolean hasNext = multipartStream.skipPreamble();            
            if (!hasNext) {
                System.out.println("❌ First boundary not detected!");
                return null;
            }
            System.out.println("✅ First boundary found");

            int partCounter = 0;
            while (hasNext) {
                partCounter++;
                String header = multipartStream.readHeaders();
                System.out.println("📝 Part " + partCounter + " headers: " + header);

                if (header.contains("Content-Disposition") && header.contains("form-data") && header.contains("file")) {
                    System.out.println("🎯 Found the file part!");

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    multipartStream.readBodyData(outputStream);

                    byte[] fileBytes = outputStream.toByteArray();
                    System.out.println("✅ File extracted! First 50 bytes: " + Arrays.toString(Arrays.copyOf(fileBytes, 50)));

                    return fileBytes;
                }

                hasNext = multipartStream.readBoundary();
            }
        } catch (Exception e) {
            System.out.println("❌ Error parsing multipart data: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
