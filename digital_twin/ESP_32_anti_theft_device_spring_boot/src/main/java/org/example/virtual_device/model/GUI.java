package org.example.virtual_device.model;

import org.example.virtual_device.services.SensorService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

public class GUI extends JFrame {

    private final JComboBox<String> sensorDropdown;
    private final JTextField tokenField;
    private final SensorService sensorService;
    private final JTextArea consoleArea;
    private final RestTemplate restTemplate = new RestTemplate();

    public GUI(SensorService sensorService) {
        super("Seqjuriti");
        this.sensorService = sensorService;

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridLayout(2, 1));

        // Token input
        tokenField = new JTextField();
        topPanel.add(new JLabel("Access Token:"));
        topPanel.add(tokenField);

        // Dropdown menu
        sensorDropdown = new JComboBox<>();
        refreshSensorDropdown();
        topPanel.add(new JLabel("Select Sensor:"));
        topPanel.add(sensorDropdown);

        // Console area
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        redirectSystemOutToTextArea();

        // Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 6));

        buttonPanel.add(createApiButton("Register", "/sensor/register"));
        buttonPanel.add(createApiButton("Start", "/sensor/start"));
        buttonPanel.add(createApiButton("Stop", "/sensor/stop"));
        buttonPanel.add(createApiButton("Arm", "/sensor/arm"));
        buttonPanel.add(createApiButton("Disarm", "/sensor/disarm"));
        JButton button = new JButton("Clear");
        button.addActionListener(e -> consoleArea.setText(""));
        buttonPanel.add(button);

        setLayout(new BorderLayout());

        add(topPanel, BorderLayout.NORTH);
        this.add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(700, 400);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    private JButton createApiButton(String label, String endpoint) {
        JButton button = new JButton(label);
        button.addActionListener(e -> {
            String token;
            if ("Register".equals(label)) {
                token = tokenField.getText().trim();
            } else
                token = String.valueOf(sensorDropdown.getSelectedItem());

            if (token.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Token cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8080" + endpoint, request, String.class);
                JOptionPane.showMessageDialog(this, "Response: " + response.getStatusCode(), "API Call", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage().substring(ex.getMessage().lastIndexOf(":") + 2), "Exception", JOptionPane.ERROR_MESSAGE);
            }

            if ("Register".equals(label)) {
                refreshSensorDropdown();
                tokenField.setText("");
            }
        });
        return button;
    }

    private void redirectSystemOutToTextArea() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                consoleArea.append(String.valueOf((char) b));
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }

            @Override
            public void write(byte[] b, int off, int len) {
                consoleArea.append(new String(b, off, len));
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }
        };
        PrintStream printStream = new PrintStream(out, true);
        System.setOut(printStream);
    }

    private void refreshSensorDropdown() {
        sensorDropdown.removeAllItems();
        for (String token : sensorService.getAllSensors().keySet()) {
            sensorDropdown.addItem(token);
        }
    }
}
