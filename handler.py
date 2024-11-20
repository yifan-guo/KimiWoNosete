import subprocess
import os
import boto3
import shutil
import json

# Set up logging

def lambda_handler(event, context):
    # Print the current home directory
    current_home_directory = os.path.expanduser('~')
    print(f"Current home directory: {current_home_directory}")
    
    # Set the home directory to the default user in Lambda
    default_home_directory = '/tmp'
    os.environ['HOME'] = default_home_directory
    
    current_home_directory = os.path.expanduser('~')
    print(f"Updated home directory to: {current_home_directory}")
    
    print("Lambda function started")

    s3 = boto3.client('s3')
    bucket_name = event['bucket_name']
    input_file_key = event['file_key']
    output_file_key = os.path.splitext(input_file_key)[0] + '.pdf'
    
    print(f"Downloading file from S3: bucket={bucket_name}, key={input_file_key}")

    input_file_path = f'/tmp/{os.path.basename(input_file_key)}'
    output_pdf_path = f'/tmp/{os.path.splitext(os.path.basename(input_file_key))[0]}'

    try:
        s3.download_file(bucket_name, input_file_key, input_file_path)
        print(f"File downloaded successfully: {input_file_path}")

    except Exception as e:
        print(f"Error downloading file: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps({'message': f'Error downloading .ly file: {e}'})
        }
        
    # Generate PDF using LilyPond

    lilypond_command = f'/opt/bin/lilypond -o {output_pdf_path} {input_file_path}'
    print(f"Running command: {lilypond_command}")

    try:
        result = subprocess.run(lilypond_command, shell=True, capture_output=True, text=True, env=os.environ)
        
        if result.returncode != 0:
            print(f"LilyPond command failed with return code {result.returncode}")
            print(f"Error:\n{result.stderr}")
            return {
                'statusCode': 500,
                'body': json.dumps({'message': f'Error generating PDF: {result.stderr}'})
            }

        print(f"PDF generated successfully: {output_pdf_path}")

    except subprocess.CalledProcessError as e:
        print(f"Error generating PDF: {e}")
        return {
            'statusCode': 500,
            'body': json.dumps({'message': f'Error generating PDF: {e}'})
        }

    try:
        # Upload the generated PDF back to S3
        s3.upload_file(output_pdf_path + ".pdf", bucket_name, output_file_key)
        print(f"PDF uploaded successfully to S3: bucket={bucket_name}, key={output_file_key}")

        # Generate a presigned URL for the uploaded PDF file
        presigned_url = s3.generate_presigned_url('get_object',
                                                  Params={'Bucket': bucket_name, 'Key': output_file_key},
                                                  ExpiresIn=3600)  # URL valid for 1 hour (3600 seconds)

        print(f"Presigned URL generated: {presigned_url}")

        # Return a response containing the presigned URL
        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'PDF generated successfully and uploaded.',
                'presigned_url': presigned_url
            })
        }
    
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return {
            'statusCode': 500,
            'body': json.dumps({'message': f'An unexpected error occurred: {e}'})
        }
