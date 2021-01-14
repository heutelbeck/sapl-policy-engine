# SAPL Server LT - lightweight authorization server.

This is a lightweight headless PDP server. The server monitors two directories for the PDP settings and SAPL documents, 
allowing for runtime updating of policies which will be reflected in decisions made for ongoing authorization 
subscriptions.

The PDP configuration for combining algorithm and environment variables is expected in a file `pdp.json`. 
All SAPL documents in the folder named `*.sapl` will be published to the PRP.

The server can be run locally via maven or by executing the JAR. 
Alternatively a container image and configurations for deployment 
in Kubernetes is available.

## Local execution

### Running from pre-build JAR

Download the latest build from [here](https://nexus.openconjurer.org/service/rest/v1/search/assets/download?repository=maven-snapshots&group=com.my.company&name=myArtefact&sort=version&direction=desc).
To run the server you need JRE 11 or later installed. Run the server:
```
java -jar sapl-server-lt-2.0.0-SNAPSHOT.jar
```

### Running the server from source

Disclaimer: Running the server from source should only be done if you are a contributor to the policy engine project.
Please use the binary or container for production use.

To run the server from source, first build the complete policy engine project from within the projects root folder
(i.e. ```sapl-policy-engine```). The system requires maven and JDK 11 or later to be installed.
The build is triggered by issuing the command:

```shell
mvn clean install
```

Afterwards, change to the folder ```sapl-policy-engine/sapl-server-lt``` and run the application:

```shell
mvn spring-boot:run
```

### Configuration of locally running server

By default, the server will use a self-signed certificate and expose the PDP API under https://localhost:8443/api/pdp
To override this certificate, use the matching Spring Boot settings, e.g.:
```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store-type=PKCS12
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=localhostpassword
server.ssl.key-alias=tomcat
```

API access requires "Basic Auth". Only one set of client credentials is implemented. 
The default client key (username) is: `YJidgyT2mfdkbmL` and the default client secret (password) is : `Fa4zvYQdiwHZVXh`.
To override these settings, use the following properties:
```properties
io.sapl.server-lt.key=YJidgyT2mfdkbmL
io.sapl.server-lt.secret=$2a$10$PhobF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9dkKTbFR4kkWABrbiyO
```
Please note, that the secret has to be BCrypt encoded. For testing, use something like: https://bcrypt-generator.com/

The server is implemented using Spring Boot. Thus, there are a number of ways to configure the 
application. 
Please consult the matching chapter of the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config).

## Testing the server

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

The server application is available as container image. Here, the server is not configured with any TLS 
security or authentication. It is expected that in deployment this responsibility is delegated to the 
infrastructure, e.g., a matching Kubernetes Ingress.

### Running directly as a Docker container

In order to run the server locally for testing in an environment like Docker Desktop, you can run the current image as follows:

```shell
docker run -d --name sapl-server-lt -p 8080:8080 --mount source=sapl-server-lt,target=/pdp/data nexus.openconjurer.org:30300/sapl-server-lt:2.0.0-SNAPSHOT
```

Alternatively the container can be run without Docker Volume which gives you easier access to the folder although Docker Desktop may warn you that this may not be as performative (Of course you can change the path):

```shell
docker run -d --name sapl-server-lt -p 8080:8080 -v c:\sapl\policies:/pdp/data nexus.openconjurer.org:30300/sapl-server-lt:2.0.0-SNAPSHOT
```

Afterwards you can check if the service is online under: http://localhost:8080/actuator/health.

Also, a volume is created for persisting the PDP configuration and policies.

Depending on your host OS and virtualization environment, these volumes may be located at:

* Docker Desktop on Windows WSL2: `\\wsl$\docker-desktop-data\version-pack-data\community\docker\volumes\sapl-server-lt\_data`
* Docker Desktop on Windows Hyper-V: `C:\Users\Public\Documents\Hyper-V\Virtual hard disks\sapl-server-lt\_data`
* Docker on Ubuntu: `/var/lib/docker/volumes/sapl-server-lt/_data`
* Docker Desktop on Windows with shared folder: `c:\sapl\policies` (or as changed)

