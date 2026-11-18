package kata5;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class Main {
    private final static String address = "http://localhost:8080";

    static void main() throws InterruptedException, IOException {
        try (HttpClient client = client()) {
            while (true) {
                int terms = ThreadLocalRandom.current().nextInt(1, 20);
                String endpoint = ThreadLocalRandom.current().nextBoolean()
                        ? "/api/v1/fibonacci/?n=" + terms
                        : "/api/v1/primes/?n=" + terms;

                HttpRequest request = request(endpoint);
                CompletableFuture<HttpResponse<String>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                future.thenAccept(response -> showResponse(endpoint, response));
                Thread.sleep(200);
            }
        }
    }

    private static void showResponse(String endpoint, HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            System.out.println("NGINX response " + response.statusCode() + " for " + endpoint);
        } else {
            String node = response.headers().firstValue("X-Node").orElse("unknown");
            System.out.printf("Endpoint: %-26s | Node: %-4s | Body: %s%n", endpoint, node, response.body());
        }
    }

    private static HttpRequest request(String endpoint) {
        return HttpRequest.newBuilder(URI.create(address).resolve(endpoint))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
}