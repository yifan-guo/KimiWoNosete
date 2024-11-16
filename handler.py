import os
import json
import boto3
import yt_dlp
import requests
from tempfile import NamedTemporaryFile

# Define the OAuth 2.0 scope (YouTube Data API v3 in this case)
SCOPES = ['https://www.googleapis.com/auth/youtube.readonly']

# The token endpoint
TOKEN_URI = 'https://oauth2.googleapis.com/token'

def get_access_token(client_id, client_secret, refresh_token):
    # Request body for refreshing the token
    data = {
        'refresh_token': refresh_token,
        'client_id': client_id,
        'client_secret': client_secret,
        'grant_type': 'refresh_token',
    }

    # Make the request to refresh the token
    response = requests.post(TOKEN_URI, data=data)
    tokens = response.json()

    # Now, you can use the new access token
    access_token = tokens.get('access_token') 

    return access_token

def lambda_handler(event, context):

    # Step 1: Parse the JSON payload from the API Gateway request
    try:
        payload = event['payload']  # API Gateway body is in the 'payload' key
    except (KeyError, json.JSONDecodeError) as e:
        print(f"Error parsing event: {str(e)}")
        return {
            'statusCode': 400,
            'body': json.dumps({
                'error': 'Event missing payload'
            })
        }
    
    youtube_url = payload.get('youtube_url')
    
    if not youtube_url:
        return {
            'statusCode': 400,
            'body': 'Missing youtube_url parameter'
        }
    
    # Authenticate and get the access token using OAuth
    client_id = os.environ['client_id']
    client_secret = os.environ['client_secret']
    refresh_token = os.environ['refresh_token']

    access_token = get_access_token(client_id, client_secret, refresh_token)

    # Create a temporary file to store WAV audio
    with NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
        temp_file_path = temp_file.name
        
        # Set up yt-dlp options to download only the audio (WAV)
        ydl_opts = {
            'format': 'bestaudio/best',  # Choose the best available audio format
            'outtmpl': temp_file_path,  # Store it as a temporary file
            'username': f'oauth {access_token}',  # Pass OAuth token
            'password': '',  # empty OAuth token used as password for YouTube
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'wav',  # Change codec to WAV
                'preferredquality': '192',  # Quality (if applicable)
            }],
            'ffmpeg_location': 'ffmpeg-layer/ffmpeg/bin/ffmpeg',  # Path to FFmpeg binary in Lambda layer
        }

        # Step 2: Use yt-dlp to download the video/audio
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([youtube_url])
        except Exception as e:
            return {
                'statusCode': 500,
                'body': f"Error downloading video: {str(e)}"
            }

        # Upload the file to S3
        s3_client = boto3.client('s3')
        s3_bucket_name = 'python-lilypond-bucket'  # Replace with your S3 bucket name
        s3_key = f"audio/{os.path.basename(temp_file_path)}"
        
        try:
            # Upload the WAV file to the specified S3 bucket
            s3_client.upload_file(temp_file_path, s3_bucket_name, s3_key)
        except Exception as e:
            return {
                'statusCode': 500,
                'body': f"Error uploading to S3: {str(e)}"
            }

        # Clean up the temporary file after upload
        os.remove(temp_file_path)
        
        print(f'Successfully wrote .wav file to https://{s3_bucket_name}.s3.amazonaws.com/{s3_key}')
        return {
            'statusCode': 200,
            'bucket_name': s3_bucket_name,
            'file_key': s3_key
        }
