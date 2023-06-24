package com.thecodinginterface.kinesis;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.coordinator.Scheduler;

import java.util.UUID;

public class KclConsumerApp {
    static final Logger logger = LogManager.getLogger(KclConsumerApp.class);

    public static void main(String[] args) {

        var schedulerId = UUID.randomUUID().toString();
        String appName = args[0]; // group of the same type of application
        String streamName = args[1];
        Region region = Region.of(args[2]);

        logger.info(String.format("Starting KCL Scheduler %s app %s for stream %s in region %s", schedulerId, appName, streamName, region.id()));

        var kinesisClient = KinesisClientUtil.createKinesisAsyncClient(KinesisAsyncClient.builder().region(region));
        var dynamoClient = DynamoDbAsyncClient.builder().region(region).build();
        var cwClient = CloudWatchAsyncClient.builder().region(region).build();
        var orderProcessorFactory = new OrderRecordProcessorFactory();

        var configsBuilder = new ConfigsBuilder(
                streamName,
                appName,
                kinesisClient,
                dynamoClient,
                cwClient,
                schedulerId,
                orderProcessorFactory
        );

        var scheduler = new Scheduler(
                configsBuilder.checkpointConfig(),
                configsBuilder.coordinatorConfig(),
                configsBuilder.leaseManagementConfig(),
                configsBuilder.lifecycleConfig(),
                configsBuilder.metricsConfig(),
                configsBuilder.processorConfig(),
                configsBuilder.retrievalConfig()
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down consumer application");
            scheduler.shutdown();
        }, "consumer-shutdown"));

        int exitCode = 0;
        try {
            scheduler.run();
        } catch (Exception e) {
            logger.error(String.format("KCL Scheduler %s app %s encountered error.", schedulerId, appName), e);
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
