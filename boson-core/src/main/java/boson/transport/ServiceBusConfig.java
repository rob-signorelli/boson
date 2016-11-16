package boson.transport;

import boson.Utils;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generic configuration used to provide connection, threading, and timing information for a service bus transport. Things
 * like URI can mean different things to different transports. To a local transport it might mean absolutely nothing
 * whereas to an HTTP transport it could supply the DNS/port info for the target service.
 *
 * These options are general enough to be used to build most types of service bus transports - it's all in how you
 * translate the given values. Ideally you should be able to pass a config with the same values to both the producer
 * and the consumer and they'll link up properly - again it's all in how the implementations translate the values.
 */
public class ServiceBusConfig
{
    private URI uri;
    private String username;
    private String password;
    private Duration requestTimeToLive;
    private ExecutorService threadPool;
    private String keystorePath;
    private String keystorePassword;
    private boolean selfSignedCertificate;

    public ServiceBusConfig()
    {
        setUri(URI.create("http://localhost:5454"));
        setRequestTimeToLive(Duration.ofMinutes(5));
        setThreadPool(Executors.newCachedThreadPool());
    }

    /**
     * @return The URL for the host that the consumer is going to run on
     */
    public URI getUri() { return uri; }

    /**
     * Sets the URI for the HTTP endpoint where the consumer is listening. You can use IP addresses or DNS names as you see fit.
     * @param uri A URI such as "localhost" or "http://foo.bar.baz" or "foo://blah"
     */
    public void setUri(URI uri) { this.uri = uri; }

    /**
     * How much time does a request have before timing out and we close the underlying connection?
     * @return The request connection's time to live
     */
    public Duration getRequestTimeToLive() { return requestTimeToLive; }

    /**
     * Given that we have no idea how long a request might take, both sides of the service bus agree that after this much
     * time the producer will stop waiting for a response and the consumer will close the underlying HTTP connection. This
     * should result in an appropriate "I didn't get a response" error on the producer side.
     * @param requestTimeToLive The request's time limit
     */
    public void setRequestTimeToLive(Duration requestTimeToLive) { this.requestTimeToLive = requestTimeToLive; }

    /**
     * @return The username for any credentials required to access some communication channel.
     */
    public String getUsername() { return username; }

    /**
     * Some service bus communication channels require authentication for usage. For instance, a message queue broker
     * may require authentication to post/pull messages, so this is the username half of the credentials. While not all
     * transports will use basic username/password authentication, you can utilize this field as needed for those cases.
     * @param username The username value to use during authentication.
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * @return The password for any credentials required to access some communication channel.
     */
    public String getPassword() { return password; }

    /**
     * Some service bus communication channels require authentication for usage. For instance, a message queue broker
     * may require authentication to post/pull messages, so this is the password half of the credentials. While not all
     * transports will use basic username/password authentication, you can utilize this field as needed for those cases.
     * @param password The password value to use during authentication.
     */
    public void setPassword(String password) { this.password = password; }

    /**
     * This is true when either the 'username' or 'password' property has a value applied.
     * @return Are any auth credentials set on this configuration
     */
    public boolean isAuthenticationPresent()
    {
        return Utils.hasValue(getUsername()) || Utils.hasValue(getPassword());
    }

    /**
     * This is the thread pool that we'll use to dispatch request threads as needed.
     * @return The thread pool for dispatching request/response handling workers
     */
    public ExecutorService getThreadPool() { return threadPool; }

    /**
     * Not all service bus implementations require this, but for those that do - this thread pool is used to fork off
     * work that can dispatch requests or response handlers. It defaults to the <code>Executors.newCachedThreadPool()</code>.
     * @param threadPool The thread pool to utilize
     */
    public void setThreadPool(ExecutorService threadPool) { this.threadPool = threadPool; }

    /**
     * @return When performing secure communication this is the keystore to use for encryption/decryption
     */
    public String getKeystorePath() { return keystorePath; }

    /**
     * @return The password used to unlock the keystore for secure communication
     */
    public String getKeystorePassword() { return keystorePassword; }

    /**
     * Sets the keystore password to use to access the keystore specified by 'keystorePath'
     * @param keystorePassword The password to apply
     */
    public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }

    /**
     * Sets the keystore used for secure communication with the transport (e.g. HTTPS keys)
     * @param keystorePath The path to the keystore
     */
    public void setKeystorePath(String keystorePath) { this.keystorePath = keystorePath; }

    /**
     * @return Will the service dispatcher/consumer allow authentication from self-signed certificates? (default=false)
     */
    public boolean isSelfSignedCertificate() { return selfSignedCertificate; }

    /**
     * When using a 'keystore' for transport encryption, will we allow you to use a self-signed certificate or must it
     * be verified by a third-party certificate authority (CA)?
     * @param flag Allow self-signing or not
     */
    public void setSelfSignedCertificate(boolean flag) { this.selfSignedCertificate = flag; }

    /**
     * Chaining support. Sets the URI the we'll use to connect to the service bus
     * @param uri The transport protocol encoded URI
     * @return this
     */
    public ServiceBusConfig uri(URI uri)
    {
        setUri(uri);
        return this;
    }

    /**
     * Chaining support. Sets the URI the we'll use to connect to the service bus
     * @param uri The transport protocol encoded URI
     * @return this
     */
    public ServiceBusConfig uri(String uri)
    {
        setUri(URI.create(uri));
        return this;
    }

    /**
     * Chaining support. Defines how long a request has to be completely serviced before both sides give up on it.
     * @param duration The time to live for any request to this service/bus
     * @return this
     */
    public ServiceBusConfig requestTTL(Duration duration)
    {
        setRequestTimeToLive(duration);
        return this;
    }

    /**
     * Chaining support. Supplies the username field for any authentication required to access the communication channel.
     * @param username The username to apply
     * @return this
     */
    public ServiceBusConfig username(String username)
    {
        setUsername(username);
        return this;
    }

    /**
     * Chaining support. Supplies the password field for any authentication required to access the communication channel.
     * @param password The password to apply
     * @return this
     */
    public ServiceBusConfig password(String password)
    {
        setPassword(password);
        return this;
    }

    /**
     * Chaining support. Supplies the thread pool that we'll use for forking off request/response handling code.
     * @param threadPool The executor to use
     * @return this
     */
    public ServiceBusConfig threadPool(ExecutorService threadPool)
    {
        setThreadPool(threadPool);
        return this;
    }

    /**
     * Chaining support. Specifies the keystore to use in secure communication through the transport
     * @param path The path to the keystore
     * @return this
     */
    public ServiceBusConfig keystore(String path)
    {
        return keystore(path, "");
    }

    /**
     * Chaining support. Specifies the keystore to use in secure communication through the transport
     * @param path The path to the keystore
     * @param password The password to unlock the keystore
     * @return this
     */
    public ServiceBusConfig keystore(String path, String password)
    {
        setKeystorePath(path);
        setKeystorePassword(password);
        return this;
    }

    /**
     * Chaining support. Allows a dispatcher/consumer to accept self-signed certificates from the remote endpoint. You
     * should only do this when you intend to lock all of your endpoints behind some protected, private network.
     * @return this
     */
    public ServiceBusConfig canSelfSign()
    {
        setSelfSignedCertificate(true);
        return this;
    }
}
