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
        message.setSubject("Код подтверждения входа в TicketSwap");
        message.setText("""
                Ваш код подтверждения TicketSwap: %s
                Срок действия кода истекает: %s

                Если вы не пытались войти, проигнорируйте это письмо.
                """.formatted(
                code,
                EXPIRES_AT_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC))
        ));

        send(message, "Не удалось отправить код подтверждения");
    }

    public void sendPasswordResetLink(String recipientEmail, String token, Instant expiresAt) {
        String resetLink = UriComponentsBuilder.fromUriString(properties.getMail().getPasswordResetUrlBase())
                .queryParam("token", token)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(recipientEmail);
        message.setSubject("Сброс пароля TicketSwap");
        message.setText("""
                Мы получили запрос на сброс пароля TicketSwap.

                Ссылка для сброса: %s
                Срок действия ссылки истекает: %s

                Если вы не запрашивали сброс пароля, проигнорируйте это письмо.
                """.formatted(
                resetLink,
                EXPIRES_AT_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC))
        ));

        send(message, "Не удалось отправить письмо для сброса пароля");
    }

    public void sendEmailVerificationLink(String recipientEmail, String token, Instant expiresAt) {
        String verifyLink = UriComponentsBuilder.fromUriString(properties.getMail().getEmailVerificationUrlBase())
                .queryParam("token", token)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(recipientEmail);
        message.setSubject("Подтверждение почты TicketSwap");
        message.setText("""
                Добро пожаловать в TicketSwap.

                Пожалуйста, подтвердите адрес почты по этой ссылке:
                %s

                Срок действия ссылки истекает: %s

                Если вы не создавали этот аккаунт, проигнорируйте это письмо.
                """.formatted(
                verifyLink,
                EXPIRES_AT_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC))
        ));

        send(message, "Не удалось отправить письмо для подтверждения почты");
    }

    private void send(SimpleMailMessage message, String errorMessage) {
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new MailDeliveryException(errorMessage, ex);
        }
    }
}