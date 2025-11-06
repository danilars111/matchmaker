package org.poolen.web.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender javaMailSender;

    // It reads the username right from the application properties that your PropertiesManager built!
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Sends a simple plain text email.
     * @param to The recipient's email address.
     * @param subject The subject line of the email.
     * @param text The plain text body of the email.
     */
    public void sendSimpleMessage(String to, String subject, String text) {
        logger.info("Attempting to send email to {} with subject '{}'", to, subject);
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            // We use the 'fromEmail' from our properties file.
            // This is crucial for Gmail, as the "From" must match the login user!
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            javaMailSender.send(message);
            logger.info("Email sent successfully to {}!", to);

        } catch (MailException e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * Sends an email with a file attachment.
     * @param to The recipient's email address.
     * @param subject The subject line of the email.
     * @param text The plain text body of the email.
     * @param pathToAttachment The absolute file path to the log file to attach.
     */
    public void sendMessageWithAttachment(String to, String subject, String text, String pathToAttachment) {
        logger.info("Attempting to send email with attachment to {}...", to);
        try {
            // Create a MimeMessage, which is the only kind that can handle attachments
            MimeMessage message = javaMailSender.createMimeMessage();

            // Use a MimeMessageHelper to make our lives easier
            // The 'true' means this is a 'multipart' message (i.e., it has parts like text AND a file)
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);

            // Now for the attachment!
            FileSystemResource file = new FileSystemResource(new File(pathToAttachment));

            if (!file.exists()) {
                logger.error("Attachment file not found at path: {}", pathToAttachment);
                // We'll just log the error and not send the email
                return;
            }

            // We use file.getFilename() so it has a nice name in the email
            helper.addAttachment(file.getFilename(), file);

            javaMailSender.send(message);
            logger.info("Email with attachment sent successfully to {}!", to);

        } catch (MessagingException | MailException e) {
            // We catch MessagingException because the MimeMessageHelper can throw it!
            logger.error("Failed to send email with attachment to {}: {}", to, e.getMessage(), e);
        }
    }

}
