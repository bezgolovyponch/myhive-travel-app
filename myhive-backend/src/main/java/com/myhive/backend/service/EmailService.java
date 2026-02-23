package com.myhive.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.myhive.backend.dto.TripExportRequest;

import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username:noreply@myhive-travel.com}")
    private String fromEmail;
    
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;
    
    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData, String googleSheetUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🎉 Your MyHive Travel Itinerary Confirmation");
            
            String htmlContent = buildItineraryEmail(customerName, tripData, googleSheetUrl);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Itinerary confirmation email sent to: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send itinerary confirmation email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send confirmation email", e);
        }
    }
    
    private String buildItineraryEmail(String customerName, TripExportRequest tripData, String googleSheetUrl) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>")
            .append("<html><head><meta charset='UTF-8'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }")
            .append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; }")
            .append(".container { max-width: 600px; margin: 0 auto; background: white; }")
            .append(".content { padding: 30px; }")
            .append(".section { margin: 25px 0; padding: 20px; border-left: 4px solid #667eea; background: #f8f9fa; }")
            .append(".activity { margin: 10px 0; padding: 15px; background: white; border-radius: 8px; border: 1px solid #e9ecef; }")
            .append(".price { color: #28a745; font-weight: bold; }")
            .append(".btn { display: inline-block; padding: 12px 24px; background: #667eea; color: white; text-decoration: none; border-radius: 6px; margin: 10px 0; }")
            .append(".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 14px; }")
            .append("</style></head><body>")
            .append("<div class='container'>")
            .append("<div class='header'>")
            .append("<h1>🌍 Your MyHive Travel Itinerary</h1>")
            .append("<p>Thank you for choosing MyHive Travel, ").append(customerName).append("!</p>")
            .append("</div>")
            .append("<div class='content'>")
            
            .append("<div class='section'>")
            .append("<h2>📋 Booking Details</h2>")
            .append("<p><strong>Booking Date:</strong> ").append(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))).append("</p>")
            .append("<p><strong>Trip Name:</strong> ").append(tripData.getTripName()).append("</p>")
            .append("<p><strong>Status:</strong> <span style='color: #28a745;'>Confirmed</span></p>")
            .append("</div>");
        
        // Add destinations and activities
        if (tripData.getDestinations() != null) {
            for (TripExportRequest.DestinationExport destination : tripData.getDestinations()) {
                html.append("<div class='section'>")
                    .append("<h2>📍 ").append(destination.getDestinationName()).append("</h2>")
                    .append("<p><strong>Country:</strong> ").append(destination.getCountry()).append("</p>")
                    .append("<p><strong>Duration:</strong> ").append(destination.getDuration()).append(" days</p>");
                
                if (destination.getStartDate() != null && destination.getEndDate() != null) {
                    html.append("<p><strong>Travel Dates:</strong> ")
                        .append(destination.getStartDate()).append(" to ").append(destination.getEndDate())
                        .append("</p>");
                }
                
                if (destination.getActivities() != null && !destination.getActivities().isEmpty()) {
                    html.append("<h3>🎯 Planned Activities</h3>");
                    double totalPrice = 0.0;
                    
                    for (TripExportRequest.ActivityExport activity : destination.getActivities()) {
                        double price = activity.getPrice() != null ? activity.getPrice() : 0.0;
                        totalPrice += price;
                        
                        html.append("<div class='activity'>")
                            .append("<h4>").append(activity.getActivityName()).append("</h4>")
                            .append("<p><strong>Category:</strong> ").append(activity.getCategory()).append("</p>")
                            .append("<p><strong>Description:</strong> ").append(activity.getDescription() != null ? activity.getDescription() : "No description available").append("</p>")
                            .append("<p><strong>Duration:</strong> ").append(activity.getDuration()).append(" hours</p>")
                            .append("<p><strong>Price:</strong> <span class='price'>€").append(String.format("%.2f", price)).append("</span></p>")
                            .append("</div>");
                    }
                    
                    html.append("<p><strong>Subtotal for ").append(destination.getDestinationName()).append(":</strong> <span class='price'>€").append(String.format("%.2f", totalPrice)).append("</span></p>");
                }
                
                html.append("</div>");
            }
        }
        
        // Add notes if available
        if (tripData.getNotes() != null && !tripData.getNotes().trim().isEmpty()) {
            html.append("<div class='section'>")
                .append("<h2>📝 Additional Information</h2>")
                .append("<p>").append(tripData.getNotes()).append("</p>")
                .append("</div>");
        }
        
        html.append("<div class='section'>")
            .append("<h2>🔗 Important Links</h2>")
            .append("<p><strong>View Your Full Itinerary:</strong></p>")
            .append("<a href='").append(googleSheetUrl).append("' class='btn'>📊 Open Google Sheet</a>")
            .append("<p style='margin-top: 15px; font-size: 14px; color: #666;'>")
            .append("Your complete itinerary with all details is available in the Google Sheet. You can access it anytime using the link above.")
            .append("</p>")
            .append("</div>");
        
        html.append("<div class='section'>")
            .append("<h2>📞 Next Steps</h2>")
            .append("<ol>")
            .append("<li>Review your itinerary in the Google Sheet</li>")
            .append("<li>Our team will contact you within 24 hours to confirm details</li>")
            .append("<li>Payment arrangements will be discussed during the call</li>")
            .append("<li>Final confirmations and travel documents will be provided</li>")
            .append("</ol>")
            .append("</div>");
        
        html.append("</div>")
            .append("<div class='footer'>")
            .append("<p>🌍 <strong>MyHive Travel</strong></p>")
            .append("<p>Creating unforgettable travel experiences</p>")
            .append("<p style='font-size: 12px;'>This is an automated message. Please do not reply to this email.</p>")
            .append("<p style='font-size: 12px;'>For support, contact us at support@myhive-travel.com</p>")
            .append("</div>")
            .append("</div>")
            .append("</body></html>");
        
        return html.toString();
    }
    
    public void sendBookingNotification(String toEmail, String customerName, TripExportRequest tripData) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("🆕 New Booking: " + customerName);
            
            StringBuilder text = new StringBuilder();
            text.append("New booking received:\n\n");
            text.append("Customer: ").append(customerName).append("\n");
            text.append("Email: ").append(tripData.getUserEmail()).append("\n");
            text.append("Trip: ").append(tripData.getTripName()).append("\n");
            text.append("Activities: ").append(tripData.getDestinations().get(0).getActivities().size()).append(" selected\n");
            text.append("Booking Date: ").append(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))).append("\n\n");
            text.append("Please check the Google Sheet for complete details.");
            
            message.setText(text.toString());
            mailSender.send(message);
            
            log.info("Booking notification sent to admin: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send booking notification to admin: {}", toEmail, e);
        }
    }
}
