import subprocess
import os
import boto3
import shutil

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
    bucket_name = event['bucket']
    input_file_key = event['input_file']
    output_file_key = event['output_file']
    
    print("Downloading file from S3: bucket=%s, key=%s", bucket_name, input_file_key)

    input_file_path = f'/tmp/{os.path.basename(input_file_key)}'
    output_pdf_path = f'/tmp/{os.path.splitext(os.path.basename(input_file_key))[0]}'

    try:
        s3.download_file(bucket_name, input_file_key, input_file_path)
        print("File downloaded successfully: %s", input_file_path)

    except Exception as e:
        print(f"Error downloading file: {str(e)}")
        
        return {
            'statusCode': 500,
            'body': f'Error downloading .ly file: {e}'
        }
        
    # Generate PDF using LilyPond

    lilypond_command = f'/opt/bin/lilypond -o {output_pdf_path} {input_file_path}'
    print(f"Running command: {lilypond_command}")

    try:
        result = subprocess.run(lilypond_command, shell=True, capture_output=True, text=True, env=os.environ)
        
        if result.returncode != 0:
            print("LilyPond command failed with return code %s", result.returncode)
            print("Error:\n%s", result.stderr)
            return {
                'statusCode': 500,
                'body': f'Error generating PDF: {result.stderr}'
            }

        print("PDF generated successfully: %s", output_pdf_path)

    except subprocess.CalledProcessError as e:
        print("Error generating PDF: %s", e)
        return {
            'statusCode': 500,
            'body': f'Error generating PDF: {e}'
        }

    try:
        # Upload the generated PDF back to S3
        s3.upload_file(output_pdf_path + ".pdf", bucket_name, output_file_key)
        print("PDF uploaded successfully to S3: bucket=%s, key=%s", bucket_name, output_file_key)

        return {
            'statusCode': 200,
            'body': f'PDF generated successfully and uploaded to {output_file_key}'
        }
    
    except Exception as e:
        print("An unexpected error occurred: %s", e)
        return {
            'statusCode': 500,
            'body': f'An unexpected error occurred: {e}'
        }
