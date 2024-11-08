# KimiWoNosete
A program to analyze .wav files of piano recordings and produce music scores

Requires Lilypond, a music engraving program to render the .ly file produced by this program.
Download can be found here: http://lilypond.org/

#Instructions
1. Pull repo<br/>
2. Open up the command line and navigate to this directory<br/>
3. Compile "javac KimiWoNosete.java"<br/>
4. Type "java KimiWoNosete [SAMPLE_SIZE] [STARTING_POINT]"  and hit ENTER <br/>
    *[SAMPLE_SIZE] is an integer that represents the number of data points analyzed per sample (a set of points that represents a window of time in the song)<br/>
    *[STARTING_POINT] is an integer that represents where in the sound file the program should start analyzing <br/>
    *a default option is "java KimiWoNosete 3000 0" (i.e. analyze this song from the beginning with 3000 data points per sample)<br/>
    *Note: the smaller the SAMPLE_SIZE the more precise the sheet score, since it is less likely for previously played notes to carry over into the next window of time, however runtime may be extended<br/>
5. Type the name of the audio file to be analyzed (default: type "KimiWoNosete_1band.wav")<br/>
6. Wait for the song to finish playing, as the program is writing the notes into a file called "test.txt"<br/>
7. Compile "javac LilypadFileGenerator.java"<br/>
7. Run "java LilypadFileGenerator" from the command line, as it is parsing the "test.txt" file into a file that can be read by Lilypond called "output.ly"<br/>
8. Open "output.ly", which launches Lilypond automatically, and select "Compile" (or press Cmd + R) from the Lilypond menu bar <br/>
9. Lilypond will open your PDF viewer and display the sheet music<br/>


# To run locally
To run your AWS Lambda function locally, you need to set up a local environment that mimics AWS Lambda's runtime, allowing you to execute the function as it would in the cloud. There are a few different approaches to running Lambda functions locally, and I'll walk you through some of the most common and recommended ones.

1. Use AWS SAM (Serverless Application Model)
The AWS SAM CLI is one of the best tools for running AWS Lambda functions locally. SAM provides a local development environment that can simulate AWS Lambda, API Gateway, and other AWS services.

Steps to run Lambda function locally using AWS SAM:
1.1. Install AWS SAM CLI
If you don't already have the AWS SAM CLI installed, follow these instructions:

Install AWS SAM CLI
1.2. Set Up Your Project Directory
Assuming your Lambda function is inside a Maven project, here's what you need to do:

Create a template.yaml file in your project directory (this is your SAM configuration file):

yaml
Copy code
AWSTemplateFormatVersion: '2010-09-09'
Resources:
  KimiWoNoseteFunction:
    Type: AWS::Serverless::Function
    Properties:
      Handler: com.yourcompany.KimiWoNoseteLambda::handleRequest
      Runtime: java8
      CodeUri: target/kimiwo-nosete-lambda-1.0-SNAPSHOT.jar
      MemorySize: 1024
      Timeout: 300
      Environment:
        Variables:
          SAMPLE_BUCKET_NAME: "your-s3-bucket-name"
CodeUri: Points to the location of your packaged .jar file (e.g., target/kimiwo-nosete-lambda-1.0-SNAPSHOT.jar).
Handler: Specifies the class and method to be invoked (com.yourcompany.KimiWoNoseteLambda::handleRequest).
Runtime: Since you're using Java, you'll need to specify java8 as the runtime.
1.3. Build Your Application
Run the following Maven command to build your application and create the .jar file:

`mvn clean package`
This will generate your Lambda function's .jar file in the target/ directory.

1.4. Run the Function Locally with AWS SAM
Now you can use the sam local invoke command to run your Lambda function locally:


`sam local invoke KimiWoNoseteFunction --event event.json`

event.json: This file should contain the event data you'd like to pass to the Lambda function. For example:

```
{
  "bucket_name": "your-s3-bucket-name",
  "file_key": "your-audio-file.wav",
  "output_bucket": "your-output-bucket",
  "output_key": "output-file.txt"
}
```

This simulates an S3 event (or any other event) that would trigger your Lambda function.

1.5. Local Lambda Logs and Debugging
Once you run the above command, SAM will execute the Lambda function locally and output logs to the terminal. You can modify your Lambda function to include additional logging using System.out.println or Logger to track the function’s behavior.

