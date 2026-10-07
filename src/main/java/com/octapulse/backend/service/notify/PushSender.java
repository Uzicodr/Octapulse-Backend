package com.octapulse.backend.service.notify;

import com.octapulse.backend.domain.DeviceToken;

import java.util.List;
import java.util.Map;

/** Delivers a push to a user's devices. Swap in an FCM/APNs implementation as a bean to go live. */
public interface PushSender {
    void send(List<DeviceToken> devices, String title, String body, Map<String, Object> data);
}
