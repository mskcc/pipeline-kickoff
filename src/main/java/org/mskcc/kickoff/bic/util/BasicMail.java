package org.mskcc.kickoff.bic.util;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Date;
import java.util.Properties;

/**
 * @usage:
 * mail.send("gabow@cbio.mskcc.org", "gabowa@mskcc.org,gabowa@mskcc.org", "cbio.mskcc.org", "Hello", "hi");
 * mail.send("kristakaz@cbio.mskcc.org", "kazmierk@mskcc.org", "cbio.mskcc.org", "Hello2", "This does have an attachment", "/ifs/projects/BIC/drafts/Proj_06774_E/Proj_06774_E_sample_key.xlsx");
 */
public class BasicMail {

    public void send(String from, String to, String host, String subject, String text) throws MessagingException {
        {
            Properties props = System.getProperties();
            props.put("mail.smtp.host", host);
            Session mailSession = Session.getDefaultInstance(props, null);

            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(from));
            String[] recipients = to.split(",");
            InternetAddress[] addresses = new InternetAddress[recipients.length];

            for (int i = 0; i < recipients.length; i++) {
                // message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipients[i]));
                addresses[i] = new InternetAddress(recipients[i]);
            }
            message.addRecipients(Message.RecipientType.TO, addresses);
            message.setSubject(subject);
            message.setText(text);
            message.setSentDate(new Date());

            Transport.send(message);
        }
    }

    //This send will attach files to the email you are senidng
    public void send(String from, String to, String host, String subject, String text, String files) throws MessagingException {
        {
            Properties props = System.getProperties();
            props.put("mail.smtp.host", host);
            Session mailSession = Session.getDefaultInstance(props, null);

            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(from));
            String[] recipients = to.split(",");
            InternetAddress[] addresses = new InternetAddress[recipients.length];

            for (int i = 0; i < recipients.length; i++) {
                // message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipients[i]));
                addresses[i] = new InternetAddress(recipients[i]);
            }
            message.addRecipients(Message.RecipientType.TO, addresses);
            message.setSubject(subject);
            //message.setText(text);
            // This is a multipart message, so create Multipart (message) and add the BodyParts
            Multipart multipart = new MimeMultipart();
            BodyPart messageParts = new MimeBodyPart();

            // First add the body text
            messageParts.setText(text + "\n");
            multipart.addBodyPart(messageParts);

            //Next add files
            String[] filenames = files.split(",");

            for (int x = 0; x < filenames.length; x++) {
                messageParts = new MimeBodyPart();
                DataSource source = new FileDataSource(filenames[x]);
                messageParts.setDataHandler(new DataHandler(source));
                String[] nameParts = filenames[x].split("/");
                String name = nameParts[nameParts.length - 1];
                messageParts.setFileName(name);
                multipart.addBodyPart(messageParts);
            }

            // Now set content to multipart
            message.setContent(multipart);

            message.setSentDate(new Date());

            Transport.send(message);
        }
    }
}
