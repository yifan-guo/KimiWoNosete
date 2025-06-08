import json
import boto3
from botocore.exceptions import ClientError
import uuid
import os
import psycopg2
from datetime import datetime

# Initialize the Step Functions client
sfn_client = boto3.client('stepfunctions')

# PostgreSQL connection settings (set these as Lambda environment variables)
DB_HOST = 'sheetmusic-db.cnwoaowq0gyw.us-east-2.rds.amazonaws.com'
DB_PORT = '5432'
DB_NAME = 'postgres'
DB_USER = 'postgres'
AWS_REGION = os.environ['AWS_REGION']

def get_db_password_from_sm():
    secret_name = "rds/sheetmusic/password"
    region_name = AWS_REGION

    session = boto3.session.Session()
    client = session.client(
        service_name='secretsmanager',
        region_name = region_name
    )

    try:
        response = client.get_secret_value(
            SecretId=secret_name
        )
    except ClientError as e:
        # For a list of exceptions thrown, see
        # https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
        print(f"Unexpected error getting secret from Secrets Manager: {str(e)}")
        raise e

    try: 
        secret_payload = json.loads(response['SecretString'])

    except (KeyError, json.JSONDecodeError) as e:
        print(f"Error parsing JSON body: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps({'error': f'Error parsing secret from SM: {str(e)}'})
        }

    return secret_payload['password']

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

    DB_PASSWORD = get_db_password_from_sm() # TODO move this to cold start
    
    # Insert record into PostgreSQL RDS
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD
        )
        cursor = conn.cursor()

        insert_query = """
        INSERT INTO public."Submission" (id, "inputType", "inputUrl", "createdAt")
        VALUES (%s, %s, %s, %s)
        """
        cursor.execute(insert_query, (job_id, 'URL', body['youtube_url'], datetime.utcnow()))
        conn.commit()

        cursor.close()
        conn.close()
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
