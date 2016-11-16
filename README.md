What is Boson?
---------------

Boson is a Java-based service bus framework that enables you to easily build client/server applications, service-oriented-architectures (SOA), or microservices. With just a few lines of setup code you can turn any POJO into a service that can be consumed remotely over HTTP, shared message queues, or locally within the same VM. You just focus on making your services do cool stuff and Boson takes care of the minutia associated with serialization, communication, network resource management, and other not-business-logic stuff required to make remote processes interact with one another.

Getting Started
---------------
This is what your code might look like if you were going to run everything in one local VM.

```java
// HelloService.java
public interface HelloService {
    CompletableFuture<String> say(String phrase);
}

// SimpleHelloService.java
public class SimpleHelloService implements HelloService {
    public CompletableFuture<String> say(String phrase) {
        // ... do something cool ...
    }
}

// LocalClientAndServer.java
public class LocalClientAndServer {
    public static void main(String[] args) {
        // "Connect" to the service
        HelloService service = new SimpleHelloService();

        // Use the service in some meaningful way
        service.say("Hello World").thenAccept(System.out::println).get();
        service.say("hello world").thenAccept(System.out::println).get();
    }
}

// Console output
$ java LocalClientAndServer
Goodbye World
goodbye World
```
Now let's say that you would like to run your main processing code on one server and have a completely different server listen for and process calls to the ```HelloService``` for you. Just make 2 classes and start each on a different server (or in different processes on the same server for this example).

```java
// HelloServer.java
public class HelloServer {
    public static void main(String[] args) throws Exception {
        // Boson: Register as a remote handler/implementer of the service
        Services serviceRepository = new Services();
        serviceRepository.implement(
            HelloService.class,
            new SimpleHelloService(),
            new HttpTransportBindings<>(),
            new ServiceBusConfig().url("http://localhost:12345"));

        // Listening for HelloService requests, press ENTER to exit
        System.console().readLine();
    }
}

// HelloClient.java
public class HelloClient {
    public static void main(String[] args) throws Exception {
        // Boson: Enable consumption of the remote service
        Services serviceRepository = new Services();
        HelloService service = serviceRepository.consume(
            HelloService.class,
            new HttpTransportBindings<>(),
            new ServiceBusConfig().url("http://localhost:12345")).get();

        // EXACT SAME application code as before
        service.say("Hello World").thenAccept(System.out::println).get();
        service.say("hello world").thenAccept(System.out::println).get();
    }
}

// Console output
$ java -jar [boson-jars] HelloServer &
$ java -jar [boson-jars] HelloClient
Goodbye World
goodbye World
```

Why Boson?
---------------
This is not the first library to facilitate service communication and it won't be the last. Inspired by some of the minimalism offered by other libraries such as [Spark Framework](http://sparkjava.com), these are the motivations and principles that drive Boson's development.

 - **Minimal Setup**. You can copy/paste 2 lines of code into your existing application to get Boson up and running. If you've used Spring or JAX-XXX related APIs in the past you're probably used to adding annotations to nearly every single class, field, and method in your code. The beauty of Boson is that other than a few setup lines in ```main()``` you'd never know that your application was using it. This makes it easy to implement and easy to swap out should you want something more complex.
 - **Asynchronous Java 8**. This was a huge leap forward for the Java language, so we're not going to water down the API trying to support older VMs. We want lambdas, streams, and completable futures so that you can write beautiful, functional code. Remote services inherently have to deal with blocking I/O, so Boson makes it easy for you to plug in the thread pool or actor framework of your choice to best utilize your resources and maximize throughput.
 - **Remote-Procedure-Call (RPC) Style Services**. All service calls feel like local, native calls where you pass real Java objects to methods and get real Java objects back - despite all of the hidden complexity that handles remote communication. While you do still need to be mindful about what is remote and what's not to avoid latency, it's keeps the simple programming paradigm of "I call a method and get a result".
 - **No Config Files**. Everything is configured via code. That means your IDE's autocomplete shows you everything you need. You don't have to learn some archaic XML schema to get up and running. You're more than welcome to bury Boson configuration values in your app's own configuration and feed them to Boson at runtime.
 - **Minimal Dependencies**. Other than [Simple Logging Facade for Java](http://www.slf4j.org/), the Boson jars are all you need. You only include Boson jars for the transports you require and you can still bring your own logger. We promise never to bloat your project with 8 additional Apache Commons libraries so that we can avoid doing 1 mildly annoying task.

FAQs
---------------

###Does the HTTP Transport Support HTTPS?
Absolutely, it does! When building the configuration for your service consumer or implementation, just change the URI to start w/ "https://" instead of "http://". The service implementation requires one additional configuration option; the keystore that we'll use to sign each request.

Here is the one change required when registering a remote implementation via ```Services.implement()```
```java
// Here's how you set up a plain-text HTTP implementation:
new ServiceBusConfig()
    .uri("http://localhost:5454");

// To enable HTTPS instead...
new ServiceBusConfig()
    .uri("https://localhost:5454")
    .keystore("/path/to/keystore", "keystore-password");
```

On the service consumer side, just pass this configuration to ```Services.consume()```:

```java
// To consume the service over plain-text HTTP
new ServiceBusConfig()
    .uri("http://localhost:5454");

// It's literally a 1 character change to consume the service over HTTPS
new ServiceBusConfig()
    .uri("https://localhost:5454");
```

###Can I Use a Self-Signed Certificate With My HTTPS Transport?
If that's your prerogative, that is supported, but not by default. To lock things down as much as possible, we disable that functionality with default configuration. You can tell Boson to allow self signed certificates when registering your service consumer by adding ```canSelfSign()``` to the configuration.
 
```java
HelloService service = Futures.await(services.consume(
    HelloService.class,
    new HttpTransportBindings<>(),
    new ServiceBusConfig().uri("https://localhost:5454").canSelfSign()));
```

That last ```.canSelfSign()``` forces Boson to use a custom ```SSLSocketFactory``` which allows your self signed certs. This is NOT a VM-wide setting as it only applies to Boson transport connections. Any other HTTPS requests your application might make (to consume some remote API or something) is bound by the default SSL rules or whatever you set up. Boson works independently so you can open up self-signing for Boson but not your entire application.

###HTTP Transport: Can I Set Jetty's Logging Level?

When developing your services you'd like to be able to run your code w/ DEBUG or TRACE level logging. The problem is that Jetty's internal "debug" logging is so ungodly chatty that it's impossible to follow your own code. If you're using the simple SLF4J binding, you can add these arguments to your command to give your app TRACE logging while Jetty stays quiet with only INFO:

```bash
$ java -cp [boson/slf4j jars] \
       -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
       -Dorg.slf4j.simpleLogger.log.org.eclipse.jetty=info \
       boson.examples.helloworld.http.HelloHttpServices
```
If you're not using the simple logger (which you're probably not), obviously you'll need to follow the instructions for Log4J or whatever you're using. This just helps you run the examples with a little less noise.