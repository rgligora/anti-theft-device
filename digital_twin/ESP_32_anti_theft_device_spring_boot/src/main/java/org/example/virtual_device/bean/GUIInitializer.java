package org.example.virtual_device.bean;

import org.example.virtual_device.model.GUI;
import org.example.virtual_device.services.SensorService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.swing.*;

@Component
public class GUIInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final SensorService sensorService;

    public GUIInitializer(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI(sensorService);
            gui.setVisible(true);
        });
    }
}
