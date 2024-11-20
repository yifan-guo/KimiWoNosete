# KimiWoNosete
A program to analyze .wav files of piano recordings and produce music scores

Requires Lilypond, a music engraving program to render the .ly file produced by this program.
Download can be found here: http://lilypond.org/

This lambda requires the `yt-dlp` library which is not included in the default Lambda runtime environment. It requires a Lambda layer which contains this library.

# Invoke the API
1. Go to API Gateway
2. Find this `YouTubeToMP3API` [API](https://us-east-2.console.aws.amazon.com/apigateway/main/apis/bnwc9iszkk/resources?api=bnwc9iszkk&region=us-east-2#)

3. Under Resources, click on the POST endpoint, then click "Test".
Paste the following in the request body
```json
{
  "youtube_url": "https://www.youtube.com/watch?v=mZwhtEnrimI"
}
```
4. Click Test

~![Invoke API with payload](images/AWS%20API%20Gateway%20POST%20Endpoint%20Test%20Invocation.png)



This tells the Step Function lambda to trigger the Step Function.

5. A new execution of the State Machine will be created. Find the [execution](https://us-east-2.con5.sole.aws.amazon.com/states/home?region=us-east-2#/v2/executions/details/arn:aws:states:us-east-2:105411766712:execution:GenerateSheetMusic:a57afda0-12c3-42e3-88d7-b13b0cf5e2b5) by going to state functions and clicking on the latest run.

6. Once state machine execution completes, navigate to the S3 [bucket](https://us-east-2.console.aws.amazon.com/s3/object/python-lilypond-bucket?region=us-east-2) to find the .pdf file.

![Successful Execution](images/AWS%20Step%20Function%20Successful%20Execution.png)

7. Download the .pdf file to see the generated sheet music.

# Instructions to setup yt-dlp (YouTube downloader) layer
Create and activate a virtual environment
```
python3 -m venv venv
source venv/bin/activate
```

Create the Lambda Layer Directory
```
mkdir -p python
```

Install the library into the layer directory
```
pip install yt-dlp requests -t python
```

Zip the contents
```
zip -r yt_dlp_layer.zip python/
```

## Create the Lambda Layer in AWS:

Go to the AWS Lambda Console.
Under the Layers section in the sidebar, click on Create Layer.
Give your layer a name (e.g., yt-dlp-layer).
Upload the yt_dlp_layer.zip file.
Set the compatible runtimes (e.g., Python 3.8 or 3.9, depending on what you're using).

Attach the Layer to Your Lambda Function:

Go to your Lambda function.
Under the Layers section, click on Add a layer.
Choose Custom layers and select the yt-dlp-layer you just created.
Click Add.


# Instructions to setup ffmpeg layer
Navigate to FFmpeg.org --> Linux --> latest static builds --> older releases
Download ffmpeg-5.0.1-amd64-static.tar.xz and unpack
Rename extracted folder to ffmpeg-layer

Zip the contents
```
zip -r ffmpeg-5.0.1-amd64-static.zip ffmpeg-layer
```

Upload .zip file to S3 bucket
Copy the S3 URL

## Create the Lambda Layer in AWS:

Go to the AWS Lambda Console.
Under the Layers section in the sidebar, click on Create New Version.
Select upload .zip file from S3 and paste the S3 URL.
Click create version

# Instructions to create cookie
Download Edit my cookie Chrome Extension
Under Extension settings, enable in Incognito (and access URLs)
Open youtube in Incognito and login
Click on extension, download cookies as Netscape format (txt file)
Upload .txt file to Lambda

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

# How to scale?

** The lambda finished in just under 5 min. But the .wav file was extracted from a 3 min video. For longer videos, how can the solution scale to handle them?

Scaling your Lambda function to handle longer audio files from videos (or longer processing times in general) requires addressing both execution time limits and memory requirements. AWS Lambda has a maximum execution time of 15 minutes (900 seconds), and you can configure your function's memory allocation, but there are a few things you can do to optimize or scale your approach to handle longer files efficiently.

Here are several strategies you can employ to scale your solution for longer video files:

1. Increase Lambda Timeout and Memory Allocation
For larger audio files, you may need to adjust your Lambda function's memory and timeout settings:

Memory: Increase the allocated memory to give your Lambda more resources for processing. This will help speed up processing and prevent timeouts.
Timeout: Set the Lambda timeout to the maximum allowed (15 minutes) to ensure that it has enough time to process the file.
2. Split the Audio File into Chunks
Since AWS Lambda processes tasks in a stateless, single-execution manner, one effective way to handle large files is to split the audio file into smaller chunks and process them in parallel or sequentially.

How to Split and Process Chunks:
Split the Audio: Before processing, you can split the large .wav file into smaller, manageable chunks. You can use the AudioInputStream to read portions of the file (by seeking and skipping data), and process these chunks in separate Lambda invocations.

For example, read the first 1 minute of the audio in one chunk, the next minute in the second invocation, and so on.

Lambda Function Orchestrator: You can use AWS Step Functions to orchestrate multiple Lambda invocations. This way, each function will process a chunk of the audio, and you can combine the results after all chunks are processed.

Example Approach:
Split the Audio File: After fetching the .wav file from S3, split the audio into smaller chunks (e.g., 5-minute segments).
Invoke Lambda Functions for Each Chunk: Use AWS Step Functions to create a workflow where each step invokes a Lambda function to process one chunk of the file.
Merge Results: After all chunks are processed, merge the results into a final output file and store it in S3.
Step Functions would allow you to handle failures or retries, and also give you the ability to monitor the entire workflow.

3. Use Amazon S3 and AWS Lambda in a More Distributed Way
For very large video files, Lambda can still handle the processing, but you might want to process the file in parallel across multiple resources.

Option 1: Divide the Audio File into Smaller S3 Objects
You can use AWS S3's multipart upload or divide the audio into separate S3 objects. Lambda can then be triggered for each smaller object, allowing the function to run on smaller chunks of the file.

Option 2: Asynchronous Lambda Processing
AWS Lambda supports asynchronous invocation, which means you can trigger the Lambda function to run asynchronously and not worry about the timeout. However, you’ll still need a mechanism to split the file into smaller pieces (perhaps by duration or size) and then let Lambda process these chunks one by one or in parallel.

4. Optimize Audio Processing (Memory and Performance)
If you're processing very long audio files, consider optimizing your algorithm for better memory and CPU usage:

Buffering: Instead of reading large chunks into memory at once, you can use streaming techniques to process the audio in smaller, incremental chunks.
Parallelize FFT Processing: If the FFT (Fast Fourier Transform) is the most time-consuming part, you can look into parallelizing this step across multiple threads or processes. You can break the file into blocks and process those blocks concurrently.
5. Use AWS Elastic Transcoder or MediaConvert (for video-to-audio extraction)
If your .wav files are coming from video files (like MP4 or MKV), it might be more efficient to extract the audio first using AWS Elemental MediaConvert or AWS Elastic Transcoder, which are designed for video/audio transcoding.

Extract Audio: Convert the video into smaller audio chunks (MP3, WAV, etc.) first using MediaConvert. This can reduce the size and complexity of the audio file before you send it to Lambda for further processing.
Automate the Process: You can set up a workflow where, once a video file is uploaded to S3, MediaConvert automatically extracts the audio, and then Lambda takes over to process the audio data.
6. Use Amazon EC2 for Larger-Scale Processing
If Lambda doesn't meet your needs for scaling (due to limitations in execution time, memory, or concurrency), you might need to offload the audio processing to Amazon EC2. EC2 instances are more flexible in terms of execution time and resource allocation.

EC2 Instance for Audio Processing: Launch an EC2 instance with enough compute power to handle large files. You can use EC2 in combination with S3 to store the audio files and results, and you can monitor the EC2 job execution.
Example Lambda Split-Processing Workflow with Step Functions
Here’s a more detailed breakdown of how to use AWS Step Functions to split and process audio in chunks:

Step 1: Split the audio file in Lambda, store the audio chunks in separate S3 objects.
Step 2: Use AWS Step Functions to invoke multiple Lambda functions in parallel for each audio chunk.
Step 3: Each Lambda function processes its chunk of the audio and returns the result.
Step 4: Combine the results from all Lambda functions into a final output (e.g., a single text file or data structure).
Step 5: Upload the combined result to S3.
You could also use SNS or SQS to notify or trigger Lambda invocations for chunk processing.

Key Takeaways:
Increase Lambda Timeout and Memory: Ensure that Lambda has enough resources to process larger audio files.
Split Files into Chunks: For large audio files, split them into manageable chunks and process each chunk in parallel.
Use Step Functions: Orchestrate multiple Lambda functions using AWS Step Functions to handle large-scale processing.
Optimize Code: Ensure that the audio processing is optimized to use memory and CPU efficiently.
Consider EC2 for Large Scale: If Lambda is not sufficient, use EC2 for more control over processing power and execution time.
Let me know if you'd like more details or examples for any of these options!

## But the code as you suggested already reads in the file chunk by chunk. Are you talking about chunking at a higher level?

ChatGPT said:
Ah, I see the confusion! You're correct in pointing out that the code already processes the .wav file in chunks (using N-sized segments). However, when I refer to "chunking at a higher level," I'm talking about breaking the entire audio file into smaller sub-files before processing, rather than just processing it in smaller pieces within the same Lambda invocation.

Let me clarify the two concepts:

1. Chunking the Audio Within Lambda (Your Current Approach)
Currently, your Lambda function reads and processes the audio in chunks of size N (e.g., 1024 samples per chunk). This is an effective way to break the file into smaller pieces for in-memory processing, especially for tasks like Fast Fourier Transform (FFT) or other audio signal analysis.

This allows the function to process one small part of the file at a time, without loading the entire file into memory.
However, when the file is large (e.g., a 3-minute video extracted to audio), the total processing time can still exceed the Lambda timeout limit (up to 15 minutes). Even if you process chunks of the audio file internally, the Lambda function may still time out if the total processing time exceeds the Lambda timeout limit.
2. Chunking the Entire Audio File (Higher-Level Chunking)
The approach I was referring to involves breaking the entire audio file into smaller files or chunks (i.e., splitting the audio at the file level) before the Lambda function processes them. This allows you to parallelize the processing of multiple smaller files across multiple Lambda invocations (or even multiple Lambda functions triggered by Step Functions).

Splitting the audio file into multiple smaller files allows each chunk to be processed independently, potentially by different Lambda functions.
Parallelizing the processing of these chunks will ensure that you're processing audio in parallel, cutting down on the total processing time.
This can be especially helpful for audio files that are too large to be processed by a single Lambda function within the time limit.
How to Implement Higher-Level Chunking:
Here’s a more detailed outline of how you can implement this strategy:

Step 1: Pre-process and Split the Audio File
You would first need to split the large .wav audio file into smaller, more manageable chunks. This could be done in a separate Lambda function or process before the main audio processing starts. The chunks could be split based on time (e.g., 1-minute segments) or file size (e.g., 10 MB per chunk), depending on what’s more efficient.

You can use audio processing libraries like FFmpeg or even simpler tools (or Lambda layers) to split the audio file into separate segments.
After splitting, each chunk is uploaded as a separate .wav file into S3.
Step 2: Invoke Lambda Functions for Each Chunk
Once the audio file has been split into smaller chunks, you can trigger multiple Lambda functions to process each of these chunks independently. This can be achieved by:

Using AWS Step Functions: This service will allow you to invoke Lambda functions in parallel to process each chunk of audio.
Using Amazon S3 Event Triggers: Whenever a chunk of the audio file is uploaded to S3, you can set up an S3 event trigger to invoke a Lambda function for processing that chunk.
For instance, each Lambda function would be responsible for processing one chunk of audio, and it would perform the same operations on a smaller portion of the file.

Step 3: Combine the Results
After each Lambda function processes a chunk of audio, you can use another Lambda function or a Step Functions step to combine the results (e.g., output from each chunk) into a final file or data structure.

This can include appending results to a text file or saving frequency analysis data into a database or S3.
You can then upload the final results back to S3.
Why Split the Audio File?
Timeout Issues: A single Lambda function might exceed the timeout limit when processing large files. By splitting the file into smaller chunks, you ensure that each individual Lambda invocation completes within the 15-minute time limit.

Parallel Processing: By splitting the audio file into smaller chunks, you can process them in parallel, reducing overall processing time. For instance, if you split a 15-minute file into 5-minute chunks, each chunk can be processed by a separate Lambda invocation.

Efficiency: Lambda functions are stateless and designed to scale horizontally. By chunking the audio at a higher level and processing each chunk independently, you avoid running into memory or time limitations that could occur if the entire audio file were processed by a single Lambda function.

Example Flow with Higher-Level Chunking
Initial Step: An S3 event triggers a Lambda function when a large .wav file is uploaded.
Lambda Splitter: This Lambda function splits the large .wav file into smaller chunks (1-minute or 5-minute .wav files) and uploads these chunks back to S3.
Step Function Orchestrator: A Step Function is triggered to process each chunk in parallel. Each Lambda function processes one of the smaller chunks (using your existing logic for chunked audio processing).
Final Aggregation: After all the chunks are processed, a final Lambda function combines the results and uploads them to S3.
Example Workflow using Step Functions:
State 1: Start with a large .wav file uploaded to S3.
State 2: Split the file into smaller chunks (e.g., using Lambda or another service).
State 3: For each chunk, trigger a Lambda function that processes the chunk (parallels).
State 4: Once all chunks are processed, merge the results and store them in S3.
This is more of a distributed approach that scales horizontally.

Summary of the Approach:
In-memory chunking: You're already chunking the audio within Lambda, but this could still hit the time limit if the total processing time exceeds Lambda's maximum execution time.
File-level chunking: Split the large audio file into multiple smaller files (sub-files) and process each chunk in parallel using multiple Lambda functions. This distributes the workload and reduces the overall processing time.
By chunking at the file level, you’re ensuring that Lambda functions process smaller portions of the data, thus reducing the risk of timeouts and improving overall performance through parallelization.

Let me know if you need more clarification on how to implement this or if you'd like more specific examples!



