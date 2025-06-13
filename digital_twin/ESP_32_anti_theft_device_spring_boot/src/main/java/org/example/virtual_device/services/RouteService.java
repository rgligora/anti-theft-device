package org.example.virtual_device.services;

import org.example.virtual_device.UserVariables;
import org.example.virtual_device.model.Coordinate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class RouteService {

    public static List<Coordinate> generateRoute() {
        List<Coordinate> route = new ArrayList<>();
        String fileName = getRandomSetElement(UserVariables.routeFileNames);
        try (InputStream in = RouteService.class.getResourceAsStream("/routes/" + fileName)) {
            assert in != null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] split = line.trim().split(", ");
                    Double latitude = Double.parseDouble(split[0]);
                    Double longitude = Double.parseDouble(split[1]);
                    route.add(new Coordinate(latitude, longitude));
                }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return route;
    }

    private static <E> E getRandomSetElement(List<E> list) {
        return list.stream().skip(new Random().nextInt(list.size())).findFirst().orElse(null);
    }
}
