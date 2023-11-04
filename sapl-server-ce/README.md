# SAPL Server CE

This is a lightweight PDP server storing policies in a MariaDB and offering a simple WebUI for policy and PDP administration.

The server can be run locally via maven or by executing the JAR. 
Alternatively, a container image and configurations for deployment 
on Docker and/or Kubernetes is available.

## Local Execution

### Running the Server from Source

Disclaimer: It is likely that you only need to run the server from source if you are a contributor to the policy engine project.

The source of the policy engine is found on the public [GitHub](https://github.com/) repository: <https://github.com/heutelbeck/sapl-policy-engine>.

First clone the repository:

```shell
git clone https://github.com/heutelbeck/sapl-policy-engine.git
```

Alternatively download the current version as a source ZIP file: <https://github.com/heutelbeck/sapl-policy-engine/archive/master.zip>.

To run the server from source, first build the complete policy engine project from within the projects root folder
(i.e., ```sapl-policy-engine```). The system requires maven and JDK 11 or later to be installed.
The build is triggered by issuing the command:

```shell
mvn clean install
```

Afterward, change to the folder ```sapl-policy-engine/sapl-server-ce``` and run the application:

```shell
mvn spring-boot:run
```

The server will startup in demo mode.

To log in use the demo credentials:

* Username: demo
* Password: demo


> #### Note: Building a Docker Image
> 
> To build the docker image of the server application locally, you need to have docker installed on the build machine.
> The image build is triggered by activating the docker maven profile of the project. This should result in the image being installed in your local docker repository. Example:
>
> ```shell
> mvn clean install -P docker,production,mariadb,!h2
> ```

### Using the PDP

By default, the server will use a self-signed certificate and expose the PDP API under <https://localhost:8443/api/pdp>
To override this certificate, use the matching Spring Boot settings, e.g.:

```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store-type=PKCS12
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=localhostpassword
server.ssl.key-alias=tomcat
```

API access requires "Basic Auth". Use the Web UI to add client credentials. 


The server is implemented using Spring Boot. Thus, there are several ways to configure the 
application. 
Please consult the matching chapter of the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config).

## Testing the Server