1.6. Testing with Custom Events
If you're testing with specific S3 files, you may need to manually download or mock the S3 content for local testing. You can write a test file locally and mock the S3 input accordingly.


# Lambda is timing out

It looks like your AWS Lambda function is timing out after 5 minutes (300 seconds). This can happen when the function takes longer than the configured timeout duration to complete its execution. From the logs, it seems like the function is not finishing in a reasonable amount of time, possibly due to the way it's processing the audio or the size of the audio file.

Here are a few areas to investigate and potential solutions to address this timeout issue:
1. Large WAV Files (Slow Processing)
If you're processing large audio files, the processing time might exceed the 5-minute limit. Even with the optimizations we've made, audio processing (especially with FFT or complex waveform analysis) can be computationally expensive.

Solutions:
Increase Lambda Timeout: If possible, increase the timeout for the Lambda function. You can adjust the timeout in the AWS Lambda console:

Go to the Lambda console.
Select your function.
In the Configuration tab, scroll to General configuration.
Increase the Timeout setting to a higher value (up to 15 minutes, 900 seconds).
However, even with a higher timeout, very large audio files might still lead to excessive processing time, so optimizing the function is recommended.

2. Process the Audio in Smaller Chunks
If the audio file is too large, processing the entire file at once might not be feasible within Lambda’s limits. You could process the audio in smaller chunks (e.g., in smaller intervals of time) and process the file in a streaming fashion, rather than loading the entire file into memory at once.

Solution:
Instead of reading the entire audio file into memory at once, consider processing the audio in smaller, overlapping chunks. You can read and process chunks of the audio, extract data from each chunk, and accumulate the results.

Here's a suggestion for processing the audio in chunks:

Stream and Process in Chunks: You can process the file in smaller blocks (e.g., 1024 or 2048 samples at a time) and immediately perform the frequency analysis or note extraction on each block. This can help prevent memory overload and allow Lambda to process the data efficiently.
3. Optimize the Audio Processing
The processing you're performing on the audio, especially the FFT and frequency analysis, might be taking a long time due to the complexity of the algorithm.

Solutions:
Optimize the FFT Algorithm: FFT computations can be optimized to reduce time complexity. For example, you can consider using optimized FFT libraries (such as JTransforms or Apache Commons Math) that provide faster and more efficient implementations.
Reduce the FFT Size (N): You're currently using a sample size N = 1024, which is a standard size for FFT. However, reducing the size of N could reduce the time it takes for each FFT calculation. You could experiment with smaller values (e.g., N = 512 or N = 256).
4. Log Progress for Debugging
To understand where the function might be hanging, add more detailed logging inside your code to track progress through various stages. This could help you identify where the delay is occurring.

For example, add log statements:

Before reading the audio file from S3.
After reading the file.
During each chunk of audio processing.
When the final results are being uploaded to S3.
This will help you pinpoint which step is taking too long.

5. Consider Other AWS Services for Large Audio Files
If processing large audio files within Lambda is inherently slow due to the size or complexity of the task, you may want to consider using other AWS services better suited for large or long-running processes, such as:

AWS Batch: AWS Batch is a service that can handle large-scale batch jobs and long-running processes. It's more suitable for computationally intensive tasks.
Amazon EC2: You can run an EC2 instance to process large files. EC2 instances offer more flexibility in terms of compute power and long-running processes.
6. Monitoring Lambda Execution
You can also monitor your Lambda function's execution using AWS CloudWatch logs and metrics. This will provide more insights into where the function is getting stuck and whether it's hitting any resource limits (e.g., CPU, memory, or network issues).

CloudWatch Logs: Review detailed logs to understand what part of the code is causing delays. Look for log entries before and after each major step to check where the function hangs.
CloudWatch Metrics: Check for Lambda memory and CPU usage to ensure the function isn't running out of resources.
Next Steps:
Increase Lambda Timeout (temporarily, to test) and see if the function completes.
Optimize the code for chunked processing of audio data to avoid loading large files into memory.
Add more logging to track where the function might be hanging.
Consider alternatives like AWS Batch or EC2 if Lambda is not the best fit for long-running, computationally intensive tasks.
Let me know how it goes or if you need further assistance with optimizing the function or implementing any of these suggestions!