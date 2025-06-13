package org.example.virtual_device;

import java.util.List;

public class UserVariables {
    // time is in seconds
    public static final int minWakeupTime = 15; // minimum time until sensor starts moving
    public static final int maxWakeupTime = 20; // maximum time until sensor starts moving
    public static final int delayBetweenSending = 5; // time between sending next location
    public static final List<String> routeFileNames = List.of("zagrepcanka-filozofski.txt"); // if used with .jar st
    public static final String platformAPIURL = "http://161.53.133.253:8080/api/v1/ly664l9nremjfdcvklw1/telemetry"; // api to which to send sensor data to
    // in CommunicationService change JSON key for id, latitude, longitude as needed
    // in SensorController change CrossOrigin depending on port and need
}
