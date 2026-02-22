# Google Sheets Integration Setup Guide

This guide will help you set up Google Sheets integration for the MyHive Travel App.

## Overview

The Google Sheets integration allows users to export their trip itineraries directly to Google Sheets for easy sharing
and collaboration.

## Prerequisites

- Google Cloud Platform (GCP) account
- Google account for accessing Google Sheets

## Setup Steps

### 1. Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Note your Project ID for later use

### 2. Enable Google Sheets API

1. In your GCP project, go to "APIs & Services" > "Library"
2. Search for "Google Sheets API"
3. Click on it and then click "Enable"

### 3. Create a Service Account

1. Go to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "Service Account"
3. Fill in the service account details:
    - **Name**: MyHive Travel App Service
    - **Service account ID**: myhive-service@your-project-id.iam.gserviceaccount.com
    - **Description**: Service account for MyHive Travel App Google Sheets integration
4. Click "Create and Continue"
5. Skip the "Grant this service account access to project" step (click "Continue")
6. Skip the "Grant users access to this service account" step (click "Done")

### 4. Generate Service Account Key

1. Find your newly created service account in the credentials list
2. Click on the service account name
3. Go to the "KEYS" tab
4. Click "Add Key" > "Create new key"
5. Select **JSON** as the key type
6. Click "Create"
7. The JSON key file will be downloaded automatically. **Keep this file secure!**

### 5. Create a Google Sheet (Optional)

You can either:

- Create a new Google Sheet that will be used as the default export target
- Allow the application to create sheets dynamically

To create a default sheet:

1. Go to [Google Sheets](https://sheets.google.com)
2. Create a new spreadsheet
3. Name it something like "MyHive Travel Exports"
4. Copy the spreadsheet ID from the URL (the long string between `/d/` and `/edit`)

### 6. Configure Environment Variables

Add the following environment variables to your application:

```bash
# Development (.env file)
GOOGLE_SHEETS_CREDENTIALS_PATH=/path/to/your/service-account-key.json
GOOGLE_SHEETS_SPREADSHEET_ID=your-spreadsheet-id-here

# Production
export GOOGLE_SHEETS_CREDENTIALS_PATH=/path/to/your/service-account-key.json
export GOOGLE_SHEETS_SPREADSHEET_ID=your-spreadsheet-id-here
```

### 7. Share the Google Sheet with Service Account

If you're using an existing spreadsheet:

1. Open your Google Sheet
2. Click "Share" in the top right
3. Add the service account email (e.g., `myhive-service@your-project-id.iam.gserviceaccount.com`)
4. Give it "Editor" permissions
5. Click "Send"

## Testing the Integration

1. Start your backend application
2. Test the status endpoint:
   ```bash
   curl http://localhost:8081/api/google-sheets/status
   ```
3. You should see a response indicating that Google Sheets is configured

## Security Considerations

- **Never commit the service account JSON key to version control**
- Store the key file in a secure location
- Use environment variables to reference the key file path
- Regularly rotate your service account keys
- Limit the service account's permissions to only what's necessary

## Troubleshooting

### Common Issues

1. **"Google Sheets is not configured" error**
    - Check that all environment variables are set correctly
    - Verify the service account key file path is accessible
    - Ensure the spreadsheet ID is correct

2. **"Permission denied" errors**
    - Make sure the service account has Editor access to the spreadsheet
    - Verify the Google Sheets API is enabled in your GCP project

3. **"Invalid credentials" errors**
    - Check that the service account key file is valid and not corrupted
    - Ensure the key file hasn't expired

### Debug Mode

Enable debug logging to troubleshoot issues:

```bash
# Add to application-dev.properties
logging.level.com.myhive.backend.service.GoogleSheetsService=DEBUG
```

## API Endpoints

Once configured, the following endpoints are available:

- `GET /api/google-sheets/status` - Check integration status
- `POST /api/google-sheets/export-trip` - Export trip data
- `POST /api/google-sheets/create-spreadsheet` - Create new spreadsheet
- `GET /api/google-sheets/spreadsheet/{id}/sheets` - List sheets in spreadsheet

## Production Deployment

For production deployment:

1. Use a production GCP project
2. Store the service account key in a secure secret management system
3. Use environment-specific configuration
4. Enable monitoring and alerting for API usage
5. Set up appropriate quotas and limits in GCP

## Cost Considerations

- Google Sheets API has a free tier with generous limits
- Monitor your API usage to avoid unexpected charges
- Consider implementing caching to reduce API calls

## Support

If you encounter issues:

1. Check the application logs for detailed error messages
2. Verify your GCP project configuration
3. Ensure the service account has proper permissions
4. Test API access using the Google API Explorer
