package io.sapl.springdatar2dbc.database;

import lombok.*;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Data
@Table(name = "person")
@AllArgsConstructor
@NoArgsConstructor
public class Person {
    int     id;
    String  firstname;
    String  lastname;
    int     age;
    Role    role;
    boolean active;
}
