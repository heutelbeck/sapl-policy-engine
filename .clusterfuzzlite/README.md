Clusterfuzzlite uses images which have JDK 15 installed, this project needs atleast JDK 17.
That's why our tests are run with the JUnit-Integration (which uses libfuzzer
under the hood as well as Clusterfuzzlite does).