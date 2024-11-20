import os
import json
import boto3
import subprocess
import yt_dlp
from tempfile import NamedTemporaryFile

def lambda_handler(event, context):
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

    # Proxy details from Smart Proxy
    proxy_url = "http://username:password@proxy.example.com:port"

    # Create a temporary file to store WAV audio
    with NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
        # temp_file_path = temp_file.name
        # name, ext = os.path.splitext(temp_file_path) # isolate the extension from the path
        temp_file_path = '/tmp/audio.wav'
        
        # Set up yt-dlp options to download only the audio (WAV)
        ydl_opts = {
            'format': 'bestaudio/best',  # Choose the best available audio format
            'proxy': proxy_url,       # Define the proxy URL here
            # 'outtmpl': name + '.webm',  # Store it as a temporary file
            'outtmpl': '/tmp/audio.webm',
            'cookies': cookies_path,  # Pass the path to your cookies.txt file
            # 'postprocessors': [{
            #     'key': 'FFmpegExtractAudio',
            #     'preferredcodec': 'wav',  # Change codec to WAV
            #     'preferredquality': '192',  # Quality (if applicable)
            # }],
            'ffmpeg_location': '/opt/ffmpeg-layer/ffmpeg',  # Path to FFmpeg binary in Lambda layer
            'verbose': True,  # Enable debug output to see what's happening
        }

        # Step 3: Use yt-dlp to download the video/audio
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([youtube_url])
        except Exception as e:
            return {
                'statusCode': 500,
                'body': f"Error downloading video: {str(e)}"
            }

        # Step 4: Convert the downloaded file to 16-bit PCM WAV using ffmpeg
        wav_temp_file_path = '/tmp/audio.wav'  # Define the output WAV file path
        
        try:
            subprocess.run([
                ffmpeg_path,
                '-f', 'webm',
                '-i', '/tmp/audio.webm',          # Input file
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

        # Step 4: Upload the file to S3
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

        # Clean up the temporary file after upload
        os.remove(temp_file_path)
        # os.remove(wav_temp_file_path)
        
        print(f'Successfully wrote .wav file to https://{s3_bucket_name}.s3.amazonaws.com/{s3_key}')
        return {
            'statusCode': 200,
            'bucket_name': s3_bucket_name,
            'file_key': s3_key
        }
