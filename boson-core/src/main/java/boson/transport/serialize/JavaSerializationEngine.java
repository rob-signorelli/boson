package boson.transport.serialize;

import boson.Utils;

import java.io.*;

/**
 * Describes a component that is capable of taking some Serializable object and turning into the raw bytes that we'll
 * send through the transport.
 */
public class JavaSerializationEngine implements SerializationEngine
{
    /**
     * Convert the given object into serialized bytes
     * @param obj The POJO to convert into raw bytes
     * @return The resulting bytes
     */
    @Override
    public byte[] objectToBytes(Serializable obj)
    {
        if (obj != null)
        {
            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(8192);
                 ObjectOutputStream outputStream = new ObjectOutputStream(byteStream))
            {
                outputStream.writeObject(obj);
                return byteStream.toByteArray();
            }
            catch (Throwable t)
            {
                throw new SerializationException(t);
            }
        }
        return new byte[0];
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
        if (bytes != null && bytes.length > 0)
        {
            try (ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
                 ObjectInputStream in = new ObjectInputStream(byteStream))
            {
                return (T)in.readObject();
            }
            catch (Throwable t)
            {
                throw new SerializationException(t);
            }
        }
        return null;
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
            try (ObjectInputStream in = new ObjectInputStream(bytes))
            {
                return (T)in.readObject();
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
