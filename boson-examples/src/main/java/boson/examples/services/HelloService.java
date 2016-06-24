package boson.examples.services;

import java.util.concurrent.CompletableFuture;

/**
 * A simple service that responds to text such as "Hello World" or "Hello Rob" with the equivalent "goodbye" phrase
 * like "Goodbye World" or "Goodbye Rob".
 */
public interface HelloService
{
    /**
     * Given a string like "Hello X", return the text "Goodbye X"
     * @param phrase The incoming hello phrase to parse and respond to
     * @return The "hello" phrase where "Hello" is replaced w/ "Goodbye"
     */
    CompletableFuture<String> say(String phrase);
}
