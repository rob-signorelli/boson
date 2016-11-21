package boson.services;

/**
 * An adapter for attaching a service context to a call or series of boson-activated calls. Different applications require
 * different implementations to store a context for the duration of its usage. For instance, in a standard servlet app
 * where threads are maintained for the duration of the request, you can use the reference ThreadLocalServiceContextProvider
 * implementation to store your context for the life of the request. In a Play Framework application you can implement
 * your own provider that interacts with their <code>Http.Context</code> structure to carry your context even as
 * Play/Akka bounces the request from thread to thread.
 *
 * An implementation likely matches the threading/context model of the framework you're using to build your application.
 */
public interface ServiceContextProvider<T>
{
    /**
     * @return The current context for the request or series of service calls
     */
    T getContext();

    /**
     * Applies the context to automatically pass along to remote service calls
     * @param context The context to carry along
     */
    void setContext(T context);
}
