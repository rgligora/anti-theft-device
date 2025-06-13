package org.example.virtual_device.services;

import lombok.NoArgsConstructor;
import org.example.virtual_device.UserVariables;
import org.example.virtual_device.model.Coordinate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;


@Service
@NoArgsConstructor
public class CommunicationService {

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendCoordinate(String accessToken, Coordinate location, boolean motionDetected, boolean armed) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);
            Map<String, Object> payload = createPayload(location, motionDetected, armed);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(UserVariables.platformAPIURL, request, String.class);
            System.out.println(accessToken + " Sent: " + payload + " => Response: " + response.getStatusCode());
            //System.out.println(accessToken + " " + payload);
        } catch (Exception e) {
            System.err.println("Failed to send data: " + e.getMessage());
        }
    }

    private static Map<String, Object> createPayload(Coordinate location, boolean motionDetected, boolean armed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("motion_detected", motionDetected);
        payload.put("gyro_x", 12.3);
        payload.put("gyro_y", -4.5);
        payload.put("gyro_z", 0.8);
        payload.put("armed", armed);
        if (motionDetected && armed) {
            payload.put("latitude", location.getLatitude());
            payload.put("longitude", location.getLongitude());
        }
        return payload;
    }
}
