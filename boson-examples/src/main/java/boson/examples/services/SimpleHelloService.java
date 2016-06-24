package boson.examples.services;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The standard implementation of the HelloService for our example.
 */
public class SimpleHelloService implements HelloService
{
    /**
     * Given a string like "Hello X", return the text "Goodbye X"
     * @param phrase The incoming hello phrase to parse and respond to
     * @return The "hello" phrase where "Hello" is replaced w/ "Goodbye"
     */
    @Override
    public CompletableFuture<String> say(String phrase)
    {
        if (phrase == null)
            return CompletableFuture.completedFuture(null);

        return CompletableFuture.completedFuture(Stream.of(phrase.split(" "))
            .map(tok -> tok.equals("Hello") ? "Goodbye" : tok)
            .map(tok -> tok.equals("hello") ? "goodbye" : tok)
            .collect(Collectors.joining(" ")));
    }
}
