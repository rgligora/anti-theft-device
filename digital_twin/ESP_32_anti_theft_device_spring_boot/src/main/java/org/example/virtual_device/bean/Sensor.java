package org.example.virtual_device.bean;

import lombok.Getter;
import org.example.virtual_device.UserVariables;
import org.example.virtual_device.model.Coordinate;
import org.example.virtual_device.services.CommunicationService;
import org.example.virtual_device.services.RouteService;

import java.util.List;


public class Sensor {
    String accessToken;
    int currentLocationIndex;
    List<Coordinate> route;
    @Getter
    boolean armed;
    boolean motionDetected;
    private final CommunicationService communicationService = new CommunicationService();
    @Getter
    private Thread activeThread;
    private Thread motionThread;

    public Sensor(String accessToken) {
        this.accessToken = accessToken;
        currentLocationIndex = 0;
        armed = false;
        route = RouteService.generateRoute();
        startSensor();
    }

    public void motionThread() {
        motionThread = new Thread(() -> {
            // random time before motion
            try {
                Thread.sleep(((int) ((Math.random() * (UserVariables.maxWakeupTime - UserVariables.minWakeupTime)) + UserVariables.minWakeupTime)) * 1000L);
                motionDetected = true;
                System.out.println("Senosr " + accessToken + " detected motion");
                if (armed) {
                    System.out.println("Sensor: " + accessToken + " Buzzer is on");
                    System.out.println("Sensor: " + accessToken + " LED is on");
                }
            } catch (InterruptedException ignored) {
            }
        });
        motionThread.start();
    }

    public void startSensor() {
        activeThread = new Thread(() -> {
            System.out.println("Starting sensor: " + accessToken);
            while (true) {
                communicationService.sendCoordinate(accessToken, route.get(currentLocationIndex), motionDetected, armed);
                try {
                    Thread.sleep(UserVariables.delayBetweenSending * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                if (motionDetected) {
                    if (currentLocationIndex + 1 < route.size()) {
                        currentLocationIndex++;
                    } else {
                        currentLocationIndex = route.size() - 1;
                    }
                }
            }
        });
        activeThread.start();
        motionThread();
    }

    public void armSensor() {
        if (armed) {
            //System.out.println("Sensor " + id + " is already armed");
            return;
        }
        armed = true;
        System.out.println("Armed sensor: " + accessToken);
        if (motionDetected) {
            System.out.println("Sensor: " + accessToken + " Buzzer is on");
            System.out.println("Sensor: " + accessToken + " LED is on");
        }
    }

    public void disarmSensor() {
        if (!armed) {
            // System.out.println("Sensor " + id + " is already disarmed");
            return;
        }
        System.out.println("Disarmed sensor: " + accessToken);
        if (motionDetected) {
            System.out.println("Sensor: " + accessToken + " Buzzer is off");
            System.out.println("Sensor: " + accessToken + " LED is off");
        }
        armed = false;
    }

    public void stopSensor() {
        System.out.println("Stopping senosr: " + accessToken);
        activeThread.interrupt();
        motionThread.interrupt();
        resetSensor();
    }

    public void resetSensor() {
        currentLocationIndex = 0;
        armed = false;
        motionDetected = false;
        route = RouteService.generateRoute();
        activeThread = null;
    }
}
