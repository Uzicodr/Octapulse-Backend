package com.octapulse.backend.service.notify;

/** Sends a transactional email. Swap in a provider implementation as a bean to go live. */
public interface EmailSender {
    void send(String to, String subject, String body);
}
