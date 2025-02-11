package com.jebeaudet.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void testTimedAnnotations() {
		ResponseEntity<String> responseOne = restTemplate.getForEntity("http://localhost:" + port + "/one", String.class);
		assertThat(responseOne.getStatusCode().is2xxSuccessful()).isTrue();
		ResponseEntity<String> responseTwo = restTemplate.getForEntity("http://localhost:" + port + "/two", String.class);
		assertThat(responseTwo.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<MetricActuatorResponse> metrics = restTemplate.getForEntity("http://localhost:" + port + "/actuator/metrics", MetricActuatorResponse.class);
		assertThat(responseTwo.getStatusCode().is2xxSuccessful()).isTrue();

		assertThat(metrics.getBody().names()).containsAll(List.of("test.one.timed", "test.two.timed.first", "test.two.timed.second"));
	}

	public record MetricActuatorResponse(List<String> names) {
	}

}
