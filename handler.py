import yt_dlp
import boto3
import os
from tempfile import NamedTemporaryFile

def lambda_handler(event, context):
    youtube_url = event.get('youtube_url')
    
    if not youtube_url:
        return {
            'statusCode': 400,
            'body': 'Missing youtube_url parameter'
        }
    
    # Create a temporary file to store MP3
    with NamedTemporaryFile(delete=False, suffix=".mp3") as temp_file:
        temp_file_path = temp_file.name
        
        # Set up yt-dlp options to download only the audio (MP3)
        ydl_opts = {
            'format': 'bestaudio/best',
            'outtmpl': temp_file_path,
            'postprocessors': [{
                'key': 'FFmpegAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '192',
            }],
        }

        # Download the audio
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([youtube_url])

        # Upload the file to S3
        s3_client = boto3.client('s3')
        s3_bucket_name = 'python-lilypond-bucket'  # Replace with your S3 bucket name
        s3_key = f"audio/{os.path.basename(temp_file_path)}"
        
        s3_client.upload_file(temp_file_path, s3_bucket_name, s3_key)

        # Clean up the temporary file
        os.remove(temp_file_path)
        
        return {
            'statusCode': 200,
            'body': {
                'mp3_s3_url': f'https://{s3_bucket_name}.s3.amazonaws.com/{s3_key}'
            }
        }
