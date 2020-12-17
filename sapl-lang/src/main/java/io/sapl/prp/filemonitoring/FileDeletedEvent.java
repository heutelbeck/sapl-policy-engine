package io.sapl.prp.filemonitoring;

import lombok.Value;

import java.io.File;

@Value
public class FileDeletedEvent implements FileEvent {
    File file;
}