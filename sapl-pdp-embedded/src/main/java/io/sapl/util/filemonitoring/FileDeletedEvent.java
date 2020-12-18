package io.sapl.util.filemonitoring;

import java.io.File;

import lombok.Value;

@Value
public class FileDeletedEvent implements FileEvent {
    File file;
}