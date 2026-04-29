package com.orderprocessing.analytics.integration

import com.orderprocessing.analytics.configuration.TestKafkaConfig
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.streams.KafkaStreams
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("integration")
@Testcontainers
@Import(TestKafkaConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
class AnalyticsIntegrationTests {
    companion object {
        @JvmStatic
        @Container
        val kafka = KafkaContainer("apache/kafka-native:3.8.0")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("kafka.bootstrap-servers") { kafka.bootstrapServers }
        }

        @JvmStatic
        @BeforeAll
        fun createTopics() {
            val props = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)
            AdminClient.create(props).use { admin ->
                admin
                    .createTopics(
                        listOf(
                            NewTopic("order-placed", 1, 1),
                            NewTopic("payment-processed", 1, 1),
                            NewTopic("order-failed", 1, 1),
                        ),
                    ).all()
                    .get()
            }
        }
    }

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var objectMapper: JsonMapper

    @Autowired
    lateinit var streamsBuilderFactoryBean: StreamsBuilderFactoryBean

    @LocalServerPort
    var port: Int = 0

    lateinit var restClient: RestClient

    @BeforeEach
    fun setUp() {
        restClient =
            RestClient
                .builder()
                .baseUrl("http://localhost:$port")
                .build()
        File("/tmp/kafka-streams-test").deleteRecursively()
    }

    @BeforeEach
    fun waitForStreamsToStart() {
        await().atMost(30, TimeUnit.SECONDS).until {
            streamsBuilderFactoryBean.kafkaStreams?.state() == KafkaStreams.State.RUNNING
        }
    }

    @Test
    fun `order-placed event appears in orders-per-minute store`() {
        val orderId = UUID.randomUUID()
        val payload =
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to "order-placed",
                "occurredAt" to Instant.now().toString(),
                "schemaVersion" to 1,
                "payload" to
                    mapOf(
                        "orderId" to orderId.toString(),
                        "customerId" to UUID.randomUUID().toString(),
                        "items" to
                            listOf(
                                mapOf(
                                    "productId" to UUID.randomUUID().toString(),
                                    "quantity" to 2,
                                    "pricePerItem" to "10.00",
                                ),
                            ),
                        "totalPrice" to "20.00",
                    ),
            )
        val json = objectMapper.writeValueAsString(payload)

        kafkaTemplate.send("order-placed", orderId.toString(), json).get()

        await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            val result =
                restClient
                    .get()
                    .uri("/api/v1/analytics/orders-per-minute")
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Long>>() {})
            assertThat(result).isNotEmpty()
        }
    }

    @Test
    fun `payment-processed event appears in payment-outcomes and confirmed-revenue`() {
        val orderId = UUID.randomUUID()
        val payload =
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to "payment-processed",
                "occurredAt" to Instant.now().toString(),
                "schemaVersion" to 1,
                "payload" to
                    mapOf(
                        "orderId" to orderId.toString(),
                        "transactionId" to UUID.randomUUID().toString(),
                        "customerId" to UUID.randomUUID().toString(),
                        "totalPrice" to "20.00",
                    ),
            )
        val json = objectMapper.writeValueAsString(payload)

        kafkaTemplate.send("payment-processed", orderId.toString(), json).get()

        await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            val outcomes =
                restClient
                    .get()
                    .uri("/api/v1/analytics/payment-outcomes")
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Long>>() {})
            assertThat(outcomes).containsKey("SUCCESS")

            val revenue =
                restClient
                    .get()
                    .uri("/api/v1/analytics/confirmed-revenue")
                    .retrieve()
                    .body(String::class.java)
            assertThat(revenue).isEqualTo("20.00")
        }
    }

    @Test
    fun `order-failed event appears in payment-outcomes as FAILED`() {
        val orderId = UUID.randomUUID()
        val payload =
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to "order-failed",
                "occurredAt" to Instant.now().toString(),
                "schemaVersion" to 1,
                "payload" to
                    mapOf(
                        "orderId" to orderId.toString(),
                        "customerId" to UUID.randomUUID().toString(),
                        "reason" to "Payment failed",
                    ),
            )
        val json = objectMapper.writeValueAsString(payload)

        kafkaTemplate.send("order-failed", orderId.toString(), json).get()

        await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            val outcomes =
                restClient
                    .get()
                    .uri("/api/v1/analytics/payment-outcomes")
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Map<String, Long>>() {})
            assertThat(outcomes).containsKey("FAILED")
        }
    }
}
