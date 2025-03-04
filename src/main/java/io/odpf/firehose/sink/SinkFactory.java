package io.odpf.firehose.sink;

import io.odpf.depot.bigquery.BigQuerySink;
import io.odpf.depot.bigquery.BigQuerySinkFactory;
import io.odpf.depot.config.BigQuerySinkConfig;
import io.odpf.depot.config.RedisSinkConfig;
import io.odpf.depot.bigtable.BigTableSinkFactory;
import io.odpf.depot.bigtable.BigTableSink;
import io.odpf.depot.config.BigTableSinkConfig;
import io.odpf.depot.log.LogSink;
import io.odpf.depot.log.LogSinkFactory;
import io.odpf.depot.metrics.StatsDReporter;
import io.odpf.depot.redis.RedisSink;
import io.odpf.depot.redis.RedisSinkFactory;
import io.odpf.firehose.config.KafkaConsumerConfig;
import io.odpf.firehose.config.enums.SinkType;
import io.odpf.firehose.consumer.kafka.OffsetManager;
import io.odpf.firehose.exception.ConfigurationException;
import io.odpf.firehose.metrics.FirehoseInstrumentation;
import io.odpf.firehose.sink.bigquery.BigquerySinkUtils;
import io.odpf.firehose.sink.blob.BlobSinkFactory;
import io.odpf.firehose.sink.elasticsearch.EsSinkFactory;
import io.odpf.firehose.sink.grpc.GrpcSinkFactory;
import io.odpf.firehose.sink.http.HttpSinkFactory;
import io.odpf.firehose.sink.influxdb.InfluxSinkFactory;
import io.odpf.firehose.sink.jdbc.JdbcSinkFactory;
import io.odpf.firehose.sink.mongodb.MongoSinkFactory;
import io.odpf.firehose.sink.prometheus.PromSinkFactory;
import io.odpf.stencil.client.StencilClient;
import org.aeonbits.owner.ConfigFactory;

import java.util.Map;

public class SinkFactory {
    private final KafkaConsumerConfig kafkaConsumerConfig;
    private final StatsDReporter statsDReporter;
    private final FirehoseInstrumentation firehoseInstrumentation;
    private final StencilClient stencilClient;
    private final OffsetManager offsetManager;
    private BigQuerySinkFactory bigQuerySinkFactory;
    private BigTableSinkFactory bigTableSinkFactory;
    private LogSinkFactory logSinkFactory;
    private RedisSinkFactory redisSinkFactory;
    private final Map<String, String> config;

    public SinkFactory(KafkaConsumerConfig kafkaConsumerConfig,
                       StatsDReporter statsDReporter,
                       StencilClient stencilClient,
                       OffsetManager offsetManager) {
        firehoseInstrumentation = new FirehoseInstrumentation(statsDReporter, SinkFactory.class);
        this.kafkaConsumerConfig = kafkaConsumerConfig;
        this.statsDReporter = statsDReporter;
        this.stencilClient = stencilClient;
        this.offsetManager = offsetManager;
        this.config = SinkFactoryUtils.addAdditionalConfigsForSinkConnectors(System.getenv());
    }

    /**
     * Initialization method for all the sinks.
     */
    public void init() {
        switch (this.kafkaConsumerConfig.getSinkType()) {
            case JDBC:
            case HTTP:
            case INFLUXDB:
            case ELASTICSEARCH:
            case GRPC:
            case PROMETHEUS:
            case BLOB:
            case MONGODB:
                return;
            case LOG:
                logSinkFactory = new LogSinkFactory(config, statsDReporter);
                logSinkFactory.init();
                return;
            case REDIS:
                redisSinkFactory = new RedisSinkFactory(
                        ConfigFactory.create(RedisSinkConfig.class, config),
                        statsDReporter);
                redisSinkFactory.init();
                return;
            case BIGQUERY:
                BigquerySinkUtils.addMetadataColumns(config);
                bigQuerySinkFactory = new BigQuerySinkFactory(
                        ConfigFactory.create(BigQuerySinkConfig.class, config),
                        statsDReporter,
                        BigquerySinkUtils.getRowIDCreator());
                bigQuerySinkFactory.init();
                return;
            case BIGTABLE:
                bigTableSinkFactory = new BigTableSinkFactory(
                        ConfigFactory.create(BigTableSinkConfig.class, config),
                        statsDReporter);
                bigTableSinkFactory.init();
                return;
            default:
                throw new ConfigurationException("Invalid Firehose SINK_TYPE");
        }
    }

    public Sink getSink() {
        SinkType sinkType = kafkaConsumerConfig.getSinkType();
        firehoseInstrumentation.logInfo("Sink Type: {}", sinkType);
        switch (sinkType) {
            case JDBC:
                return JdbcSinkFactory.create(config, statsDReporter, stencilClient);
            case HTTP:
                return HttpSinkFactory.create(config, statsDReporter, stencilClient);
            case INFLUXDB:
                return InfluxSinkFactory.create(config, statsDReporter, stencilClient);
            case LOG:
                return new GenericOdpfSink(new FirehoseInstrumentation(statsDReporter, LogSink.class), sinkType.name(), logSinkFactory.create());
            case ELASTICSEARCH:
                return EsSinkFactory.create(config, statsDReporter, stencilClient);
            case REDIS:
                return new GenericOdpfSink(new FirehoseInstrumentation(statsDReporter, RedisSink.class), sinkType.name(), redisSinkFactory.create());
            case GRPC:
                return GrpcSinkFactory.create(config, statsDReporter, stencilClient);
            case PROMETHEUS:
                return PromSinkFactory.create(config, statsDReporter, stencilClient);
            case BLOB:
                return BlobSinkFactory.create(config, offsetManager, statsDReporter, stencilClient);
            case BIGQUERY:
                return new GenericOdpfSink(new FirehoseInstrumentation(statsDReporter, BigQuerySink.class), sinkType.name(), bigQuerySinkFactory.create());
            case BIGTABLE:
                return new GenericOdpfSink(new FirehoseInstrumentation(statsDReporter, BigTableSink.class), sinkType.name(), bigTableSinkFactory.create());
            case MONGODB:
                return MongoSinkFactory.create(config, statsDReporter, stencilClient);
            default:
                throw new ConfigurationException("Invalid Firehose SINK_TYPE");
        }
    }
}
