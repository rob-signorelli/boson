package boson.transport.serialize;

import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import org.nustaq.serialization.FSTConfiguration;

import java.io.InputStream;
import java.io.Serializable;

/**
 * Describes a component that is capable of taking some Serializable object and turning into the raw bytes that we'll
 * send through the transport.
 */
public class FSTSerializationEngine implements SerializationEngine
{
    private static FSTConfiguration FST = FSTConfiguration.createDefaultConfiguration();
    static
    {
        // Optimization described in https://github.com/RuedigerMoeller/fast-serialization/wiki/Serialization to avoid
        // serializing the fully-qualified class name for our most-frequently serialized objects. As these two classes
        // literally encapsulate EVERYTHING we serialize, this cuts down on the size/time of every serialization.
        FST.registerClass(ServiceRequest.class, ServiceResponse.class);
    }

    /**
     * Convert the given object into serialized bytes
     * @param obj The POJO to convert into raw bytes
     * @return The resulting bytes
     */
    @Override
    public byte[] objectToBytes(Serializable obj)
    {
        try
        {
            return (obj != null)
                ? FST.asByteArray(obj)
                : new byte[0];
        }
        catch (Throwable t)
        {
            throw new SerializationException(t);
        }
    }

    /**
     * Reverses the serialization process, taking the serialized bytes and turning it back into the original POJO
     * @param type The type of the resulting deserialized object
     * @param bytes The serialized bytes
     * @return The original POJO
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T bytesToObject(Class<T> type, byte[] bytes)
    {
        try
        {
            return (bytes != null && bytes.length > 0)
                ? (T) FST.asObject(bytes)
                : null;
        }
        catch (Throwable t)
        {
            throw new SerializationException(t);
        }
    }

    /**
     * Reverses the serialization process, taking the serialized bytes and turning it back into the original POJO
     * @param type The type of the resulting deserialized object
     * @param bytes The serialized bytes
     * @return The original POJO
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T streamToObject(Class<T> type, InputStream bytes)
    {
        if (bytes != null)
        {
            try
            {
                // DO NOT close the FSTObjectInput object. We can close the byte input stream, but FST re-uses its
                // object input stream instances in a thread-safe way so just do as the docs say and leave it open.
                return (T)FST.getObjectInput(bytes).readObject(type);
            }
            catch (Throwable t)
            {
                throw new SerializationException(t);
            }
            finally
            {
                Utils.close(bytes);
            }
        }
        return null;
    }
}
