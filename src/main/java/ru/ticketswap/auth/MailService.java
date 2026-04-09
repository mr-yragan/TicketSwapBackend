package ru.ticketswap.auth;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import ru.ticketswap.common.MailDeliveryException;
import ru.ticketswap.config.TicketSwapProperties;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class MailService {

    private static final DateTimeFormatter EXPIRES_AT_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JavaMailSender mailSender;
    private final TicketSwapProperties properties;

    public MailService(JavaMailSender mailSender, TicketSwapProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendTwoFactorCode(String recipientEmail, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(recipientEmail);
        message.setSubject("TicketSwap login confirmation code");
        message.setText("""
                Your TicketSwap confirmation code is: %s

                The code expires at: %s

                If you did not try to sign in, ignore this email.
                """.formatted(
                code,
                EXPIRES_AT_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC))
        ));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new MailDeliveryException("Unable to send confirmation code", ex);
        }
    }

    public void sendPasswordResetLink(String recipientEmail, String token, Instant expiresAt) {
        String resetLink = UriComponentsBuilder.fromUriString(properties.getMail().getPasswordResetUrlBase())
                .queryParam("token", token)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(recipientEmail);
        message.setSubject("TicketSwap password reset");
        message.setText("""
                We received a request to reset your TicketSwap password.

                Reset link: %s

                The link expires at: %s

                If you did not request a password reset, ignore this email.
                """.formatted(
                resetLink,
                EXPIRES_AT_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC))
        ));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new MailDeliveryException("Unable to send password reset email", ex);
        }
    }
}
