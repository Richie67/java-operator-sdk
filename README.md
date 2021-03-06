# java-operator-sdk
[![Build Status](https://travis-ci.org/ContainerSolutions/java-operator-sdk.svg?branch=master)](https://travis-ci.org/ContainerSolutions/java-operator-sdk)

SDK for building Kubernetes Operators in Java. Inspired by [operator-sdk](https://github.com/operator-framework/operator-sdk).
In this first iteration we aim to provide a framework which handles the reconciliation loop by dispatching events to
a Controller written by the user of the framework.

The Controller only contains the logic to create, update and delete the actual resources related to the CRD.

## Roadmap

Feature we would like to implement and invite the community to help us implement in the future:

* ~~Spring Boot support~~
* Testing support
* Class generation from CRD to POJO

## Usage

We have several sample Operators under the samples directory:
* *basic*: Minimal Operator implementation which only parses the Custom Resource and prints to stdout.
Implemented with and without Spring Boot support. The two samples share the common module.
* *webserver*: More realistic example creating an nginx webserver from a Custom Resource containing html code.

Add dependency to your project:

```xml
<dependency>
  <groupId>com.github.containersolutions</groupId>
  <artifactId>operator-framework</artifactId>
  <version>{see https://search.maven.org/search?q=a:operator-framework for latest version}</version>
</dependency>
```

Main method initializing the Operator and registering a controller..

```java
public class Runner {

   public static void main(String[] args) {
       Operator operator = new Operator(new DefaultKubernetesClient());
       operator.registerController(new CustomServiceController());
   }
}
```

The Controller implements the business logic and describes all the classes needed to handle the CRD.

```java
@Controller(customResourceClass = WebServer.class,
        crdName = "webservers.sample.javaoperatorsdk",
        customResourceListClass = WebServerList.class,
        customResourceDonebaleClass = WebServerDoneable.class)
public class WebServerController implements ResourceController<WebServer> {

    @Override
    public boolean deleteResource(CustomService resource) {
        // ... your logic ...
        return true;
    }
    
    // Return the changed resource, so it gets updated. See javadoc for details.
    @Override
    public Optional<CustomService> createOrUpdateResource(CustomService resource) {
        // ... your logic ...
        return resource;
    }
}
```

Our custom resource java representation

```java
public class WebServer extends CustomResource {

    private WebServerSpec spec;

    private WebServerStatus status;

    public WebServerSpec getSpec() {
        return spec;
    }

    public void setSpec(WebServerSpec spec) {
        this.spec = spec;
    }

    public WebServerStatus getStatus() {
        return status;
    }

    public void setStatus(WebServerStatus status) {
        this.status = status;
    }
}

public class WebServerSpec {

    private String html;

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }
}
```

## Spring Boot Support

We provide a spring boot starter to automatically handle bean registration, and registering various components as beans. 
To use it just include the following dependency to you project: 

```
<dependency>
 <groupId>com.github.containersolutions</groupId>
 <artifactId>spring-boot-operator-framework-starter</artifactId>
 <version>[version]</version>
</dependency>
```

Note that controllers needs to be registered as beans in the Spring context. For example adding the `@Component` annotation
on the classes will work.
See Spring docs for for details, also our spring-boot with component scanning. 
All controllers that are registered as a bean, gets automatically registered to operator. 
 
Kubernetes client creation using properties is also supported, for complete list see: [Link for config class]  


## Implementation / Design details

This library relies on the amazing [kubernetes-client](https://github.com/fabric8io/kubernetes-client) from fabric8. 
Most of the heavy lifting is actually done by kubernetes-client.

What the framework adds on top of the bare client:
* Management of events from the Kubernetes API. All events are inserted into a queue by the EventScheduler. The 
framework makes sure only the latest event for a certain resource is processed. This is especially important since
on startup the operator can receive a whole series of obsolete events.
* Retrying of failed actions. When an event handler throws an exception the event is put back in the queue.
* A clean interface to the user of the framework to receive events related to the Controller's resource.

### Dealing with Consistency 

#### Run Single Instance

There should be always just one instance of an operator running at a time (think process). If there there would be 
two ore more, in general it could lead to concurrency issues. Note that we are also doing optimistic locking when we update a resource.
In this way the operator is not highly available. However for operators this not necessary an issue, 
if the operator just gets restarted after it went down. 

#### Operator Restarts

When an operator is started we got events for every resource (of a type we listen to) already on the cluster. Even if the resource is not changed 
(We use `kubectl get ... --watch` in the background). This can be a huge amount of resources depending on your use case.
So it could be a good case just have a status field on the resource which is checked, if there anything needs to be done.

#### At Least Once

To implement controller logic, we have to override two methods: `createOrUpdateResource` and `deleteResource`. 
These methods are called if a resource is create/changed or marked for deletion. In most cases these methods will be
called just once, but in some rare cases can happen that are called more then once. In practice this means that the 
implementation needs to be **idempotent**.    

#### Deleting a Resource

During deletion process we use [Kubernetes finalizers](https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#finalizers 
"Kubernetes docs") finalizers. This is required, since it can happen that the operator is not running while the delete 
of resource is executed (think `oc delete`). In this case we would not catch the delete event. So we automatically add a
finalizer first time we update the resource if its not there. 
