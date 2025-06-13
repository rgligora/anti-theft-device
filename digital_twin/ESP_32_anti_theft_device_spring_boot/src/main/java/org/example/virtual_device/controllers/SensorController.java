package org.example.virtual_device.controllers;

import lombok.RequiredArgsConstructor;
import org.example.virtual_device.services.SensorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sensor")
public class SensorController {

    private final SensorService sensorService;

    @PostMapping("register")
    public ResponseEntity<String> registerSensor(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractToken(authHeader);
        boolean response = sensorService.registerSensor(accessToken);
        if (response)
            return ResponseEntity.ok("Sensor " + accessToken + " registered");
        else
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Sensor: " + accessToken + " is already taken.");
    }

    @PostMapping("stop")
    public ResponseEntity<String> stopSensor(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractToken(authHeader);
        boolean response = sensorService.stopSensor(accessToken);
        if (response)
            return ResponseEntity.ok("Sensor " + accessToken + " stopped");
        else
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sensor " + accessToken + " not found");
    }

    @PostMapping("start")
    public ResponseEntity<String> startSensor(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractToken(authHeader);
        boolean response = sensorService.startSensor(accessToken);
        if (response)
            return ResponseEntity.ok("Sensor " + accessToken + " started");
        else
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sensor " + accessToken + " not found");
    }

    @PostMapping("arm")
    public ResponseEntity<String> armSensor(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractToken(authHeader);
        boolean response = sensorService.armSensor(accessToken);
        if (response)
            return ResponseEntity.ok("Sensor " + accessToken + " armed");
        else
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sensor " + accessToken + " not registered or is offline.");
    }

    @PostMapping("disarm")
    public ResponseEntity<String> disarmSensor(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractToken(authHeader);
        boolean response = sensorService.disarmSensor(accessToken);
        if (response)
            return ResponseEntity.ok("Sensor " + accessToken + " disarmed");
        else
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sensor " + accessToken + " not registered or is offline.");
    }

    private String extractToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7); // Strip "Bearer " prefix
        } else {
            throw new IllegalArgumentException("Invalid Authorization header");
        }
    }
}