A sample application that can be configured to use this PDP server (remote PDP) can be found
in the [demo-applications](https://github.com/heutelbeck/sapl-demos) project for the SAPL
Policy Engine in the module `sapl-demo-reactive`.

A self-signed certificate for localhost testing can be generated using the JDKs keytool, or [mkcert](https://github.com/FiloSottile/mkcert). Examples:

```shell
keytool -genkeypair -keystore keystore.p12 -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" -keypass changeme -storepass changeme -keyalg RSA -alias netty -ext SAN=dns:localhost,ip:127.0.0.1
```

```shell
mkcert -pkcs12 -p12-file self-signed-cert.p12 localhost 127.0.0.1 ::1
```

## Containerized Cloud Deployment

### Running on Kubernetes

This section will describe the deployment on a bare-metal Kubernetes installation on a Linux system like Ubuntu server which has Port 80 and 443 exposed to the Internet 
and will use the Kubernetes Nginx-Ingress-Controller as well as cert-manager to manage the Let's Encrypt certificates (Only if Ports are exposed to the Internet so Let's Encrypt can access the URL)

#### Prerequisites

This tutorial uses Kubernetes v1.23 self-hosted on an Ubuntu Server. If you use a Commercial Platform you have to create persistent Volumes yourself with the specified StorageClassNames.
 

Create a persistent Volume 
```
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-db-pv.yml
```

Install a MariaDB instance with Helm 
```
kubectl create namespace sapl-server-ce
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install sapl-ce-mariadb --set auth.rootPassword=Q8g7SvwDso5svlebNLQO,auth.username=saplce,auth.password=cvm72OadXaOGgbQ5F9ao,primary.persistence.storageClass=saplcedb bitnami/mariadb -n sapl-server-ce
```

Log into the pod and create Database with Latin-1
```
kubectl exec --stdin --tty -n sapl-server-ce sapl-ce-mariadb-0 -- /bin/bash
mysql -u root -p
CREATE DATABASE saplce CHARACTER SET = 'latin1' COLLATE = 'latin1_swedish_ci';
GRANT ALL PRIVILEGES ON saplce.* TO `saplce`@`%`;
FLUSH Privileges;
```

#### Kubernetes Deployment with Let's Encrypt Certificate

This section assumes that the Kubernetes is installed on a Linux OS f.e. Ubuntu with exposed ports 80 and 443 to the internet and matching DNS entries.

Install NGINX Ingress Controller according to https://kubernetes.github.io/ingress-nginx/deploy/

```shell
helm upgrade --install ingress-nginx ingress-nginx --repo https://kubernetes.github.io/ingress-nginx --namespace ingress-nginx --create-namespace --set controller.hostNetwork=true,controller.kind=DaemonSet
```

Install Cert-Manager according to https://cert-manager.io/docs/installation/kubernetes/ 

```shell
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.7.2/cert-manager.yaml
```

Change the Email address in the Clusterissuer.yaml (Line email: user@email.com)

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/clusterissuer.yml
kubectl apply -f clusterissuer.yml 
```

Apply the Persistent Volume YAML file (or create persistent volumes with the StorageClassNames detailed in the YAML file according to your preferred Method)

```shell
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-pv.yml -n sapl-server-ce
```

Download the Config Files from the Kubernetes/config folder and copy them to the config directory specified in the config-section of sapl-server-ce-pv.yml `/data/sapl-server-ce/conf`

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-TLS.tar
tar -xf sapl-server-ce-TLS.tar -C /data/sapl-server-ce/conf
```

Then download the TLS YAML file 

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-tls.yml
```

change the URL in the Ingress section 

```
  tls:
    - hosts:
        - saplce.exampleurl.com
      secretName: saplce.lt.local-tls
  rules:
    - host: saplce.exampleurl.com
```

then apply the YAML file

```shell
kubectl apply -f sapl-server-ce-tls.yml -n sapl-server-ce
```

Change the Admin Login credentials 

Edit the files admin-username and encoded-admin-password located in /data/sapl-server-ce/conf/io/sapl/server 
For testing you can use a public bcrypt hashing tool (e.g., https://bcrypt-generator.com/) to generate the password configuration for the user. Make sure to prepend {bcrypt} to the hash.

The service should be reachable under the URL defined in the Ingress section of the sapl-server-ce-tls.yml <https://sapl.exampleurl.com/>.

#### Kubernetes Deployment with Nodeport 

Under this section, the Process is detailed with a custom certificate for Intranet testing.


Apply the Persistent Volume YAML file (or create persistent volumes with the StorageClassNames detailed in the YAML file according to your preferred Method).

```shell
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-pv.yml -n sapl-server-ce
```

Download the Config Files from the Kubernetes/config folder and copy them to the config directory specified in the config-section of sapl-server-ce-pv.yml `/data/sapl-server-ce/conf`

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-NodePort.tar
tar -xf sapl-server-ce-NodePort.tar -C /data/sapl-server-ce/conf
```

Apply the NodePort YAML file 

```shell
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-server/main/sapl-server-ce/kubernetes/sapl-server-ce-NodePort.yml
```

Change the Admin Login credentials 

Edit the files admin-username and encoded-admin-password located in `/data/sapl-server-ce/conf/io/sapl/server` 
For testing you can use a public bcrypt hashing tool (e.g., https://bcrypt-generator.com/) to generate the password configuration for the user. Make sure to prepend {bcrypt} to the hash.

Identify the Port 

```shell
kubectl get services -n sapl-server-ce 
```

The output should look like this:
  
sapl-server-ce NodePort 10.107.25.241 <none> 8443:30773/TCP 

The service should be reachable in this Example under <https://localhost:30773/> or if you access it from another pc <https://server-ip-adress:30773/>.

### Kubernetes Troubleshooting

A pod constantly restarts:

Check if the local directory can only be accessed by root and change it to the normal account.

### Custom Policy Information Points (PIPs) in the Kubernetes Deployment

The Pod internal folder `/pdp/data/lib` is mounted as a persistent Volume and can be accessed and manipulated on the path `/data/sapl-server-ce/lib/` see below

## Custom Policy Information Points (PIPs) or Function Libraries

To support new attributes and functions, the matching libraries have to be deployed alongside the server application. One way to do so is to create your own server project and add the libraries to the dependencies of the application via maven dependencies and to add the matching packages to the component scanning of Spring Boot and/or to provide matching configurations. Alternatively, the SAPL Server LT supports the side-loading of external JARs. 

To load a custom PIP, the PIP has to be built as a JAR and all dependencies not already provided by the server have to be provided as JARs as well. Alternatively, the PIP can be packaged as a so-called "fat JAR" including all dependencies. This can be achieved using the (Maven Dependency Plugin)[https://maven.apache.org/plugins/maven-dependency-plugin/] and an example for this approach can be found here: <https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-pip-http>.

The SAPL Server LT will scan all packages below ```io.sapl.server``` for Spring beans or configurations providing PIPs or Function libraries at startup and load them automatically. Thus, the custom libraries must provide at least a matching spring configuration under this package.

The JAR files are to be put into the folder `/pdp/data/lib` in the directory where policies are stored. Changes only take effect upon restart of the server application. To change the folder, overwrite the property `loader.path`.

