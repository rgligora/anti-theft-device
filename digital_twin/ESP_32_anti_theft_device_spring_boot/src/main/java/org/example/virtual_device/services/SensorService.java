package org.example.virtual_device.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.virtual_device.bean.Sensor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SensorService {

    @Getter
    Map<String, Sensor> allSensors = new HashMap<>();

    public boolean registerSensor(String accessToken) {
        if (!allSensors.containsKey(accessToken)) {
            allSensors.put(accessToken, new Sensor(accessToken));
            System.out.println("Sensor: " + accessToken + " registered");
            return true;
        }
        return false;
    }

    public boolean armSensor(String accessToken) {
        if (allSensors.containsKey(accessToken) && allSensors.get(accessToken).getActiveThread() != null) {
            allSensors.get(accessToken).armSensor();
            return true;
        }
        return false;
    }

    public boolean disarmSensor(String accessToken) {
        if (allSensors.containsKey(accessToken) && allSensors.get(accessToken).getActiveThread() != null) {
            allSensors.get(accessToken).disarmSensor();
            return true;
        }
        return false;
    }

    public boolean stopSensor(String accessToken) {
        if (allSensors.containsKey(accessToken)) {
            if (allSensors.get(accessToken).getActiveThread() != null) {
                allSensors.get(accessToken).stopSensor();
            }
            return true;
        }
        return false;
    }

    public boolean startSensor(String accessToken) {
        if (allSensors.containsKey(accessToken)) {
            if (allSensors.get(accessToken).getActiveThread() == null) {
                allSensors.get(accessToken).startSensor();
            }
            return true;
        }
        return false;
    }
}
