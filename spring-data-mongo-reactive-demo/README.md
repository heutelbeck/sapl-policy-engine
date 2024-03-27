# Introduction of the demo application

This small application is intended to illustrate the use of the Mongo reactive module. 

It has been built so that only the software called Docker is required to run it. A test database has been added to this demo and this database is started as a Docker container to communicate with the application. 

## Additional software required:
Docker Engine and Docker Compose must be installed on the computer for the Docker containers. Docker Engine and Docker Compose can be installed as independent binary files. Alternatively, Docker Desktop can be used, which already contains Docker Engine and Docker Compose.

## Preparations:
The demo must be built once and the dependencies installed. The demo uses Maven, so the ``mvn install`` command takes over this task. It should be noted here that Docker must already be running in the background, as the tests of the demo are also executed with the ``mvn install`` command. The tests communicate with a test container.  The integration tests that require a test container for the database are located in the following path: ``io.sapl.springdatar2dbcdemo.demo.rest.integration``

## Start the demo: 
A file called ``docker-compose.yml`` is located in the root directory of the demo. To start this, a terminal must be opened in the root directory. The command ``docker-compose up`` then executes the YML file in the terminal. Docker now installs all dependencies in the YML file and then starts the database container. Once the database container is running, the demo can be started via the main method.  

## User manual of the demo:
The demo is structured in such a way that there are always two versions for a database query. One version executes the database query without the use of SAPL and the other includes the protection of SAPL. Each version of all database queries has its own residual endpoint for illustration purposes. An overview of all database queries can be found in the path ``http://localhost:8080/info``. The demo does not have a graphical user interface, but objects of type JSON can be displayed in a structured manner via the rest endpoints. The info endpoint lists all information about the various database queries. This includes, among other things 

* Type of database method
* Name of the method	
* Original query	
* Manipulated query	
* Link to call the remaining endpoint with original query	
* Link to call the remaining endpoint with a manipulated query	
* Policy used	

The links already have filled-in parameters for the remaining endpoint, but these can be changed. If, for example, the second parameter with the value ``USER`` of the remaining endpoint ``http://localhost:8080/user/findAllByAgeAfterAndRole/18/USER`` is set below ``ADMIN``, an exception called ``AccessDeniedException`` is thrown. The examples from the demo are only intended to illustrate the new functionalities that the Mongo reactive module provides. No attention was paid to meaningfulness.   
 