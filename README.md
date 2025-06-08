# About
To orchestrate multiple AWS Lambda functions with AWS Step Functions, you'll need to create a Step Functions state machine that defines the sequence of steps (i.e., Lambda function executions) as well as any other workflows such as error handling, retries, and branching logic.

# Create the Step Functions State Machine
The Step Functions state machine allows you to define the orchestration flow.

Start with a State Machine Definition: You can define the state machine in Amazon States Language (ASL), a JSON-based language used to define workflows.
The repo includes an `asl.json` file that has the state machine definition that orchestrates multiple Lambda functions:

Explanation of the State Machine:
Step Function Lambdas
Convert to MP3: youtubeURL —> .wav file (YouTubeToMP3Lambda)
Generate Notes: .wav file —> .txt file (KimiWoNoseteLambda)
Generate .ly File: .txt file —> .ly file (LilypadFileGeneratorLambda)
Generate PDF: .ly file —> PDF (LilyPondPDFGenerator)


Convert to MP3: This state invokes the YouTubeToMP3Lambda function to process the input url to generate a .wav file. (TODO: Rename to reflect it outputs a .wav file)
The Resource field specifies the ARN of the Lambda function that should be executed.
After completing, it transitions to the next state (Generate notes).
Generate Notes: This state invokes the KimiWoNoseteLambda function, which performs an FFT on the .wav file to product the notes in a .txt file.
After the validation is done, it proceeds to the `Generate .ly file` state.
`Generate .ly file`: This state invokes the LilypadFileGeneratorLambda, which uses a deque to arrange the notes in a .ly file that Lilypond can read.
`Generate PDF`: This state invokes the LilyPondPDFGenerator function, which uses lilypond installed in a lambda layer to generate a PDF from the .ly file.
The End: true indicates the end of the state machine.

# How to trigger step function using S3 object upload?
To configure your iOS to trigger an AWS Step Functions state machine when a request is created, you will need to use an intermediary AWS Lambda function. Unfortunately, you cannot directly trigger an AWS Step Functions state machine from a API Gateway endpoint. Instead, you use a request to invoke a Lambda function, and that Lambda function, in turn, triggers the Step Functions state machine.


Generated a function in Python 3.13 that triggers a Step Function from the body of a POST request from an API Gateway endpoint, and returns a job ID along with a status code for job tracking.

The Lambda function will receive the JSON payload from the API Gateway.
It will use that payload to trigger a Step Function.
The Lambda will return a new job ID (you can generate this ID or use a unique identifier) and a status code (e.g., 202 for accepted requests).


{
  "message": "Job accepted and is being processed.",
  "job_id": "123e4567-e89b-12d3-a456-426614174000",
  "status": "in-progress"
}

# Input
The lambda expects an input from API Gateway like this
```json
{
  "body": "{\"youtube_url\": \"https://www.youtube.com/watch?v=mZwhtEnrimI\"}"
}
```

# Lambda Layer
To connect to the sheet music database for record persistence, the Lambda requires the psycopg2 library which is not included in the default Lambda runtime. To create a layer, run the commands below (from [link](https://medium.com/@bloggeraj392/creating-a-psycopg2-layer-for-aws-lambda-a-step-by-step-guide-a2498c97c11e)).

```
mkdir -p psycopg2-layer/python
cd psycopg2-layer/python
pip3 install --platform manylinux2014_x86_64 --target . --python-version 3.11 --only-binary=:all: psycopg2-binary
cd ..
zip -r psycopg2-layer.zip python
```

Upload the generated .zip file to the layer to test the Lambda.