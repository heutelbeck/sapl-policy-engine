package io.sapl.generator;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.LinkedList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class DomainSubject {

    private final String subjectName;

    private final List<String> subjectAuthorities = new LinkedList<>();
}
