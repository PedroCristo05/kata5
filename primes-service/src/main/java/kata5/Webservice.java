package kata5;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class Webservice {
	private final String id;
	private final String host;
	private final int port;
	private final String consul;
	private final Gson gson = new Gson();

	public Webservice(String id, String host, int port, String consul) {
		this.id = id;
		this.host = host;
		this.port = port;
		this.consul = consul;
	}

	public void start() throws IOException, InterruptedException {
		Javalin app = Javalin.create(this::init).start(host, port);
		try {
			register();
			System.out.println("Registered : " + id + " as " + host() + ":" + port);
		} catch (IOException | InterruptedException | RuntimeException failure) {
			app.stop();
			throw failure;
		}
	}

	private void init(JavalinConfig config) {
		config.routes.get("/", this::handlePrimes);
		config.routes.get("/health", this::health);
	}

	private void health(Context context) {
		context.result("OK");
	}

	private void handlePrimes(Context context) {
		context.header("X-Node", id);
		context.contentType("application/json");

		String rawParam = context.queryParam("n");
		if (rawParam == null || rawParam.isBlank()) {
			context.status(400).result(gson.toJson(Map.of("error", "Falta el parámetro obligatorio 'n'")));
			return;
		}

		try {
			int terms = Integer.parseInt(rawParam);
			if (terms < 1 || terms > 100) {
				context.status(400).result(gson.toJson(Map.of("error", "El parámetro 'n' debe estar entre 1 y 100")));
				return;
			}
			int[] primes = calculatePrimes(terms);
			context.status(200).result(gson.toJson(primes));
		} catch (NumberFormatException e) {
			context.status(400).result(gson.toJson(Map.of("error", "El parámetro 'n' debe ser un número entero válido")));
		}
	}

	public static int[] calculatePrimes(int n) {
		int[] result = new int[n];
		int count = 0;
		int candidate = 2;
		while (count < n) {
			if (isPrime(candidate)) {
				result[count++] = candidate;
			}
			candidate++;
		}
		return result;
	}

	private static boolean isPrime(int number) {
		if (number < 2) return false;
		for (int i = 2; i * i <= number; i++) {
			if (number % i == 0) return false;
		}
		return true;
	}

	private void register() throws IOException, InterruptedException {
		try (HttpClient client = client()) {
			HttpResponse<String> response = client.send(requestWith(json()), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				throw new IOException("Consul rechazó el registro (HTTP " + response.statusCode() + "): " + response.body());
			}
		}
	}

	private static HttpClient client() {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	private HttpRequest requestWith(String json) {
		return HttpRequest
				.newBuilder(URI.create(consul).resolve("/v1/agent/service/register"))
				.timeout(Duration.ofSeconds(5))
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json))
				.build();
	}

	private String host() {
		return isLocalHost() ? "host.docker.internal" : host;
	}

	private boolean isLocalHost() {
		return "localhost".equals(host) ||
				"127.0.0.1".equals(host) ||
				"0.0.0.0".equals(host);
	}

	private String healthUrl() {
		return "http://" + host() + ":" + port + "/health";
	}

	private String json() {
		return gson.toJson(Map.of(
				"ID", id,
				"Name", "primes_service_v1",
				"Address", host(),
				"Port", port,
				"Check", Map.of(
						"HTTP", healthUrl(),
						"Interval", "5s",
						"Timeout", "2s",
						"DeregisterCriticalServiceAfter", "10m"
				)
		));
	}
}