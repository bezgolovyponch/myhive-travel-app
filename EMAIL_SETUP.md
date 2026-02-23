# Email Configuration Setup Guide

This guide explains how to configure email sending for the MyHive Travel App itinerary confirmations.

## Overview

The application sends beautiful HTML email confirmations to customers when they submit a booking. The email includes:
- Personalized greeting with customer name
- Complete itinerary details
- Activities with descriptions and pricing
- Direct link to Google Sheet itinerary
- Next steps and contact information

## Email Service Setup

### Option 1: Gmail (Recommended for Development)

1. **Enable 2-Factor Authentication** on your Gmail account
2. **Generate an App Password**:
   - Go to Google Account settings
   - Security → 2-Step Verification → App passwords
   - Generate a new app password for "MyHive Travel App"
   - Copy the 16-character password

3. **Configure Environment Variables**:
   ```bash
   # Development (.env file)
   EMAIL_USERNAME=your-email@gmail.com
   EMAIL_PASSWORD=your-16-character-app-password
   
   # Production
   export EMAIL_USERNAME=your-email@gmail.com
   export EMAIL_PASSWORD=your-16-character-app-password
   ```

### Option 2: SMTP Service (Recommended for Production)

1. **Choose an SMTP Provider**:
   - SendGrid
   - Mailgun
   - Amazon SES
   - Your own SMTP server

2. **Configure Environment Variables**:
   ```bash
   # Production
   export SMTP_HOST=smtp.sendgrid.net
   export SMTP_PORT=587
   export EMAIL_USERNAME=apikey
   export EMAIL_PASSWORD=your-sendgrid-api-key
   ```

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `EMAIL_USERNAME` | Email username or API key | `your-email@gmail.com` |
| `EMAIL_PASSWORD` | Email password or app password | `abcd-efgh-ijkl-mnop` |
| `SMTP_HOST` | SMTP server host (optional) | `smtp.gmail.com` |
| `SMTP_PORT` | SMTP server port (optional) | `587` |
| `FRONTEND_URL` | Frontend application URL | `http://localhost:3000` |

## Email Templates

The system uses HTML email templates with the following sections:

### Customer Confirmation Email
- **Header**: Personalized greeting and trip name
- **Booking Details**: Date, status, trip information
- **Itinerary**: Destinations and activities with pricing
- **Important Links**: Direct link to Google Sheet
- **Next Steps**: What happens after booking
- **Footer**: Contact information and branding

### Admin Notification (Optional)
- Simple text notification
- Customer name and contact info
- Trip summary
- Link to Google Sheet

## Testing Email Configuration

1. **Start the application** with email configuration
2. **Submit a test booking** through the frontend
3. **Check the email inbox** for the confirmation
4. **Verify the Google Sheet link** works correctly

## Troubleshooting

### Common Issues

1. **"Authentication failed" errors**:
   - Verify app password is correct (Gmail)
   - Check 2FA is enabled
   - Ensure username is correct

2. **"Connection refused" errors**:
   - Verify SMTP host and port
   - Check firewall settings
   - Ensure network connectivity

3. **Email not received**:
   - Check spam/junk folder
   - Verify recipient email address
   - Check email logs in application

### Debug Mode

Enable email debugging in development:
```properties
# application-dev.properties
spring.mail.properties.mail.debug=true
logging.level.org.springframework.mail=DEBUG
```

## Security Considerations

- **Never commit email credentials** to version control
- **Use environment variables** for all email configuration
- **Use app passwords** instead of real passwords (Gmail)
- **Consider using email services** like SendGrid for production
- **Monitor email sending** for abuse or issues

## Production Recommendations

1. **Use a dedicated email service** (SendGrid, Mailgun, etc.)
2. **Set up email domain authentication** (SPF, DKIM, DMARC)
3. **Monitor email deliverability** and bounce rates
4. **Set up email analytics** to track engagement
5. **Use transactional email templates** for consistency

## Email Content Customization

To customize the email content:

1. **Edit `EmailService.java`** - Modify the `buildItineraryEmail` method
2. **Update HTML templates** - Change styling and content
3. **Add new email types** - Create additional email methods
4. **Test thoroughly** - Ensure emails render correctly

## Rate Limits

Be aware of email provider rate limits:
- **Gmail**: ~100 emails/day via SMTP
- **SendGrid**: Based on your plan
- **Mailgun**: Based on your plan
- **Amazon SES**: Starts at 1,000 emails/day (sandbox)

For high-volume applications, consider implementing email queuing and batch processing.
