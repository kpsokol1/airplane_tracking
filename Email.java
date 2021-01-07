package com.planes.kyle;

import javafx.scene.chart.ScatterChart;

import java.util.*;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Email {
    //keeps from making copies of the instance variables evrytime the function is called
    private static final String emailUsername = Utilities.getAuth("emailUsername");
    private static final String emailPassword = Utilities.getAuth("emailPassword");


    public static void sendEmail(String sendTo, String subjectLine, String messageToSend, String fileLocation) {
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
        Date date = new Date();
        try {
            // Get system properties
            Properties properties = System.getProperties();

            //Setup mail server
            properties.setProperty("mail.smtp.auth", "true");

            //Use secure connection (SSL/TLS)
            properties.setProperty("mail.smtp.starttls.enable", "true");

            //Set SMTP server hostname
            properties.setProperty("mail.smtp.host", "smtp.gmail.com");

            //Need to trust certificate from server
            properties.setProperty("mail.smtp.ssl.trust", "smtp.gmail.com");

            //Set port
            properties.setProperty("mail.smtp.port", "587");


            //Set username and password for sending mail account
            properties.setProperty("mail.smtp.user", emailUsername);
            properties.setProperty("mail.smtp.password", emailPassword);

            //Get the default session object.
            Session session = Session.getInstance(properties, new Authenticator()
            {
                @Override
                protected PasswordAuthentication getPasswordAuthentication()
                {
                    return new PasswordAuthentication(emailUsername, emailPassword);
                }
            });

            //Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            //Set From: header field of the header.
            message.setFrom(new InternetAddress(emailUsername));

            //Set To: header field of the header.
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(sendTo));

            //Set Subject: header field
            message.setSubject(subjectLine);

            //Create the message part
            BodyPart messageBodyPart = new MimeBodyPart();

            //Set the body of the email
            messageBodyPart.setText(messageToSend + "\n" + "http://radar24:8080" + "\nTime Sent: " + formatter.format(date));
            //Create a multipart message
            Multipart multipart = new MimeMultipart();

            //Set text message part
            multipart.addBodyPart(messageBodyPart);

            //Attach file
            if(fileLocation != "none") {
                messageBodyPart = new MimeBodyPart();
                DataSource source = new FileDataSource(fileLocation);
                messageBodyPart.setDataHandler(new DataHandler(source));
                messageBodyPart.setFileName(fileLocation);
                multipart.addBodyPart(messageBodyPart);
            }


            //Send the complete message parts
            message.setContent(multipart);

            //Send message
            Transport.send(message);
            System.out.println("Message Sent");
            //lastTimeEmailSent = getCurrentTime().toString();
        }
        catch (MessagingException ex) {
            ex.printStackTrace();
            System.out.println("Email1");
        }
    }
}



