package boson;

import boson.services.ServiceContextProvider;
import boson.services.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * General purpose helpers that we'll use all over the place to build our service bus implementations.
 */
public class Utils
{
    /**
     * Returns a logger for the given class context where all log lines will have the tag "Boson.MyClassName" assuming
     * that the "simple" name for the class in this case was "MyClassName"
     * @param context The class whose simple name should be used in the log line tags
     * @return The appropriate logger
     */
    public static Logger logger(Class<?> context)
    {
        return LoggerFactory.getLogger("Boson." + context.getSimpleName());
    }

    /**
     * A simple sleep that quietly gobbles up the interrupted exception that might be thrown.
     * @param millis The number of millis to sleep for
     */
    public static void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            // Gobble this up.
        }
    }

    /**
     * A null-safe way to interrupt the given thread
     * @param thread The thread to interrupt
     * @return A future that completes after the interrupt attempt (will complete even if 'thread' is null)
     */
    public static CompletableFuture<Void> interrupt(Thread thread)
    {
        if (thread != null)
            thread.interrupt();

        return Futures.of(null);
    }

    /**
     * For the first version of Boson, we are going to insist that every service method return a CompletableFuture so
     * that we can process things in a nicely asynchronous way. In a moment of future weakness I may decide to allow
     * you to return the value synchronously and we can hack around that to make it behave implicitly like an
     * async/await style call.
     * @param serviceType The interface that defines the common service
     * @param method The actual method definition (whose return type we'll check)
     * @throws IllegalStateException If the method's return type is not a CompletableFuture
     */
    public static void assertAsync(Class<?> serviceType, Method method)
    {
        if (method.getReturnType() != CompletableFuture.class)
        {
            String type = serviceType.getClass().getSimpleName();
            throw illegalState("[Boson] Can't invoke %s.%s because it does not return CompletableFuture", type, method.getName());
        }
    }

    /**
     * A shorthand for constructing a nice message and using in a new (yet not-thrown) IllegalArgumentException.
     * @param message The message template
     * @param args The runtime replacements for any "%s" instances in the message.
     */
    public static IllegalArgumentException illegalArg(String message, Object... args)
    {
        return new IllegalArgumentException(String.format(message, args));
    }

    /**
     * A shorthand for constructing a nice message and using in a new (yet not-thrown) IllegalStateException.
     * @param message The message template
     * @param args The runtime replacements for any "%s" instances in the message.
     */
    public static IllegalStateException illegalState(String message, Object... args)
    {
        return new IllegalStateException(String.format(message, args));
    }

    /**
     * Performs standard Java serialization to convert the given object into its serialized byte form.
     * @param pojo The original object you want to serialize
     * @return The bytes for this object (null object results in zero-length array, not null)
     */
    public static byte[] serialize(Object pojo) throws IOException
    {
        if (pojo != null)
        {
            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(8192);
                 ObjectOutputStream outputStream = new ObjectOutputStream(byteStream))
            {
                outputStream.writeObject(pojo);
                return byteStream.toByteArray();
            }
        }
        return new byte[0];
    }

    /**
     * Reconstitutes an instance of T from the serialized bytes provided. This is a dumb implementation that fully trusts
     * that the bytes (A) represent a serialized object and (B) that when put back together is really an instance of T,
     * so it's possible to get a ClassCastException if you're being stupid/careless.
     * @param serializedBytes The raw bytes of the serialized instance (output of the <code>Utils.serialize()</code> method)
     * @return The original POJO, now reconstituted from the bytes
     * @throws ClassNotFoundException If the deserialized object is not in this class loader's classpath.
     * @throws IOException If anything else goes wrong during the deserialization process
     * @throws ClassCastException If you were dumb and the resulting object wasn't actually an instance of type T.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] serializedBytes) throws ClassNotFoundException, IOException
    {
        if (serializedBytes != null && serializedBytes.length > 0)
        {
            try (ByteArrayInputStream bytes = new ByteArrayInputStream(serializedBytes);
                 ObjectInputStream in = new ObjectInputStream(bytes))
            {
                return (T)in.readObject();
            }
        }
        return null;
    }

    /**
     * Reconstitutes an instance of T from the serialized bytes provided. This is a dumb implementation that fully trusts
     * that the bytes (A) represent a serialized object and (B) that when put back together is really an instance of T,
     * so it's possible to get a ClassCastException if you're being stupid/careless.
     * @param serializedBytes The raw bytes of the serialized instance (output of the <code>Utils.serialize()</code> method)
     * @return The original POJO, now reconstituted from the bytes
     * @throws ClassNotFoundException If the deserialized object is not in this class loader's classpath.
     * @throws IOException If anything else goes wrong during the deserialization process
     * @throws ClassCastException If you were dumb and the resulting object wasn't actually an instance of type T.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(InputStream serializedBytes) throws ClassNotFoundException, IOException
    {
        if (serializedBytes != null)
        {
            try (ObjectInputStream in = new ObjectInputStream(serializedBytes))
            {
                return (T)in.readObject();
            }
            finally
            {
                close(serializedBytes);
            }
        }
        return null;
    }

    /**
     * Quietly close the given resource. It doesn't matter if the resource is null, already closed, or just barfs in
     * the process - this will give it the old college try.
     * @param closeable The resource you want to close/dispose.
     */
    public static void close(Closeable closeable)
    {
        try
        {
            if (closeable != null)
                closeable.close();
        }
        catch (Throwable t)
        {
            /* Don't bother me with this shit... */
        }
    }

    /**
     * Executes a unit of work in a try/catch that gobbles up any errors. I should not have to explain how dangerous
     * and potentially stupid this is. So why does it exist if it's usually such a bad idea? Sometimes you have a SMALL
     * block of code that is supposed to be completely rock solid but just in the complete off chance something bad happens
     * you don't want to kill a thread with an uncaught exception, you can use this instead of cluttering your code w/
     * a try/catch.
     * @param work The work to execute
     */
    public static void runQuietly(Runnable work)
    {
        try
        {
            work.run();
        }
        catch (Throwable t)
        {
            /* The point is for this to be quiet... so be quiet. */
        }
    }

    /**
     * Determines if the given text has any "real" value. Real values are non-null and contain at least 1 NON-WHITESPACE
     * character. As such, a string of empty spaces is not considered to have a "real" value.
     * @param text The text to test
     * @return Does 'text' have a real, usable value?
     */
    public static boolean hasValue(String text)
    {
        return (text != null) && text.trim().length() > 0;
    }

    /**
     * Null-safe helper that returns the current context. This will return 'null' if there is no context provider for
     * the given service repository.
     * @param services The service repository where we'll pull the context provider from
     * @param <T> The type describing the context
     * @return The current security/authorization context (may be null)
     */
    public static <T> T getContext(Services services)
    {
        ServiceContextProvider<T> provider = services.getContextProvider();
        return (provider == null) ? null : provider.getContext();
    }

    /**
     * Null-safe helper that updates the current context within the given service repository. If you've set the context
     * provider to null, this is effectively a noop.
     * @param services The service repository whose context provider we'll use to apply the given context
     * @param <T> The type describing the context
     */
    public static <T> void setContext(Services services, T context)
    {
        ServiceContextProvider<T> provider = services.getContextProvider();
        if (provider != null)
            provider.setContext(context);
    }
}
