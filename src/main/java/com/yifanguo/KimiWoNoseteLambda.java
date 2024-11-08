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

    // Helper methods for musical note processing
    public static String h1(int index, String[] notes) {
        return notes[index % 12];
    }

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

        // Log before fetching the file from S3
        context.getLogger().log("Fetching the .wav file from S3 bucket: " + s3BucketName + ", file: " + s3Key);
        
        // Fetch the .wav file from S3
        S3Object s3Object = s3Client.getObject(s3BucketName, s3Key);
        
        // Process the .wav file
        try {
            // Log before reading the file
            context.getLogger().log("Reading the .wav file from S3...");
            InputStream inputStream = s3Object.getObjectContent();
            double[] sound = readWavFile(inputStream);

            // Log after reading the file
            context.getLogger().log("Finished reading the .wav file. File size: " + sound.length + " samples.");
            
            // Prepare the note data
            String[] notes = {"a", "ais", "b", "c", "cis", "d", "dis", "e", "f", "fis", "g", "gis"};
            double[] frequency = new double[88];
            for (int i = -48; i <= 39; i++) {
                frequency[i + 48] = 440 * Math.pow(2, (double) i / 12);
            }

            // Log before processing the audio
            context.getLogger().log("Starting audio processing...");

            // Process the audio to extract frequencies
            StringBuilder output = new StringBuilder();
            int N = 1024;  // Sample size
            int Start = 0;  // Start index
            processAudio(output, sound, N, Start, frequency, notes, context);

            // Log after processing the audio
            context.getLogger().log("Audio processing complete.");

            // Upload the output .txt file to S3
            context.getLogger().log("Uploading the results to S3...");
            uploadOutputToS3(s3Client, output.toString(), outputBucket, outputKey);

            // Log after uploading
            context.getLogger().log("Upload complete. Output saved to: " + outputBucket + "/" + outputKey);

            return "Processing complete: " + outputKey;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error processing the audio file: " + e.getMessage();
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
            return "Unsupported audio file format: " + e.getMessage();
        }
    }

    private double[] readWavFile(InputStream inputStream) throws IOException, UnsupportedAudioFileException {
        // Log before reading the audio data
        System.out.println("Reading audio data from InputStream...");

        // Wrap the input stream in a BufferedInputStream to support mark/reset
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

        // Now pass the bufferedInputStream to the AudioSystem
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream);

        // Get the format of the audio file
        AudioFormat format = audioInputStream.getFormat();

        // Log the format of the audio file
        System.out.println("Audio format: " + format.toString());

        // Check if the format is supported (16-bit mono PCM format, typically used for WAV files)
        if (format.getSampleSizeInBits() != 16 || format.getChannels() != 1) {
            throw new UnsupportedAudioFileException("Only 16-bit mono PCM WAV files are supported.");
        }

        // Create a buffer to read data from the AudioInputStream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; // Buffer to hold data while reading from the input stream
        int bytesRead;

        // Read the audio stream in chunks and write to the ByteArrayOutputStream
        while ((bytesRead = audioInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }

        // Get the audio bytes from the ByteArrayOutputStream
        byte[] audioBytes = byteArrayOutputStream.toByteArray();

        // Convert the byte array into a double array for the waveform
        int sampleSize = format.getSampleSizeInBits() / 8;  // 2 bytes for 16-bit samples
        int numSamples = audioBytes.length / sampleSize;

        double[] audioData = new double[numSamples];
        ByteBuffer byteBuffer = ByteBuffer.wrap(audioBytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // Audio samples in WAV files are typically little-endian

        // Extract each sample and store as a double
        for (int i = 0; i < numSamples; i++) {
            // Read the sample as a 16-bit signed integer
            short sample = byteBuffer.getShort();
            // Normalize to the range [-1.0, 1.0] (16-bit signed integer range is [-32768, 32767])
            audioData[i] = sample / 32768.0;
        }

        return audioData;
    }

    private void processAudio(StringBuilder output, double[] sound, int N, int Start, double[] frequency, String[] notes, Context context) {
        int SAMPLES_PER_SECOND = 44100;
        int chunkCount = 0;
        
        for (int probe = Start; probe + N < sound.length; probe = probe + N) {
            // Log for each chunk being processed
            chunkCount++;
            context.getLogger().log("Processing chunk #" + chunkCount + " from index " + probe);

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

        // Log when processing is complete
        context.getLogger().log("Completed processing " + chunkCount + " chunks.");
    }

    // Helper method to upload the output .txt file to S3
    private void uploadOutputToS3(AmazonS3 s3Client, String outputData, String outputBucket, String outputKey) {
        // Log before uploading
        System.out.println("Uploading the output to S3...");

        InputStream inputStream = new ByteArrayInputStream(outputData.getBytes());
        s3Client.putObject(outputBucket, outputKey, inputStream, null);
        
        // Log after uploading
        System.out.println("Upload complete.");
    }
}
