package boson;

import org.junit.Test;

import java.io.NotSerializableException;

import static org.junit.Assert.*;

public class UtilsTest
{
    @Test
    public void sleep() throws Exception
    {
        long start = System.currentTimeMillis();
        Utils.sleep(250);
        long end = System.currentTimeMillis();
        assertTrue("Sleep woke up too early", end - start >= 250);
    }

    @Test
    public void illegalArg() throws Exception
    {
        IllegalArgumentException a = Utils.illegalArg("It broke");
        assertEquals("Incorrect formatted message", "It broke", a.getMessage());

        IllegalArgumentException b = Utils.illegalArg("It broke %s", "hard");
        assertEquals("Incorrect formatted message", "It broke hard", b.getMessage());

        IllegalArgumentException c = Utils.illegalArg("It broke %s because of %s", "hard", "bugs");
        assertEquals("Incorrect formatted message", "It broke hard because of bugs", c.getMessage());
    }

    @Test
    public void illegalState() throws Exception
    {
        IllegalStateException a = Utils.illegalState("It broke");
        assertEquals("Incorrect formatted message", "It broke", a.getMessage());

        IllegalStateException b = Utils.illegalState("It broke %s", "hard");
        assertEquals("Incorrect formatted message", "It broke hard", b.getMessage());

        IllegalStateException c = Utils.illegalState("It broke %s because of %s", "hard", "bugs");
        assertEquals("Incorrect formatted message", "It broke hard because of bugs", c.getMessage());
    }

    @Test
    public void serialize() throws Exception
    {
        String text = "hello world";
        byte[] textBytes = Utils.serialize(text);
        assertNotNull(textBytes);
        assertEquals("Serialized text bytes incorrect size", 18, textBytes.length);
    }

    @Test (expected = NotSerializableException.class)
    public void serializeNonserializable() throws Exception
    {
        Utils.serialize(this);
    }

    @Test
    public void deserialize() throws Exception
    {
        String original = "hello world";
        byte[] bytes = Utils.serialize(original);
        assertEquals("Improper deserialization", "hello world", Utils.deserialize(bytes));
    }

    @Test
    public void runQuietly() throws Exception
    {
        // As long as there's no error, we assume all went well.
        Utils.runQuietly("foo"::trim);
        Utils.runQuietly(() -> { throw new RuntimeException(); });
    }

    @Test
    public void hasValue() throws Exception
    {
        assertFalse("hasValue(null)", Utils.hasValue(null));
        assertFalse("hasValue('')", Utils.hasValue(""));
        assertFalse("hasValue('  ')", Utils.hasValue("  "));
        assertTrue("hasValue(' x ')", Utils.hasValue(" x "));
        assertTrue("hasValue('fooBar)", Utils.hasValue("fooBar"));
    }
}