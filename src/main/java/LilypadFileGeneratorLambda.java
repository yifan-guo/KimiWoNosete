import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import java.io.*;
import java.util.*;

public class LilypadFileGeneratorLambda implements RequestHandler<Map<String, String>, String> {

    private static final int MARK_LIMIT = 1000;  // The maximum mark size for reading the file
    private static final int CHUNK_SIZE = 1024;  // Number of samples to process at a time

    @Override
    public String handleRequest(Map<String, String> event, Context context) {
        // Retrieve S3 bucket and file key information from the event
        String inputBucket = event.get("bucket_name");
        String inputKey = event.get("file_key");
        String outputBucket = event.get("output_bucket");
        String outputKey = event.get("output_key");

        // Create S3 client to fetch and store data
        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

        try {
            // Fetch the input file from S3
            S3Object s3Object = s3Client.getObject(inputBucket, inputKey);
            S3ObjectInputStream s3InputStream = s3Object.getObjectContent();
            BufferedReader buffer = new BufferedReader(new InputStreamReader(s3InputStream));

            // Prepare output to be written back to S3
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write("\\absolute {\n");

            // Process the file and generate the Lilypad music notation
            processFile(buffer, writer);

            // Write footer to the Lilypad file
            writer.write("\n}\n");
            writer.write("\\version \"2.14.0\"");
            writer.close();

            // Upload the generated output to the specified S3 output location
            uploadOutputToS3(s3Client, outputStream.toString(), outputBucket, outputKey);

            return "Processing complete: " + outputKey;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error processing file: " + e.getMessage();
        }
    }

    private void processFile(BufferedReader buffer, BufferedWriter writer) throws IOException {
        String myLine;
        Deque<Double> deque = new LinkedList<>();

        while ((myLine = buffer.readLine()) != null) {
            if (myLine.contains("START_OF_SAMPLE")) {
                String[] summary = myLine.split(" ");
                double maxSample = Double.parseDouble(summary[1]);

                // Process the sample based on deque logic
                processSamples(buffer, deque, maxSample, myLine, writer);
            }
        }
    }

    private void processSamples(BufferedReader buffer, Deque<Double> deque, double maxSample, String myLine, BufferedWriter writer) throws IOException {
        if (deque.isEmpty()) {
            deque.addLast(maxSample);
        } else if (deque.size() == 1 && maxSample > deque.getLast()) {
            deque.addLast(maxSample);
            buffer.mark(MARK_LIMIT);
        } else if (deque.size() == 1 && maxSample <= deque.getLast()) {
            deque.clear();
            deque.addLast(maxSample);
        } else if (deque.size() == 2 && maxSample < deque.getLast()) {
            // Logic for when peak is detected
            double maxMagnitude = deque.getLast();
            deque.clear();
            deque.addLast(maxSample);
            buffer.reset();

            // Now handle notes extraction
            ArrayList<Note> notes = new ArrayList<>();
            while ((myLine = buffer.readLine()) != null && !myLine.contains("START_OF_SAMPLE")) {
                String[] note = myLine.split(":");
                String[] data = note[1].split(" ");
                double magnitude = Double.parseDouble(data[0]);
                int index = Integer.parseInt(data[1]);

                if (magnitude >= maxMagnitude - 35) {
                    notes.add(new Note(note[0], magnitude, index));
                }
            }

            // Write the notes/chords into the LilyPond file
            if (notes.size() > 1) {
                writeChord(notes, writer);
            } else if (notes.size() == 1) {
                writeNote(notes, writer);
            }
            buffer.mark(MARK_LIMIT);
        } else {
            deque.addLast(maxSample);
            deque.removeFirst();
            buffer.mark(MARK_LIMIT);
        }
    }

    private void writeNote(ArrayList<Note> notes, BufferedWriter writer) throws IOException {
        for (Note s : notes) {
            int index = s.getIndex();
            if (index < 3) {
                writer.write(s.getNote() + ",,, ");
            } else if (index < 15) {
                writer.write(s.getNote() + ",, ");
            } else if (index < 27) {
                writer.write(s.getNote() + ", ");
            } else if (index < 39) {
                writer.write(s.getNote() + " ");
            } else if (index < 51) {
                writer.write(s.getNote() + "' ");
            } else if (index < 63) {
                writer.write(s.getNote() + "'' ");
            } else if (index < 75) {
                writer.write(s.getNote() + "''' ");
            } else if (index < 87) {
                writer.write(s.getNote() + "'''' ");
            } else {
                writer.write(s.getNote() + "''''' ");
            }
        }
    }

    private void writeChord(ArrayList<Note> notes, BufferedWriter writer) throws IOException {
        writer.write("<");
        writeNote(notes, writer);
        writer.write("> ");
    }

    private void uploadOutputToS3(AmazonS3 s3Client, String outputData, String outputBucket, String outputKey) {
        InputStream inputStream = new ByteArrayInputStream(outputData.getBytes());
        s3Client.putObject(outputBucket, outputKey, inputStream, null);
    }
}
