package boson.transport;

/**
 * Transport bindings define the components required to send requests/responses to/from Boson-activated services. It
 * contains the communication details to both "consume" some other remote service as well as the communication details
 * to "produce" results, giving them to some other consuming process.
 * @param <T> The type of service that the bindings are facilitating transport for
 */
public interface ServiceTransportBindings<T>
{
    /**
     * Defines the communication bindings required to make a request to the remote service. You can consider this the
     * "client" that consumes the service work. This is a factory method that will give a new instance each time so
     * do not call this multiple times assuming that you'll get the same instance!
     * @param serviceContract The service interface that exposes the available methods
     * @param config The configuration to apply to the dispatcher
     * @return A new instance of the dispatcher/caller transport bindings
     */
    ServiceBusDispatcher<T> dispatcher(Class<T> serviceContract, ServiceBusConfig config);

    /**
     * Defines the communication bindings required for a process to accept incoming service requests and respond back
     * to them. You can consider this the "server" and produces results for requested work. This is a factory method
     * that will give a new instance each time so do not call this multiple times assuming that you'll get the same instance!
     * @param serviceContract The service interface that exposes the available methods
     * @param config The configuration to apply to the receiver
     * @return A new instance of the receiver/callee transport bindings
     */
    ServiceBusReceiver<T> receiver(Class<T> serviceContract, ServiceBusConfig config);
}
