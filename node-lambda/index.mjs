// index.mjs

import apn from 'apn'; // Importing the APN module
import AWS from 'aws-sdk'; // Importing the AWS SDK for S3
import fs from 'fs'; // For writing the APNs key to the /tmp directory

// Replace with your own APNs key details from Apple Developer Portal
const APNS_KEY_ID = 'T236YGCUT2';  // Key ID from Apple Developer Portal
const APNS_TEAM_ID = '78Z85K7J98'; // Your Team ID
const APNS_BUNDLE_ID = 'gh-yifan.YouTubeToPDF'; // Your app's bundle ID

const BUCKET_NAME = 'python-lilypond-bucket';
const KEY_NAME = 'AuthKey_T236YGCUT2.p8';

// Endpoint URL
const APNS_SERVER = 'https://api.sandbox.push.apple.com:443';  // Use the sandbox URL for development, replace with production URL when ready

// Create a function to download APNs key from S3
async function getApnsKeyFromS3(bucketName, keyName) {
    console.log(`Attempting to download APNs key from S3. Bucket: ${bucketName}, Key: ${keyName}`);

    const s3 = new AWS.S3();
    const localPath = `/tmp/${keyName}`;
    try {
        const params = { Bucket: bucketName, Key: keyName };
        const data = await s3.getObject(params).promise();

        // Save the file to local storage in Lambda's /tmp directory
        fs.writeFileSync(localPath, data.Body);
        console.log(`APNs key downloaded successfully to ${localPath}`);
        return localPath;
    } catch (error) {
        console.error(`Error downloading APNs key from S3: ${error.message}`);
        throw new Error('Failed to download APNs key from S3.');
    }
}

// Create a function to send a push notification using the apn package
async function sendPushNotification(apnsKeyPath, deviceToken, title, body, userInfo) {
    console.log(`Preparing to send push notification. APNs key path: ${apnsKeyPath}, Device token: ${deviceToken}`);

    // Create APNs credentials
    const apnsOptions = {
        token: {
            key: apnsKeyPath, // Path to your APNs key file
            keyId: APNS_KEY_ID, // Your APNs Key ID
            teamId: APNS_TEAM_ID // Your Apple Developer Team ID
        },
        production: false // Use false for sandbox, true for production
    };

    const apnProvider = new apn.Provider(apnsOptions);

    // Create the notification payload
    const notification = new apn.Notification();
    notification.alert = { title, body };
    notification.sound = 'default'; // Optional: Sound on notification
    notification.badge = 1; // Optional: Badge number
    notification.payload = userInfo; // Custom data
    notification.topic = APNS_BUNDLE_ID

    try {
        const response = await apnProvider.send(notification, deviceToken);
        console.log(`Notification sent successfully. Response: `, JSON.stringify(response,));
    } catch (error) {
        console.error(`Error sending notification: ${error.message}`);
        throw new Error(`Failed to send notification: ${error.message}`);
    } finally {
        apnProvider.shutdown(); // Always shutdown the provider after sending
    }
}

// Lambda handler function
export const handler = async (event, context) => {
    console.log("Received event:", JSON.stringify(event));

    original_input = event.get("originalInput", {});
    payload = original_input.get("payload", {});
    device_token = payload.get("deviceToken");
    if (!device_token) {
        console.log("Error: deviceToken not found in input.");
        return {
            statusCode: 400,
            body: JSON.stringify({ message: "deviceToken not provided." })
        };
    }

    // Log the extracted values
    console.log(`Extracted deviceToken: ${deviceToken}`);
    
    error_details = event.get("error", {})
    error_message = error_details.get("cause", "Unknown error occurred.")

    if (!error_message) {
        console.log("Error: error cause not found in input.");
        return {
            statusCode: 400,
            body: JSON.stringify({ message: "error cause not found." })
        };
    } 
    
    // Example: Fetch APNs key from S3
    console.log(`Fetching APNs key from S3. Bucket: ${BUCKET_NAME}, Key: ${KEY_NAME}`);
    let apnsKeyPath;
    try {
        apnsKeyPath = await getApnsKeyFromS3(BUCKET_NAME, KEY_NAME);
    } catch (error) {
        return {
            statusCode: 500,
            body: JSON.stringify({ message: 'Failed to download APNs key from S3.', error: error.message })
        };
    }

    // Example device token and message
    const title = 'Download Failed';
    const message = 'Your download failed. Please try again.'
    const userInfo = { error: error_message }; // Attach the error message in userInfo

    console.log(`Sending push notification with title: 
        ${title}, 
        message: ${message}, 
        user info: ${JSON.stringify(userInfo)}
        to device token: ${deviceToken}`);

    // Continue with your logic to send the push notification
    try {
        await sendPushNotification(apnsKeyPath, deviceToken, title, message, userInfo);
        return {
            statusCode: 200,
            body: JSON.stringify({ message: 'Push notification sent successfully.' })
        };
    } catch (error) {
        return {
            statusCode: 500,
            body: JSON.stringify({ message: 'Failed to send notification.', error: error.message })
        };
    }
};
