// index.mjs

import apn from 'apn'; // Importing the APN module
import AWS from 'aws-sdk'; // Importing the AWS SDK for S3
import fs from 'fs'; // For writing the APNs key to the /tmp directory

// Replace with your own APNs key details from Apple Developer Portal
const APNS_KEY_ID = 'T236YGCUT2';  // Key ID from Apple Developer Portal
const APNS_TEAM_ID = '78Z85K7J98'; // Your Team ID
const APNS_BUNDLE_ID = 'gh-yifan.SheetOfMusic'; // Your app's bundle ID

const BUCKET_NAME = 'python-lilypond-bucket';
const KEY_NAME = 'AuthKey_T236YGCUT2.p8';

// Endpoint URL
const APNS_SERVER = 'https://api.push.apple.com:443';

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
        production: true // Use false for sandbox, true for production
    };

    const apnProvider = new apn.Provider(apnsOptions);

    // Create the notification payload
    const notification = new apn.Notification();
    notification.alert = { title, body };
    notification.sound = 'default'; // Optional: Sound on notification
    notification.contentAvailable = 1; // Set this to trigger background behavior
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
    console.log("Lambda function triggered.");

    console.log("Received event:", JSON.stringify(event));

    const statusCode = event.statusCode;
    if (!statusCode) {
        console.log("Error: statusCode not found in input.");
        return {
            statusCode: 400,
            body: JSON.stringify({ message: "statusCode not provided." })
        };
    } 
    
    // If statusCode is not 200, return a 400 response
    if (statusCode !== 200) {
        console.log(`Error: Received statusCode ${statusCode}.`);
        return {
            statusCode: 400,
            body: JSON.stringify({ message: `Request failed with status code ${statusCode}.` })
        };
    }

    console.log("Status code is 200, proceeding with the next steps.");

    // Access the presigned url from the input payload
    // e.g presignedUrl = 'https://www.adobe.com/content/dam/cc/en/legal/terms/enterprise/pdfs/GeneralTerms-NA-2024v1.pdf';
    const body = JSON.parse(event.body);  // The body is a string, so we need to parse it
    const presignedUrl = body.presigned_url;  // Extract the presigned URL from the parsed body
    if (!presignedUrl) {
        console.log("Error: presigned url not found in input.");
        return {
            statusCode: 400,
            body: JSON.stringify({ message: "presigned_url missing from prior step." })
        };
    }

    // Access the deviceToken from the input payload
    // e.g deviceToken = '4c6b80b1c2a6a241cea9b4a1096cb429d2635dca38e8bc0889dd57e9252280d2';
    const deviceToken = body.deviceToken;
    if (!deviceToken) {
        console.log("Error: deviceToken not found in input.");
        return {
            statusCode: 400,
            body: JSON.stringify({ message: "deviceToken not provided." })
        };
    }
    // Log the extracted values
    console.log(`Extracted presigned URL: ${presignedUrl}`);
    console.log(`Extracted deviceToken: ${deviceToken}`);
    
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
    const title = 'Download Complete';
    const message = 'Your PDF is ready. Tap to view it.';
    const userInfo = { presigned_url: presignedUrl }; // Attach the URL in userInfo

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
