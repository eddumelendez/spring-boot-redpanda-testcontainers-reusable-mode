package com.example.consumer;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.List;

public class ConsumerApplicationTests {

    public static void main(String[] args) {
        SpringApplication.from(ConsumerApplication::main)
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
            return new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v23.1.10")
                    .withListener(() -> "redpanda:19092")
                    .withNetwork(this.network)
                    .withReuse(true);
        }

    }

}
