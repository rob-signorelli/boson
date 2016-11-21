package boson.services;

/**
 * The reference implementation of security context providers which stores your context information on the current
 * thread. Note that your application should properly wipe out the context at the disposal of your thread (or before
 * it goes back into its pool).
 */
public class ThreadLocalServiceContextProvider<T> implements ServiceContextProvider<T>
{
    // Since this is theoretically a singleton in your app, non-static is OK so we use 'T' here
    private final ThreadLocal<T> CONTEXTS = new ThreadLocal<>();

    /**
     * @return The current context for the request or series of service calls
     */
    @Override
    public T getContext()
    {
        return CONTEXTS.get();
    }

    /**
     * Applies the context to automatically pass along to remote service calls
     * @param context The context to carry along
     */
    @Override
    public void setContext(T context)
    {
        CONTEXTS.set(context);
    }
}
