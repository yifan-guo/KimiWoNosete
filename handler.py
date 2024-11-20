import os
import json
import boto3
import subprocess
import yt_dlp
import uuid
import time
from datetime import datetime

# Retry logic helper function
def retry_download(yt_dlp_instance, youtube_url, retries=3, delay=5):
    last_exception = None
    for attempt in range(1, retries + 1):
        try:
            yt_dlp_instance.download([youtube_url])
            return True  # Successful download
        except Exception as e:
            last_exception = e
            print(f"Attempt {attempt} failed: {str(e)}")
            if attempt < retries:
                print(f"Retrying in {delay} seconds...")
                time.sleep(delay)  # Wait before retrying
            else:
                print(f"Giving up after {retries} attempts.")
    # If all attempts fail, raise the last encountered exception
    raise last_exception

def lambda_handler(event, context):
    # Step 0: Set the home directory to /tmp so yt-dlp can write intermediate files
    current_home_directory = os.path.expanduser('~')
    print(f"Current home directory: {current_home_directory}")
    
    # Set the home directory to the default user in Lambda
    default_home_directory = '/tmp'
    os.environ['HOME'] = default_home_directory
    
    current_home_directory = os.path.expanduser('~')
    print(f"Updated home directory to: {current_home_directory}")

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

    # Step 2: Use yt-dlp to download the video/audio using cookies for authentication
    cookies_path = 'youtube_cookies.txt' # path to the youtube cookies file

    # Check if ffmpeg exists at the expected location
    ffmpeg_path = '/opt/ffmpeg-layer/ffmpeg'
    if os.path.exists(ffmpeg_path):
        print(f"Found ffmpeg at {ffmpeg_path}")
    else:
        print(f"ffmpeg not found at {ffmpeg_path}")

    # Proxy configuration 
    proxy_url = "http://username:password@proxy.example.com:port"
    
    # Step 3: Generate unique filename for output
    unique_id = str(uuid.uuid4())  # Create a unique ID for the file
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")  # Current timestamp for uniqueness
    file_name = f"/audio_{timestamp}_{unique_id}"  # Unique audio file name
    temp_file_path = "/tmp" + file_name + ".webm"
    wav_temp_file_path = "/tmp" + file_name + ".wav"

        
    # Set up yt-dlp options to download only the audio (WAV)
    ydl_opts = {
        'format': 'bestaudio/best',  # Choose the best available audio format
        'proxy': proxy_url,       # Define the proxy URL here
        'outtmpl': temp_file_path,  # Store it as a temporary file
        'cookies': cookies_path,  # Pass the path to your cookies.txt file
        'ffmpeg_location': '/opt/ffmpeg-layer/ffmpeg',  # Path to FFmpeg binary in Lambda layer
        'verbose': True,  # Enable debug output to see what's happening
    }

    # Step 4: Use yt-dlp to download the video/audio
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            retry_download(ydl, youtube_url)
    except Exception as e:
        return {
            'statusCode': 500,
            'body': f"Error downloading video: {str(e)}"
        }

    # Step 5: Convert the downloaded file to 16-bit PCM WAV using ffmpeg    
    try:
        subprocess.run([
            ffmpeg_path,
            '-f', 'webm',
            '-i', temp_file_path,          # Input file
            '-ac', '1',                     # Set audio to mono (1 channel)
            '-ar', '44100',                 # Set sample rate to 44100 Hz
            '-sample_fmt', 's16',           # Set sample format to 16-bit PCM
            wav_temp_file_path              # Output file path
        ], check=True)
        print(f"Successfully converted to WAV: {wav_temp_file_path}")
    except subprocess.CalledProcessError as e:
        return {
            'statusCode': 500,
            'body': f"Error processing audio with ffmpeg: {str(e)}"
        }

    # Step 6: Upload the file to S3
    s3_client = boto3.client('s3')
    s3_bucket_name = 'python-lilypond-bucket'  # Replace with your S3 bucket name
    s3_key = f"audio/{os.path.basename(wav_temp_file_path)}"
    
    try:
        # Upload the WAV file to the specified S3 bucket
        s3_client.upload_file(wav_temp_file_path, s3_bucket_name, s3_key)
    except Exception as e:
        return {
            'statusCode': 500,
            'body': f"Error uploading to S3: {str(e)}"
        }

    # Step 7: Clean up the temporary file after upload
    os.remove(temp_file_path)
    os.remove(wav_temp_file_path)
    
    # Step 8: Return status to user
    print(f'Successfully wrote .wav file to https://{s3_bucket_name}.s3.amazonaws.com/{s3_key}')
    return {
        'statusCode': 200,
        'bucket_name': s3_bucket_name,
        'file_key': s3_key
    }
