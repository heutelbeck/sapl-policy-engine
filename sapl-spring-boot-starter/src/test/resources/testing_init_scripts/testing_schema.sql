DROP TABLE IF EXISTS Person;
CREATE TABLE Person (id INT PRIMARY KEY, firstname VARCHAR(255), age INTEGER, active BOOLEAN);

INSERT INTO Person (id, firstname, age, active) VALUES (1, 'Alice', 30, true);
INSERT INTO Person (id, firstname, age, active) VALUES (2, 'Bob', 25, false);
INSERT INTO Person (id, firstname, age, active) VALUES (3, 'Charlie', 35, true);
INSERT INTO Person (id, firstname, age, active) VALUES (4, 'David', 28, true);
INSERT INTO Person (id, firstname, age, active) VALUES (5, 'Emma', 40, false);
INSERT INTO Person (id, firstname, age, active) VALUES (6, 'Frank', 32, true);
INSERT INTO Person (id, firstname, age, active) VALUES (7, 'Grace', 27, false);
INSERT INTO Person (id, firstname, age, active) VALUES (8, 'Hannah', 38, true);
INSERT INTO Person (id, firstname, age, active) VALUES (9, 'Ian', 45, true);
INSERT INTO Person (id, firstname, age, active) VALUES (10, 'Julia', 29, false);
