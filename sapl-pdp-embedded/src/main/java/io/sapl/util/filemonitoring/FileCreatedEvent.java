package io.sapl.util.filemonitoring;

import java.io.File;

import lombok.Value;

@Value
public class FileCreatedEvent implements FileEvent {
    File file;
}