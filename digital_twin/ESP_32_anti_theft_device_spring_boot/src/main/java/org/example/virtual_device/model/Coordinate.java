package org.example.virtual_device.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Coordinate {
    Double latitude;
    Double longitude;

    @Override
    public String toString() {
        return latitude + ", " + longitude;
    }
}