### Running on Kubernetes

This section will describe the deployment on a baremetal Kubernetes installation which has Port 80 and 443 exposed to the Internet 
as well as Desktop Docker on Windows and will use the Kubernetes nginx-ingress-controller as well as cert-manager to manage the Let's Encrypt certificates (Only if Ports are exposed to the Internet so Let's Encrypt can access the URL)

#### Prerequisites

Installed Kubernetes v1.18+ 
Install NGINX Ingress Controller according to https://kubernetes.github.io/ingress-nginx/deploy/
```shell
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx
```
Install Cert-Manager according to https://cert-manager.io/docs/installation/kubernetes/ (Only for Use with exposed Ports and matching DNS Entries)
```shell
kubectl create namespace cert-manager
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.2.0-alpha.0/cert-manager.crds.yaml
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install   cert-manager jetstack/cert-manager   --namespace cert-manager   --version v1.1.0
```
Change the Email address in the Clusterissuer.yaml (Line email: user@email.com)
```shell
wget https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-lt/kubernetes/clusterissuer.yml
kubectl apply -f clusterissuer.yml -n your-namespace
```

#### Baremetal Kubernetes

This section assumes that the Kubernetes is installed on a Linux OS i.e. Ubuntu

First apply the Persistent Volume yaml 
```shell
kubectl create namespace sapl-server-lt
kubectl apply -f https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-lt/kubernetes/clusterissuer.yml  -n sapl-server-lt
```

Then download the Baremetal yaml file 

```shell
wget https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-lt/kubernetes/sapl-server-lt-baremetal.yml
```

change the URL in the Ingress section 

```
  tls:
    - hosts:
        - sapl.exampleurl.com
      secretName: sapl.lt.local-tls
  rules:
    - host: sapl.exampleurl.com
```

then apply the yaml file

```shell
kubectl apply -f sapl-server-lt-baremetal.yml -n sapl-server-lt
```

Create the Secret with htpasswd, you will be asked to enter the password

```shell
htpasswd -c auth Username
kubectl create secret generic basic-auth --from-file=auth -n sapl-server-lt
```

The Service should be reachable under the URL defined in the Ingress section of the sapl-server-lt-baremetal.yml https://sapl.exampleurl.com/actuator/health.

#### Docker Desktop Kubernetes

We are still working on the persistent volume solution for the Docker Desktop Kubernetes installation with WSL2 on Windows. 

Apply the sapl.server-lt.yml file 

```shell
kubectl create namespace sapl-server-lt
kubectl apply -f https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-lt/kubernetes/sapl-server-lt.yml -n sapl-server-lt
```

The URL is sapl.lt.local and has to be added to the hosts file (which is located in %windir%\system32\drivers\etc) add the Line 

```
127.0.0.1       sapl.lt.local
```

Create the Secret with htpasswd, you will be asked to enter the password

```shell
htpasswd -c auth Username
kubectl create secret generic basic-auth --from-file=auth -n sapl-server-lt
```

In the meantime the files can are volatile but can be accessed with

```shell
kubectl exec sapl-server-lt-d5d65dd6b-fz29g --stdin --tty -- /bin/sh -n sapl-server-lt
```

You have to use the actual podname which can be listed with the command:

```shell
kubectl get pods -n sapl-server-lt
```

#### Kubernetes Troubleshooting

The service is defined as ClusterIP but can be changed to use NodePort for testing purposes (Line   type: ClusterIP to type: NodePort)

```shell
kubectl edit service sapl-server-lt -n sapl-server-lt
```
 
If the Website can't be reached try installing the NGINX Ingress Controller using helm with the flag --set controller.hostNetwork=true,controller.kind=DaemonSet

