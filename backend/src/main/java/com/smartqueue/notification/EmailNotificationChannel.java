package com.smartqueue.notification;

import com.smartqueue.config.AppProperties;
import com.smartqueue.entity.NotificationChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);
    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties props;

    public EmailNotificationChannel(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void send(OutboundMessage m) {
        if (!props.getMail().isEnabled() || m.email() == null) {
            return;
        }
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(props.getMail().getFrom());
            mail.setTo(m.email());
            mail.setSubject(m.title());
            mail.setText(m.message());
            s.send(mail);
        } catch (Exception e) {
            // Never log the recipient address or message body.
            log.warn("Email delivery failed for user {}: {}", m.userId(), e.getClass().getSimpleName());
        }
    }
}
