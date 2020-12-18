package io.sapl.util.filemonitoring;

import java.io.File;

import lombok.Value;

@Value
public class FileChangedEvent implements FileEvent {
    File file;
}