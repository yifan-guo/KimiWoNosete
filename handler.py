import json
import boto3
from botocore.exceptions import ClientError
import uuid
import os
from datetime import datetime

# Initialize the Step Functions client
sfn_client = boto3.client('stepfunctions')

# AWS Connection settings
AWS_REGION = os.environ['AWS_REGION']

# Initialize the DynamoDB client
dynamodb = boto3.resource('dynamodb', region_name=AWS_REGION) 
table = dynamodb.Table('SubmissionsTable')

def lambda_handler(event, context):
    # Log the incoming event for debugging
    print(f"Received event: {json.dumps(event)}")
    
    # Step 1: Parse the JSON payload from the API Gateway request
    try:
        body = json.loads(event['body'])  # API Gateway body is in the 'body' key

        if 'youtube_url' not in body:
            return {
                'statusCode': 400,
                'body': json.dumps({
                    'error': "'Missing required field 'youtube_url'"
                })
            }
    except (KeyError, json.JSONDecodeError) as e:
        print(f"Error parsing JSON body: {str(e)}")
        return {
            'statusCode': 400,
            'body': json.dumps({
                'error': 'Invalid JSON body'
            })
        }

    # Step 2: Generate a unique job ID for tracking
    job_id = str(uuid.uuid4())  # Unique job ID using uuid

    # Step 3: Prepare the input for the Step Function execution
    state_machine_input = {
        'job_id': job_id,
        'payload': body  # You can send the entire payload to the state machine or modify as needed
    }

    # Step 4: Define the ARN of the Step Function state machine (replace with your own ARN)
    state_machine_arn = 'arn:aws:states:us-east-2:105411766712:stateMachine:GenerateSheetMusic'

    # Step 5: Trigger the Step Function with the input
    try:
        response = sfn_client.start_execution(
            stateMachineArn=state_machine_arn,
            name=job_id,
            input=json.dumps(state_machine_input)
        )
        print(f"Step Function execution started: {response['executionArn']}")
    except Exception as e:
        print(f"Error starting Step Function execution: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps({
                'error': 'Failed to start Step Function execution',
                'message': str(e)
            })
        }
    
    # Step 6: Insert record into DynamoDB
    youtube_url = body['youtube_url']
    
    try:
        now = datetime.utcnow().isoformat()

        item = {
            'PK': f'SUBMISSION#{job_id}',
            'SK': 'DETAILS',
            'entity': 'Submission',
            'submissionId': job_id,
            'inputType': 'URL',
            'inputUrl': youtube_url,
            'createdAt': now
        }

        table.put_item(Item=item)
        print("Submission inserted successfully.")

    except Exception as db_err:
        print(f"Database insert failed: {str(db_err)}")

    return {
        'statusCode': 202,
        'body': json.dumps({
            'message': 'Job accepted and is being processed.',
            'job_id': job_id,
            'status': 'in-progress'
        })
    }
