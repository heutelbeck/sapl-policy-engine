package io.sapl.prp.filemonitoring;

import lombok.Value;

import java.io.File;

@Value
public class FileChangedEvent implements FileEvent {
    File file;
}