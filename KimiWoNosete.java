import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public class KimiWoNoseteLambda implements RequestHandler<Map<String, String>, String> {

    //post:: returns the note that corresponds to the index arg
    public static String h1(int index, String[] notes) {
        return notes[index % 12];
    }

    //post:: returns the index (in the frequency array) with the smallest epsilon value
    public static int computeEpsilon(double[] frequency, double fundfreq) {
        double epsilon = Double.MAX_VALUE;
        double difference = 0;
        int index = 0;
        for (int i = 0; i < frequency.length; i++) {
            difference = Math.abs(fundfreq - frequency[i]);
            if (difference < epsilon) {
                epsilon = difference;
                index = i;
            }
        }
        return index;
    }

    @Override
    public String handleRequest(Map<String, String> event, Context context) {
        // Retrieve parameters from event
        String s3BucketName = event.get("bucket_name");  // S3 bucket where the input file is located
        String s3Key = event.get("file_key");            // S3 key (path) of the .wav file
        String outputBucket = event.get("output_bucket"); // Output bucket to save the result
        String outputKey = event.get("output_key");      // S3 key for the output file

        // Initialize the S3 client
        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
        
        // Fetch the .wav file from S3
        S3Object s3Object = s3Client.getObject(s3BucketName, s3Key);
        
        // Process the .wav file
        try {
            InputStream inputStream = s3Object.getObjectContent();
            double[] sound = readWavFile(inputStream);

            // Prepare the note data
            String[] notes = {"a", "ais", "b", "c", "cis", "d", "dis", "e", "f", "fis", "g", "gis"};
            double[] frequency = new double[88];
            for (int i = -48; i <= 39; i++) {
                frequency[i + 48] = 440 * Math.pow(2, (double) i / 12);
            }

            // Process the audio to extract frequencies
            StringBuilder output = new StringBuilder();
            int N = 1024;  // Sample size
            int Start = 0;  // Start index
            processAudio(output, sound, N, Start, frequency, notes);

            // Upload the output .txt file to S3
            uploadOutputToS3(s3Client, output.toString(), outputBucket, outputKey);

            return "Processing complete: " + outputKey;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error processing the audio file: " + e.getMessage();
        }
    }

    // Helper method to read the WAV file from InputStream
    private double[] readWavFile(InputStream inputStream) throws IOException {
        // Convert the .wav file (in InputStream) into a double array.
        // Use a library like Apache Tika or javax.sound.sampled.AudioSystem to read the WAV file.
        // For simplicity, let's assume this method returns the sound data as a double array.
        // You'll need to implement this part based on how the WAV file is structured.
        return new double[44100]; // Placeholder, replace with actual WAV reading logic
    }

    // Helper method to process audio data (same as your current logic, refactored)
    private void processAudio(StringBuilder output, double[] sound, int N, int Start, double[] frequency, String[] notes) {
        int SAMPLES_PER_SECOND = 44100;
        for (int probe = Start; probe + N < sound.length; probe = probe + N) {
            double[] sample = new double[N];
            for (int i = probe; i < probe + N; i++) {
                sample[i - probe] = sound[i];
            }

            int FREQUENCY_RANGE = 32768;
            Complex[] NFFT = new Complex[FREQUENCY_RANGE];
            for (int i = 0; i < NFFT.length; i++) {
                NFFT[i] = new Complex(0, 0);  // Zero-padded NFFT
            }

            int count = 0;
            for (int i = probe; i < probe + N; i++) {
                Complex temp = new Complex(sound[i], 0);
                NFFT[count] = NFFT[count].plus(temp);
                count++;
            }

            Complex[] y = FFT.fft(NFFT);  // FFT computation
            double[] magnitude = new double[y.length / 4];
            double max = Double.MIN_VALUE;
            for (int i = 0; i < magnitude.length; i++) {
                magnitude[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
                if (magnitude[i] > max) {
                    max = magnitude[i];
                }
            }

            output.append("START_OF_SAMPLE " + String.format("%.2f", max) + "\n");
            for (int i = 1; i < magnitude.length - 1; i++) {
                if (magnitude[i] > magnitude[i - 1] && magnitude[i] > magnitude[i + 1] && magnitude[i] > 0.40 * max) {
                    int potentialNote = computeEpsilon(frequency, ((double) i / NFFT.length) * SAMPLES_PER_SECOND);
                    output.append(h1(potentialNote, notes) + ":" + String.format("%.2f", magnitude[i]) + " " + potentialNote + "\n");
                }
            }
        }
    }

    // Helper method to upload the output .txt file to S3
    private void uploadOutputToS3(AmazonS3 s3Client, String outputData, String outputBucket, String outputKey) {
        InputStream inputStream = new ByteArrayInputStream(outputData.getBytes());
        s3Client.putObject(outputBucket, outputKey, inputStream, null);
    }
}
