package com.example.producer;

import com.example.container.RedpandaWithExtraListenersContainer;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ProducerApplicationTests {

	public static void main(String[] args) {
		SpringApplication.from(ProducerApplication::main)
				.with(ContainerConfiguration.class)
				.run(args);
	}

	@TestConfiguration
	static class ContainerConfiguration {

		private static final String REDPANDA_NETWORK = "redpanda-network";

		Network network = getNetwork();

		static Network getNetwork() {
			Network defaultDaprNetwork = new Network() {
				@Override
				public String getId() {
					return REDPANDA_NETWORK;
				}

				@Override
				public void close() {

				}

				@Override
				public Statement apply(Statement base, Description description) {
					return null;
				}
			};

			List<com.github.dockerjava.api.model.Network> networks = DockerClientFactory.instance().client().listNetworksCmd().withNameFilter(REDPANDA_NETWORK).exec();
			if (networks.isEmpty()) {
				Network.builder()
						.createNetworkCmdModifier(cmd -> cmd.withName(REDPANDA_NETWORK))
						.build().getId();
				return defaultDaprNetwork;
			} else {
				return defaultDaprNetwork;
			}
		}

		@Bean
		@ServiceConnection
		@RestartScope
		RedpandaContainer redpandaContainer() {
			return new RedpandaWithExtraListenersContainer("docker.redpanda.com/redpandadata/redpanda:v23.1.10")
					.withAdditionalListener(() -> "redpanda:19092")
					.withNetwork(this.network)
					.withNetworkAliases("redpanda")
					.withReuse(true);
		}

		@Bean
		@DependsOn("redpandaContainer")
		GenericContainer<?> redpandaConsole() {
			GenericContainer<?> connect = new GenericContainer("confluentinc/cp-server-connect:7.4.0") {
				@Override
				protected void containerIsStarting(InspectContainerResponse containerInfo) {
					try {
						execInContainer("confluent-hub", "install", "--no-prompt", "confluentinc/kafka-connect-s3:latest");
						execInContainer("confluent-hub", "install", "--no-prompt", "confluentinc/kafka-connect-jdbc:latest");
					} catch (IOException | InterruptedException e) {
						throw new RuntimeException("Error downloading connectors", e);
					}

				}
			}
					.withExposedPorts(8083)
					.withNetwork(network)
					.withNetworkAliases("connect")
					.waitingFor(Wait.forHttp("/connectors").forPort(8083))
					.withEnv("CONNECT_BOOTSTRAP_SERVERS", "redpanda:19092")
					.withEnv("CONNECT_LISTENERS", "http://0.0.0.0:8083")
					.withEnv("CONNECT_GROUP_ID", "connect-cluster")
					.withEnv("CONNECT_CONFIG_STORAGE_TOPIC", "connect-configs")
					.withEnv("CONNECT_OFFSET_STORAGE_TOPIC", "connect-offsets")
					.withEnv("CONNECT_STATUS_STORAGE_TOPIC", "connect-statuses")
					.withEnv("CONNECT_KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
					.withEnv("CONNECT_VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
					.withEnv("CONNECT_REST_ADVERTISED_HOST_NAME", "connect")
					.withEnv("CONNECT_REST_ADVERTISED_PORT", "8083")
					.withEnv("CONNECT_PLUGIN_PATH", "/usr/share/java,/usr/share/confluent-hub-components")
					.withEnv("CONNECT_REPLICATION_FACTOR", "1")
					.withEnv("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1")
					.withEnv("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1")
					.withEnv("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1")
					.withEnv("CONNECT_PRODUCER_CLIENT_ID", "connect-worker-producer");
			return new GenericContainer<>("docker.redpanda.com/redpandadata/console:v2.3.0")
					.withNetwork(network)
					.withExposedPorts(8080)
					.withCopyFileToContainer(MountableFile.forClasspathResource("/redpandaConsole.yml"),
							"/tmp/config.yml")
					.withEnv("CONFIG_FILEPATH", "/tmp/config.yml")
					.withStartupTimeout(Duration.ofSeconds(30))
					.waitingFor(new HostPortWaitStrategy())
					.dependsOn(connect)
					.withLabel("com.testcontainers.desktop.service", "redpanda-console");
		}

	}

}
